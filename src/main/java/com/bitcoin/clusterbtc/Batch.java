package com.bitcoin.clusterbtc;

import com.bitcoin.clusterbtc.model.Block;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Scanner;

public class Batch {

    public static void main(String[] args) {
        try {
            Controller controller = new Controller();
            Service service = new Service();
            // Open connection to DB
            service.openConnection();
            // Get block hash -> Download block -> Parse block
            ArrayList<String> blockHashList = getHashesFromFile();
            for (int i=500008; i<500010; i++) {
                Block block = downloadBlock(blockHashList.get(i));
                controller.parseBlocks(block,i);
            }
            // Close connection to DB
            service.closeConnection();
        } catch (IOException | SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static Block downloadBlock(String hash) throws IOException {
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
        String block_str = String.valueOf(inline);
        return new ObjectMapper().readerFor(Block.class).readValue(block_str);
    }

    private static ArrayList<String> getHashesFromFile() throws IOException {
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
}
