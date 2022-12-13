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
    private static Long address_count = 0L;

    public Service() {}

    public void openConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        con = DriverManager.getConnection("jdbc:postgresql://localhost:5432/cluster_btc", "matteo", "password");
    }

    public void closeConnection() throws SQLException {
        con.close();
    }

    public AddressDTO getAddress(AddressDTO address) {
        try {
            pst = con.prepareStatement("select * from ADDRESS where ADDRESS_HASH = ?");
            pst.setString(1,address.getAddress_hash());
            ResultSet rs = pst.executeQuery();
            rs.next();
            address.setAddress_id(rs.getLong("ADDRESS_ID"));
            address.setAddress_hash(rs.getString("ADDRESS_HASH"));
            address.setMiner_address(rs.getBoolean("MINER_ADDRESS"));
            address.setMiningPoolAddress(rs.getBoolean("MINING_POOL_ADDRESS"));
            address.setCluster_id(rs.getLong("CLUSTER"));
            return address;
        } catch (SQLException ex) {
            // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot find address!");
            return null;
        }
    }

    public List<String> getAddressByCluster(Long clusterID) {
        try {
            pst = con.prepareStatement("select ADDRESS_HASH from ADDRESS where CLUSTER = ?");
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

    /**
     * Gli address del vecchio cluster faranno parte del nuovo cluster.
     * Il vecchio cluster sarà vuoto.
     * @param newClusterID
     * @param oldClusterID
     */
    public void updateAddressList(Long newClusterID, Long oldClusterID) {
        try {
            pst = con.prepareStatement("update ADDRESS set CLUSTER = ? where CLUSTER = ?");
            pst.setLong(1, newClusterID);
            pst.setLong(2, oldClusterID);
            pst.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot update cluster columns in address!");
        }
    }

    public void updateAddressCluster(Long clusterID, String addressHash) {
        try {
            pst = con.prepareStatement("update ADDRESS set CLUSTER = ? where ADDRESS_HASH = ?");
            pst.setLong(1, clusterID);
            pst.setString(2, addressHash);
            pst.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot update cluster column in address!");
        }
    }

    public void updateClusterMiningPool(Long clusterID, boolean mining_pool) {
        try {
            pst = con.prepareStatement("update ADDRESS set MINING_POOL_ADDRESS = ? where CLUSTER = ?");
            pst.setBoolean(1, mining_pool);
            pst.setLong(2, clusterID);
            pst.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Cannot update mining_pool columns in address!");
        }
    }

    public void addAddress(AddressDTO address) {
        // ADDRESS_HASH has a unique constraint
        try {
            pst = con.prepareStatement("insert into address (ADDRESS_ID,ADDRESS_HASH,MINER_ADDRESS,MINING_POOL_ADDRESS,CLUSTER) values (?,?,?,?,?)");
            pst.setLong(1,address_count);
            pst.setString(2,address.getAddress_hash());
            pst.setBoolean(3,address.isMiner_address());
            pst.setBoolean(4,address.isMiningPoolAddress());
            if(address.getCluster_id() != null)
                pst.setLong(5,address.getCluster_id());
            else
                pst.setNull(5,Types.BIGINT);
            pst.executeUpdate();
            address_count++;
        } catch (SQLException e) {
            // Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.INFO, "Address already exists!");
        }
    }
}
