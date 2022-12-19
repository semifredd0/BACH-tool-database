package com.bitcoin.clusterbtc;

import com.bitcoin.clusterbtc.dto.AddressDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Service {
    private static Connection con;
    private static PreparedStatement pst;

    public Service() {}

    public void openConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        con = DriverManager.getConnection("jdbc:postgresql://localhost:5432/cluster_btc", "matteo", "password");
    }

    public void closeConnection() throws SQLException {
        con.close();
    }

    public void addAddress(AddressDTO address) {
        // ADDRESS_HASH has a unique constraint
        try {
            pst = con.prepareStatement("insert into address (ADDRESS_HASH,MINER_ADDRESS,ADDRESS_TYPE,CLUSTER_ID) values (?,?,?,?)");
            pst.setString(1,address.getAddress_hash());
            pst.setBoolean(2,address.isMiner_address());
            short type = getType(address.getAddress_hash());
            if(type != -1) pst.setShort(3,type);
            else pst.setNull(3,Types.SMALLINT);
            if(address.getCluster_id() != null) pst.setLong(4,address.getCluster_id());
            else pst.setNull(4,Types.BIGINT);
            pst.executeUpdate();
        } catch (SQLException e) {
            // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Address already exists!");
        }
    }

    public AddressDTO getAddress(String hash) {
        try {
            pst = con.prepareStatement("select * from ADDRESS where ADDRESS_HASH = ?");
            pst.setString(1,hash);
            ResultSet rs = pst.executeQuery();
            rs.next();
            AddressDTO address = new AddressDTO();
            address.setAddressId(rs.getLong("ADDRESS_ID"));
            address.setAddress_hash(rs.getString("ADDRESS_HASH"));
            address.setMiner_address(rs.getBoolean("MINER_ADDRESS"));
            address.setAddressType(rs.getShort("ADDRESS_TYPE"));
            address.setCluster_id(rs.getLong("CLUSTER_ID"));
            return address;
        } catch (SQLException ex) {
            // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot find address!");
            return null;
        }
    }

    public List<String> getAddressByCluster(Long clusterID) {
        try {
            pst = con.prepareStatement("select ADDRESS_HASH from ADDRESS where CLUSTER_ID = ?");
            pst.setLong(1,clusterID);
            ResultSet rs = pst.executeQuery();
            List<String> hash_list = new ArrayList<>();
            while(rs.next())
                hash_list.add(rs.getString("ADDRESS_HASH"));
            return hash_list;
        } catch (SQLException ex) {
            // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot find address!");
            return null;
        }
    }

    public void updateAddressCluster(Long clusterID, String addressHash) {
        try {
            pst = con.prepareStatement("update ADDRESS set CLUSTER_ID = ? where ADDRESS_HASH = ?");
            pst.setLong(1, clusterID);
            pst.setString(2, addressHash);
            pst.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot update cluster column in address!");
        }
    }

    /** Gli address del vecchio cluster faranno parte del nuovo cluster.
     * Il vecchio cluster sarà vuoto.
     * @param newClusterID New cluster to add.
     * @param oldClusterID Old cluster to substitute.
     */
    public void updateAddressList(Long newClusterID, Long oldClusterID) {
        try {
            pst = con.prepareStatement("update ADDRESS set CLUSTER_ID = ? where CLUSTER_ID = ?");
            pst.setLong(1, newClusterID);
            pst.setLong(2, oldClusterID);
            pst.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot update cluster column in address!");
        }
    }

    public void addSubCluster(List<String> addressList, short type, long subClusterId) {
        /* List<Long> addressIds = new ArrayList<>();
        for(String hash : addressList)
            addressIds.add(getAddress(hash).getAddressId());
        // Check for duplicates sub-clusters
        if(!addSubClusterCheck(addressIds)) return; */
        for(String hash : addressList) {
            Long addressId = getAddress(hash).getAddressId();
            try {
                pst = con.prepareStatement("insert into SUB_CLUSTER (SUB_CLUSTER_ID,ADDRESS_ID,NODE_TYPE) values (?,?,?)");
                pst.setLong(1, subClusterId);
                pst.setLong(2, addressId);
                pst.setShort(3, type);
                pst.executeUpdate();
            } catch (SQLException e) {
                // Chiave duplicata
                // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Address + SubCluster already exists!");
            }
        }
    }

    public void addAddressSubCluster(Long subClusterId, String change_hash, short type) {
        Long addressId = getAddress(change_hash).getAddressId();
        try {
            pst = con.prepareStatement("insert into SUB_CLUSTER (SUB_CLUSTER_ID,ADDRESS_ID,NODE_TYPE) values (?,?,?)");
            pst.setLong(1, subClusterId);
            pst.setLong(2, addressId);
            pst.setShort(3, type);
            pst.executeUpdate();
        } catch (SQLException e) {
            // Chiave duplicata
            // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Address + SubCluster already exists!");
        }
    }

    /** Check if the sub-cluster to add already exists,
     * or if the sub-cluster to add is a partition of an existing cluster.
     * @param subClusterToAdd List of addresses IDs in the sub-cluster.
     */
    private boolean addSubClusterCheck(List<Long> subClusterToAdd) {
        for(Long addressId : subClusterToAdd) {
            List<Long> subClusters = getSubClusterIdFromAddressId(addressId);
            if(subClusters == null) continue; // Address does not have a cluster
            for(Long subClusterId : subClusters) {
                boolean inner_cluster = true;
                List<Long> subClusterAddresses = getSubClusterAddresses(subClusterId);
                assert subClusterAddresses != null; // Should be always true
                for(Long item : subClusterToAdd) {
                    if(!subClusterAddresses.contains(item)) {
                        inner_cluster = false;
                        break;
                    }
                }
                if(inner_cluster) return false; // Inner cluster found
            }
        }
        return true;
    }

    private List<Long> getSubClusterAddresses(Long subClusterId) {
        try {
            pst = con.prepareStatement("select ADDRESS_ID from SUB_CLUSTER where SUB_CLUSTER_ID = ?");
            pst.setLong(1,subClusterId);
            ResultSet rs = pst.executeQuery();
            List<Long> lista = new ArrayList<>();
            while(rs.next())
                lista.add(rs.getLong("ADDRESS_ID"));
            return lista;
        } catch (SQLException ex) {
            return null;
        }
    }

    private List<Long> getSubClusterIdFromAddressId(Long addressId) {
        try {
            pst = con.prepareStatement("select SUB_CLUSTER_ID from SUB_CLUSTER where ADDRESS_ID = ?");
            pst.setLong(1,addressId);
            ResultSet rs = pst.executeQuery();
            List<Long> lista = new ArrayList<>();
            while(rs.next())
                lista.add(rs.getLong("SUB_CLUSTER_ID"));
            return lista;
        } catch (SQLException ex) {
            // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot find address ID!");
            return null; // Address do not have a cluster
        }
    }

    /** Scala affidabilità delle euristiche: 0,1,2 (ordine crescente).
     * Quindi se ci sono dei duplicati di address nello stesso subCluster,
     * impostiamo come node_type il numero corrispondente alla euristica
     * con affidabilità maggiore, ossia il type minore.
     * Metodo non utilizzato attualmente. */
    private void updateSubClusterType(Long addressId, Long subClusterId, short type) {
        try {
            pst = con.prepareStatement("update SUB_CLUSTER set NODE_TYPE = ? where ADDRESS_ID = ?, SUB_CLUSTER_ID = ?");
            pst.setShort(1, type);
            pst.setLong(2, addressId);
            pst.setLong(3, subClusterId);
            pst.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot update node type in sub cluster!");
        }
    }

    private short getType(String hash) {
        // Legacy Address (P2PKH)
        if(hash.startsWith("1")) return 0;
        // Pay to Script Hash (P2SH)
        if(hash.startsWith("3")) return 1;
        // Native SegWit (P2WPKH)
        if(hash.startsWith("bc1q")) return 2;
        // Taproot (P2TR)
        if(hash.startsWith("bc1p")) return 3;
        return -1;
    }
}
