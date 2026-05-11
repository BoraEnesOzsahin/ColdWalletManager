package com.ayrotek.coldwalletmanagerservice.dto;

import java.math.BigDecimal;

public class BalanceResponse {
    private String address;
    private BigDecimal balanceInEther;

    public BalanceResponse(String address, BigDecimal balanceInEther) {
        this.address = address;
        this.balanceInEther = balanceInEther;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getBalanceInEther() {
        return balanceInEther;
    }

    public void setBalanceInEther(BigDecimal balanceInEther) {
        this.balanceInEther = balanceInEther;
    }
}
