package com.bitcoin.clusterbtc;

import com.bitcoin.clusterbtc.dto.AddressDTO;
import com.bitcoin.clusterbtc.dto.ClusterDTO;
import com.bitcoin.clusterbtc.model.Block;
import com.bitcoin.clusterbtc.model.Out;
import com.bitcoin.clusterbtc.model.Tx;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Controller {

    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final Service service = new Service();
    private static Long cluster_id_counter = 0L; // ClusterID counter

    public Controller() {}

    public void addTransaction() throws IOException {
        ArrayList<String> blockHashList = getHashesFromFile();

        // Open connection to DB
        try {
            service.openConnection();
        } catch (SQLException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Cannot open connection to DB!");
        }

        for (int i=600000; i<600010; i++) {
            // Timeout DB connection for backup every 100 blocks
            if (i % 100 == 0) {
                try {
                    service.closeConnection();
                    service.openConnection();
                } catch (SQLException | ClassNotFoundException e) {
                    LOGGER.log(Level.SEVERE, "Cannot restart connection to DB!");
                }
            }

            // Initialize block object
            Block block = getBlock(blockHashList.get(i));
            List<Tx> list_tx = block.getTx();
            // Logging current block
            LOGGER.log(Level.INFO, "Block hash: " + block.getHash() + " - Transaction number: " + block.getTx().size());

            // Coinbase transaction added manually to DB
            // Coinbase transaction can have multiple output
            Tx coinbase = list_tx.get(0);

            // List of effective addresses
            List<String> minerAddressList = new ArrayList<>();
            for(int k=0; k<coinbase.getVout_sz(); k++) {
                if (coinbase.getOut().get(k).getAddr() != null)
                    minerAddressList.add(coinbase.getOut().get(k).getAddr());
            }

            ClusterDTO clusterDTO = new ClusterDTO();
            for(int k=0; k<minerAddressList.size(); k++) {
                // Create addressDTO
                AddressDTO minerAddress = new AddressDTO();
                minerAddress.setMiner_address(true);
                minerAddress.setMiningPoolAddress(false);
                minerAddress.setAddress_hash(minerAddressList.get(k));

                // Only one miner -> No cluster
                if(minerAddressList.size() == 1)
                    service.addAddress(minerAddress);
                // Coinbase cluster
                else if(k == 0) { // First iteration -> Choose the cluster id
                    if(service.getAddress(minerAddress) == null) {
                        // New address -> Create new cluster and assign miner address to it
                        clusterDTO.setCluster_id(cluster_id_counter);
                        minerAddress.setCluster_id(cluster_id_counter);
                        // Update ID counter
                        cluster_id_counter++;
                        // Save cluster and address
                        service.addCluster(clusterDTO);
                        service.addAddress(minerAddress);
                    } else {
                        // Update the actual object with the existing one by address hash
                        minerAddress = service.getAddress(minerAddress);
                        if(minerAddress.getCluster_id() != null)
                            // Address already exists with a cluster -> Update coinbase cluster ID
                            clusterDTO.setCluster_id(minerAddress.getCluster_id());
                        else {
                            // Address exists without a cluster -> Create new cluster and assign miner address to it
                            clusterDTO.setCluster_id(cluster_id_counter);
                            minerAddress.setCluster_id(cluster_id_counter);
                            // Update ID counter
                            cluster_id_counter++;
                            // Save cluster and update address
                            service.addCluster(clusterDTO);
                            service.updateAddressCluster(clusterDTO.getCluster_id(), minerAddress.getAddress_hash());
                        }
                    }
                } else { // Next iterations -> Assign the same cluster ID
                    if(service.getAddress(minerAddress) == null) {
                        // New address -> Assign miner address to the coinbase cluster
                        minerAddress.setCluster_id(clusterDTO.getCluster_id());
                        // Save address
                        service.addAddress(minerAddress);
                    } else {
                        // Update the actual object with the existing one by address hash
                        minerAddress = service.getAddress(minerAddress);
                        if(minerAddress.getCluster_id() != null) {
                            // Assign all addresses of the miner cluster to the new coinbase cluster
                            service.updateAddressList(clusterDTO.getCluster_id(), minerAddress.getCluster_id());
                        }
                        // Miner address exists without a cluster -> Assign the coinbase cluster
                        else service.updateAddressCluster(clusterDTO.getCluster_id(), minerAddress.getAddress_hash());
                    }
                }
            }

            // Add each transaction of the block to DB
            for(int j=1; j<list_tx.size(); j++) {
                // Logging current transaction
                // LOGGER.log(Level.INFO, "Transaction number: " + j);
                createAddressTransactionDTO(list_tx.get(j));
            }
        }

        // Close connection to DB
        try {
            service.closeConnection();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Cannot close connection to DB!");
        }
    }

    private Block getBlock(String hash) throws IOException {
        URL url = new URL("https://blockchain.info/rawblock/" + hash);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        // Write all the JSON data into a string using a scanner
        StringBuilder inline = new StringBuilder();
        Scanner scanner = new Scanner(url.openStream());
        while (scanner.hasNext())
            inline.append(scanner.nextLine());
        scanner.close();

        return new ObjectMapper().readerFor(Block.class).readValue(String.valueOf(inline));
    }

    private ArrayList<String> getHashesFromFile() throws IOException {
        ArrayList<String> list = new ArrayList<>();

        File f = new File("src/main/resources/hash.txt");
        BufferedReader b = new BufferedReader(new FileReader(f));
        String readLine;

        System.out.println("Reading file using Buffered Reader...");
        while((readLine = b.readLine()) != null) {
            readLine = readLine.split(",")[1];
            list.add(readLine);
        }
        return list;
    }

    private void createAddressTransactionDTO(Tx transaction) {
        ClusterDTO multi_input_cluster = new ClusterDTO();
        // Save last input address to check if the next is the same
        String last_address = null;

        // List of effective addresses
        List<String> inputAddressList = new ArrayList<>();
        for(int k=0; k<transaction.getVin_sz(); k++) {
            if (transaction.getInputs().get(k).getPrev_out().getAddr() != null)
                inputAddressList.add(transaction.getInputs().get(k).getPrev_out().getAddr());
        }

        /** Input txDTO */
        for(int k=0; k<inputAddressList.size(); k++) {
            // Skip address if it's the same as the last one
            if(inputAddressList.get(k).equals(last_address)) continue;

            // Create addressDTO
            AddressDTO tempAddressDTO = new AddressDTO();
            tempAddressDTO.setMiner_address(false);
            tempAddressDTO.setMiningPoolAddress(false);
            tempAddressDTO.setAddress_hash(inputAddressList.get(k));

            // Only one input -> No cluster
            if(inputAddressList.size() == 1)
                service.addAddress(tempAddressDTO);
            // Multi-input cluster
            else if(k == 0) { // First iteration -> Choose the cluster id
                if(service.getAddress(tempAddressDTO) == null) {
                    // New address -> Create new cluster and assign address to it
                    multi_input_cluster.setCluster_id(cluster_id_counter);
                    tempAddressDTO.setCluster_id(cluster_id_counter);
                    // Update ID counter
                    cluster_id_counter++;
                    // Save cluster and address
                    service.addCluster(multi_input_cluster);
                    service.addAddress(tempAddressDTO);
                } else {
                    // Update the actual object with the existing one by address hash
                    tempAddressDTO = service.getAddress(tempAddressDTO);
                    if(tempAddressDTO.getCluster_id() != null) {
                        // Address already exists with a cluster -> Update cluster ID
                        multi_input_cluster.setCluster_id(tempAddressDTO.getCluster_id());
                    } else {
                        // Address exists without a cluster -> Create new cluster and assign address to it
                        multi_input_cluster.setCluster_id(cluster_id_counter);
                        tempAddressDTO.setCluster_id(cluster_id_counter);
                        // Update ID counter
                        cluster_id_counter++;
                        // Save cluster and update address
                        service.addCluster(multi_input_cluster);
                        service.updateAddressCluster(multi_input_cluster.getCluster_id(), tempAddressDTO.getAddress_hash());
                    }
                }
            } else { // Next iterations -> Assign the same cluster ID
                if(service.getAddress(tempAddressDTO) == null) {
                    // New address -> Assign address to the coinbase cluster
                    tempAddressDTO.setCluster_id(multi_input_cluster.getCluster_id());
                    // Save address
                    service.addAddress(tempAddressDTO);
                } else {
                    // Update the actual object with the existing one by address hash
                    tempAddressDTO = service.getAddress(tempAddressDTO);
                    if(tempAddressDTO.getCluster_id() != null) {
                        // Assign all addresses of the multi-input cluster to the existing cluster
                        service.updateAddressList(tempAddressDTO.getCluster_id(), multi_input_cluster.getCluster_id());
                        // Update the multi-input cluster ID
                        multi_input_cluster.setCluster_id(tempAddressDTO.getCluster_id());
                    }
                    // Input address exists without a cluster -> Assign the multi-input cluster
                    else service.updateAddressCluster(multi_input_cluster.getCluster_id(), tempAddressDTO.getAddress_hash());
                }
            }
            // Save last address to check in the next iteration and skip if they are equals
            last_address = inputAddressList.get(k);
        }

        // List of effective addresses
        List<String> outputAddressList = new ArrayList<>();
        for(int k=0; k<transaction.getVout_sz(); k++) {
            if (transaction.getOut().get(k).getAddr() != null)
                outputAddressList.add(transaction.getOut().get(k).getAddr());
        }

        // Flag for mining pool transaction
        boolean mining_pool_tx = false;
        // Cluster for mining pool tx
        ClusterDTO cluster_mining_pool = new ClusterDTO();
        // Mining pool cluster
        if(transaction.getVout_sz() >= 100) {
            // Check if the output contains a miner address
            for (String s : outputAddressList) {
                AddressDTO tempAddress = new AddressDTO();
                tempAddress.setAddress_hash(s);
                tempAddress = service.getAddress(tempAddress);
                if ((tempAddress != null) &&
                        (tempAddress.isMiner_address() || tempAddress.isMiningPoolAddress())) {
                    // Logging tx if it is a miner pool tx
                    LOGGER.log(Level.INFO, "Mining pool tx: " + transaction.getHash());
                    // Create a mining pool cluster
                    cluster_mining_pool.setCluster_id(cluster_id_counter);
                    // Update cluster ID
                    cluster_id_counter++;
                    // Upload cluster on DB
                    service.addCluster(cluster_mining_pool);
                    // Activate flag
                    mining_pool_tx = true;
                    break;
                }
            }
        }

        // Hash of the change address
        String change_hash = null;
        // Identify change address
        if(!mining_pool_tx && transaction.getVout_sz() >= 2) {
            // ----------- First method
            // Get all addresses in the multi-input cluster
            List<String> hash_list;
            if (multi_input_cluster.getCluster_id() != null)
                hash_list = service.getAddressByCluster(multi_input_cluster.getCluster_id());
            else {
                // Case: multi-input cluster is null because there is only one input or zero!
                hash_list = new ArrayList<>();
                if(inputAddressList.size() == 1) // Only one input
                    hash_list.add(inputAddressList.get(0));
            }
            for (String output : outputAddressList) {
                // Verify if one of the output belong to the same cluster
                if (hash_list.contains(output)) {
                    change_hash = output;
                    break;
                }
            }
            /// ----------- Second method, used if first one doesn't work
            if(change_hash == null) {
                List<String> changes = new ArrayList<>();
                // Save all the new addresses in the list
                for (String output : outputAddressList) {
                    AddressDTO tempAddress = new AddressDTO();
                    tempAddress.setAddress_hash(output);
                    if (service.getAddress(tempAddress) == null)
                        changes.add(tempAddress.getAddress_hash());
                }
                // Verify there's only one new address
                if (changes.size() == 1)
                    change_hash = changes.get(0);
                else if (changes.size() > 1) { // More new addresses in the output
                    // Verify the minimum value in the output addresses
                    // Sort list of outputs by value in ascending
                    List<Out> outList = transaction.getOut();
                    outList.sort(Comparator.comparing(Out::getValue));
                    /* This could be a multi-sig script, so cannot retrive address hash.
                     * In this case we iterate on all the outputs because we want to check
                     *      the value of the output not just if the address hash exists.
                     *      If the output found doesn't have an address, change is null.
                     * Store the minimum value but also the second one, to check that the minimum
                     *      output is the only one minor of all the input.
                     */
                    String temp_change_out1 = outList.get(0).getAddr();
                    Long temp_value_out1 = outList.get(0).getValue();
                    Long temp_value_out2 = outList.get(1).getValue();

                    // Continue only if the change address found have an address hash
                    if(temp_change_out1 != null) {
                        boolean minimum_input_flag = true;
                        boolean only_minimum_flag = true;
                        for (int k=0; k<transaction.getVin_sz(); k++) {
                            // Verify the change address has the minimum value between inputs
                            if (transaction.getInputs().get(k).getPrev_out().getValue() < temp_value_out1) {
                                minimum_input_flag = false;
                                break;
                            }
                            // Verify the change address is the only one having the minimum value between inputs
                            if(transaction.getInputs().get(k).getPrev_out().getValue() > temp_value_out2) {
                                only_minimum_flag = false;
                                break;
                            }
                        }
                        // Change address match the two condition on the values
                        if (minimum_input_flag && only_minimum_flag && changes.contains(temp_change_out1)) {
                            change_hash = temp_change_out1;
                            // LOGGER.log(Level.INFO, "Transaction hash: " + transaction.getHash());
                            // System.out.println("[Change]: " + change_hash);
                        }
                    }
                }
            }
        }

        /** Output txDTO */
        for (String output : outputAddressList) {
            // Create addressDTO
            AddressDTO tempAddressDTO = new AddressDTO();
            tempAddressDTO.setMiner_address(false);
            tempAddressDTO.setMiningPoolAddress(false);
            tempAddressDTO.setAddress_hash(output);

            // Check if it is a mining pool tx
            if (mining_pool_tx) {
                if (service.getAddress(tempAddressDTO) == null) { // Address not exists
                    tempAddressDTO.setCluster_id(cluster_mining_pool.getCluster_id());
                    tempAddressDTO.setMiningPoolAddress(true);
                    service.addAddress(tempAddressDTO);
                } else {
                    // Update the actual object with the existing one (to get the cluster ID)
                    tempAddressDTO = service.getAddress(tempAddressDTO);
                    tempAddressDTO.setMiningPoolAddress(true);
                    // Unify clusters: for each address in the old cluster -> Set the new cluster ID
                    service.updateAddressList(cluster_mining_pool.getCluster_id(), tempAddressDTO.getCluster_id());
                    // Set all address of the old cluster as mining pool addresses
                    service.updateClusterMiningPool(cluster_mining_pool, true);
                }
            } else service.addAddress(tempAddressDTO); // Add address without cluster
        }

        // Add change address and its cluster to the multi-input cluster
        if(change_hash != null) {
            // Get cluster of the change address
            AddressDTO change_address = new AddressDTO();
            change_address.setAddress_hash(change_hash);
            Long change_address_cluster = service.getAddress(change_address).getCluster_id();
            if(change_address_cluster != null && multi_input_cluster.getCluster_id() != null)
                // Unify clusters: for each address in the change address cluster -> Set the multi-input cluster ID
                service.updateAddressList(multi_input_cluster.getCluster_id(),change_address_cluster);
            else if (multi_input_cluster.getCluster_id() != null)
                // Change address cluster is null -> There is a multi-input cluster
                // Add just the change address to the multi-input cluster
                service.updateAddressCluster(multi_input_cluster.getCluster_id(),change_hash);
            else {
                // The two clusters are null -> One input and a change address -> Create cluster
                // Verify input exists
                if (inputAddressList.size() == 1) {
                    ClusterDTO miniCluster = new ClusterDTO();
                    miniCluster.setCluster_id(cluster_id_counter);
                    // Update cluster ID
                    cluster_id_counter++;
                    // Upload cluster on DB
                    service.addCluster(miniCluster);
                    service.updateAddressCluster(miniCluster.getCluster_id(), change_hash);
                    service.updateAddressCluster(miniCluster.getCluster_id(), inputAddressList.get(0));
                }
            }
        }

        if(mining_pool_tx) {
            if (multi_input_cluster.getCluster_id() != null)
                // Unify multi-input cluster and mining-pool cluster
                service.updateAddressList(cluster_mining_pool.getCluster_id(), multi_input_cluster.getCluster_id());
            else if(inputAddressList.size() == 1)
                // Add the only input address to the mining-pool cluster
                service.updateAddressCluster(cluster_mining_pool.getCluster_id(), inputAddressList.get(0));
            // Set all the new addresses in the cluster as mining pool addresses
            service.updateClusterMiningPool(cluster_mining_pool, true);
        }
    }
}