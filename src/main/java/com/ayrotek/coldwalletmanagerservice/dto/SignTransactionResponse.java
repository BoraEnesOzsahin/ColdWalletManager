package com.ayrotek.coldwalletmanagerservice.dto;

public class SignTransactionResponse {
    private String transactionHash;

    public SignTransactionResponse(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }
}
