package com.bitcoin.clusterbtc;

import java.io.IOException;

public class Batch {

    public static void main(String[] args) {
        Controller controller = new Controller();
        try {
            controller.addTransaction();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
