package com.ayrotek.coldwalletmanagerservice.controller;

import com.ayrotek.coldwalletmanagerservice.dto.SignTransactionRequest;
import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import com.ayrotek.coldwalletmanagerservice.service.KeyRetrievalService;
import com.ayrotek.coldwalletmanagerservice.service.SignTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.web3j.crypto.RawTransaction;

import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/hsm")
public class HsmController {

    private final SignTransactionService signTransactionService;
    private final KeyRetrievalService keyRetrievalService;
    private final WalletRepository walletRepository;

    public HsmController(SignTransactionService signTransactionService, 
                         KeyRetrievalService keyRetrievalService,
                         WalletRepository walletRepository) {
        this.signTransactionService = signTransactionService;
        this.keyRetrievalService = keyRetrievalService;
        this.walletRepository = walletRepository;
    }

    @PostMapping("/sign")
    public String signTransaction(@RequestBody SignTransactionRequest request) throws Exception {
        // 0. Check if the wallet exists
        Wallet wallet = walletRepository.findByAddress(request.getAddress())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for address: " + request.getAddress()));

        // 1. Fetch KeyPair (PrivateKey and ECPublicKey) from HSM using request.getAddress()
        KeyRetrievalService.KeyPairResult keyPairResult = keyRetrievalService.getKeyPairByAddress(request.getAddress());
        PrivateKey privateKey = keyPairResult.getPrivateKey();
        ECPublicKey publicKey = keyPairResult.getPublicKey();

        // 2. Create the RawTransaction from the request
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                request.getNonce(),
                request.getGasPrice(),
                request.getGasLimit(),
                request.getTo(),
                request.getValue(),
                request.getData()
        );

        // 3. Sign the transaction
        return signTransactionService.signTransaction(rawTransaction, privateKey, publicKey, request.getChainId());
    }
}
