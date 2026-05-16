package com.ayrotek.coldwalletmanagerservice.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Service
public class CheckBalanceService {

    private final RestClient restClient;
    
    // Tatum Ethereum Classic Testnet (Mordor) RPC URL requires an API key to work correctly,
    // so we will switch to a public RPC node for Mordor
    private static final String ETC_RPC_URL = "https://rpc.mordor.etccooperative.org";

    public CheckBalanceService() {
        this.restClient = RestClient.create(ETC_RPC_URL);
    }

    public BigDecimal getBalance(String address) {
        // Ethereum JSON-RPC expects this specific payload format
        RpcRequest requestPayload = new RpcRequest("2.0", "eth_getBalance", List.of(address, "latest"), 1);

        // Send the POST request to the node
        RpcResponse response = restClient.post()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(requestPayload)
                .retrieve()
                .body(RpcResponse.class);

        if (response == null || response.result() == null) {
            throw new RuntimeException("Failed to fetch balance from Ethereum Classic node.");
        }

        // The balance comes back as a hex string representing Wei
        String hexBalance = response.result();
        BigInteger balanceInWei = Numeric.decodeQuantity(hexBalance);

        // Convert Wei to Ether (ETC uses the same 10^18 Wei denomination as ETH)
        return Convert.fromWei(balanceInWei.toString(), Convert.Unit.ETHER);
    }

    public BigInteger getTransactionCount(String address) {
        RpcRequest requestPayload = new RpcRequest("2.0", "eth_getTransactionCount", List.of(address, "latest"), 1);

        RpcResponse response = restClient.post()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(requestPayload)
                .retrieve()
                .body(RpcResponse.class);

        if (response == null || response.result() == null) {
            throw new RuntimeException("Failed to fetch transaction count (nonce) from node.");
        }

        return Numeric.decodeQuantity(response.result());
    }

    public BigInteger getGasPrice() {
        RpcRequest requestPayload = new RpcRequest("2.0", "eth_gasPrice", List.of(), 1);

        RpcResponse response = restClient.post()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(requestPayload)
                .retrieve()
                .body(RpcResponse.class);

        if (response == null || response.result() == null) {
            throw new RuntimeException("Failed to fetch gas price from node.");
        }

        return Numeric.decodeQuantity(response.result());
    }

    public String sendRawTransaction(String signedTxHex) {
        // Broadcast a signed raw transaction to the network
        RpcRequest requestPayload = new RpcRequest("2.0", "eth_sendRawTransaction", List.of(signedTxHex), 1);
        
        RpcResponse response = restClient.post()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(requestPayload)
                .retrieve()
                .body(RpcResponse.class);

        if (response == null || response.result() == null) {
            String errorMsg = (response != null && response.error() != null) ? response.error().toString() : "Unknown error";
            throw new RuntimeException("Failed to broadcast transaction to Ethereum Classic node. Error: " + errorMsg);
        }

        return response.result();
    }

    // A simple inner record to map the JSON-RPC request to the node
    private record RpcRequest(String jsonrpc, String method, List<Object> params, int id) {}

    // A simple inner record to map the JSON-RPC response from the node
    private record RpcResponse(String jsonrpc, int id, String result, Object error) {}
}