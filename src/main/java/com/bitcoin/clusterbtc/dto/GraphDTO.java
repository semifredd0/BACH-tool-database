package com.bitcoin.clusterbtc.dto;

public class GraphDTO {

    private Long address_id1;
    private Long address_id2;
    /**
     * 0 -> Coinbase clustering
     * 1 -> Multi-input clustering
     * 2 -> Change address clustering
     */
    private int type;

    public GraphDTO() {
    }

    public Long getAddress_id1() {
        return address_id1;
    }

    public void setAddress_id1(Long address_id1) {
        this.address_id1 = address_id1;
    }

    public Long getAddress_id2() {
        return address_id2;
    }

    public void setAddress_id2(Long address_id2) {
        this.address_id2 = address_id2;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
