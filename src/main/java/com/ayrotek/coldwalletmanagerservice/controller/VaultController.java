package com.ayrotek.coldwalletmanagerservice.controller;

import com.ayrotek.coldwalletmanagerservice.dto.SignTransactionRequest;
import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
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

@RestController
@RequestMapping("/api/v1/vault")
public class VaultController {

    private final VaultKeyRetrievalService vaultKeyRetrievalService;
    private final WalletRepository walletRepository;

    public VaultController(VaultKeyRetrievalService vaultKeyRetrievalService,
                           WalletRepository walletRepository) {
        this.vaultKeyRetrievalService = vaultKeyRetrievalService;
        this.walletRepository = walletRepository;
    }

    @PostMapping("/sign")
    public String signTransaction(@RequestBody SignTransactionRequest request) throws Exception {
        // 1. Check if the wallet exists
        Wallet wallet = walletRepository.findByAddressIgnoreCase(request.getAddress())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for address: " + request.getAddress()));

        if (wallet.getHsmAlias() == null || wallet.getHsmAlias().isBlank()) {
             throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Wallet does not have an associated Vault key");
        }

        // 2. Retrieve the key pair from Vault
        ECKeyPair ecKeyPair = vaultKeyRetrievalService.getKeyPairFromVault(wallet.getHsmAlias());

        // 3. Create the RawTransaction from the request
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                request.getNonce(),
                request.getGasPrice(),
                request.getGasLimit(),
                request.getTo(),
                request.getValue(),
                request.getData()
        );

        // 4. Sign the transaction locally using Web3j
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, request.getChainId(), org.web3j.crypto.Credentials.create(ecKeyPair));

        return Numeric.toHexString(signedMessage);
    }
}
