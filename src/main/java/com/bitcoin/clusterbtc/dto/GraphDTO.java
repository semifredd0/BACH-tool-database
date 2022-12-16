package com.bitcoin.clusterbtc.dto;

public class GraphDTO {

    private Long address_id;
    private int subClusterId;
    /** 0 -> Coinbase clustering
     *  1 -> Multi-input clustering
     *  2 -> Change address clustering */
    private short type;

    public GraphDTO() {
    }

    public Long getAddress_id() {
        return address_id;
    }

    public void setAddress_id(Long address_id) {
        this.address_id = address_id;
    }

    public int getSubClusterId() {
        return subClusterId;
    }

    public void setSubClusterId(int subClusterId) {
        this.subClusterId = subClusterId;
    }

    public short getType() {
        return type;
    }

    public void setType(short type) {
        this.type = type;
    }
}
