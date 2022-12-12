package com.bitcoin.clusterbtc.dto;

public class AddressDTO {

    private Long address_id;
    private String addressHash;
    private boolean minerAddress;
    private boolean miningPoolAddress;
    private Long clusterId;

    public AddressDTO() {}

    public Long getAddress_id() {
        return address_id;
    }

    public void setAddress_id(Long address_id) {
        this.address_id = address_id;
    }

    public String getAddress_hash() {
        return addressHash;
    }

    public void setAddress_hash(String address_hash) {
        this.addressHash = address_hash;
    }

    public Long getCluster_id() {
        return clusterId;
    }

    public void setCluster_id(Long cluster_id) {
        this.clusterId = cluster_id;
    }

    public boolean isMiner_address() {
        return minerAddress;
    }

    public void setMiner_address(boolean miner_address) {
        this.minerAddress = miner_address;
    }

    public boolean isMiningPoolAddress() {
        return miningPoolAddress;
    }

    public void setMiningPoolAddress(boolean miningPoolAddress) {
        this.miningPoolAddress = miningPoolAddress;
    }
}
