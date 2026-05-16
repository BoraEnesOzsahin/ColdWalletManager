package com.ayrotek.coldwalletmanagerservice.controller;

import com.ayrotek.coldwalletmanagerservice.dto.SignTransactionRequest;
import com.ayrotek.coldwalletmanagerservice.dto.SignTransactionResponse;
import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import com.ayrotek.coldwalletmanagerservice.service.CheckBalanceService;
import com.ayrotek.coldwalletmanagerservice.service.VaultKeyRetrievalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

@RestController
@RequestMapping("/api/v1/vault")
public class VaultController {

    private final VaultKeyRetrievalService vaultKeyRetrievalService;
    private final WalletRepository walletRepository;
    private final CheckBalanceService checkBalanceService; // Use this as the RPC client

    public VaultController(VaultKeyRetrievalService vaultKeyRetrievalService,
                           WalletRepository walletRepository,
                           CheckBalanceService checkBalanceService) {
        this.vaultKeyRetrievalService = vaultKeyRetrievalService;
        this.walletRepository = walletRepository;
        this.checkBalanceService = checkBalanceService;
    }

    @PostMapping("/sign")
    public SignTransactionResponse signTransaction(@RequestBody SignTransactionRequest request) {
        // 1. Check if the wallet exists
        Wallet wallet = walletRepository.findByAddressIgnoreCase(request.getAddress())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for address: " + request.getAddress()));

        if (wallet.getHsmAlias() == null || wallet.getHsmAlias().isBlank()) {
             throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Wallet does not have an associated Vault key");
        }

        // 2. Retrieve the key pair from Vault
        ECKeyPair ecKeyPair = vaultKeyRetrievalService.getKeyPairFromVault(wallet.getHsmAlias());

        // 3. Fetch missing details from the network if not provided in the request
        BigInteger nonce = request.getNonce();
        if (nonce == null) {
            nonce = checkBalanceService.getTransactionCount(request.getAddress());
        }

        BigInteger gasPrice = request.getGasPrice();
        if (gasPrice == null) {
            gasPrice = checkBalanceService.getGasPrice();
        }

        BigInteger gasLimit = request.getGasLimit();
        if (gasLimit == null) {
            gasLimit = BigInteger.valueOf(21000); // Default gas limit for standard ETH transfers
        }

        // 4. Create the RawTransaction
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                request.getTo(),
                request.getValue(),
                request.getData() != null ? request.getData() : ""
        );

        // 5. Sign the transaction locally using Web3j
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, request.getChainId(), org.web3j.crypto.Credentials.create(ecKeyPair));
        String signedTxHex = Numeric.toHexString(signedMessage);

        // 6. Broadcast the transaction to the network using Tatum RPC
        String txHash = checkBalanceService.sendRawTransaction(signedTxHex);

        return new SignTransactionResponse(txHash);
    }
}
