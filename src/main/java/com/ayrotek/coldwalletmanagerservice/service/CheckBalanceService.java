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
    
    // Using the Tatum gateway for Ethereum Classic Mainnet as requested
    private static final String ETC_RPC_URL = "https://ethereum-classic-mainnet.gateway.tatum.io";

    public CheckBalanceService() {
        this.restClient = RestClient.create(ETC_RPC_URL);
    }

    public BigDecimal getBalance(String address) {
        // Tatum Ethereum Classic JSON-RPC expects this specific payload format
        RpcRequest requestPayload = new RpcRequest("2.0", "eth_getBalance", List.of(address, "latest"), 1);

        // Send the POST request to the Tatum Ethereum Classic node
        RpcResponse response = restClient.post()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(requestPayload)
                .retrieve()
                .body(RpcResponse.class);

        if (response == null || response.result() == null) {
            throw new RuntimeException("Failed to fetch balance from Ethereum Classic node via Tatum.");
        }

        // The balance comes back as a hex string representing Wei
        String hexBalance = response.result();
        BigInteger balanceInWei = Numeric.decodeQuantity(hexBalance);

        // Convert Wei to Ether (ETC uses the same 10^18 Wei denomination as ETH)
        return Convert.fromWei(balanceInWei.toString(), Convert.Unit.ETHER);
    }

    // A simple inner record to map the JSON-RPC request to the node
    private record RpcRequest(String jsonrpc, String method, List<String> params, int id) {}

    // A simple inner record to map the JSON-RPC response from the node
    private record RpcResponse(String jsonrpc, int id, String result) {}
}
