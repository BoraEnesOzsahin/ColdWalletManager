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
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
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

        // Convert the requested value (in ETC/Ether) to Wei
        BigInteger valueInWei = BigInteger.ZERO;
        if (request.getValue() != null) {
            valueInWei = Convert.toWei(request.getValue(), Convert.Unit.ETHER).toBigInteger();
        }

        // 3. Fetch missing details from the network if not provided in the request
        BigInteger nonce = request.getNonce();
        if (nonce == null) {
            nonce = checkBalanceService.getTransactionCount(request.getAddress());
        }

        BigInteger gasPrice;
        // Always fetch the current gas price and increase it by 20% to handle replacements
        BigInteger currentGasPrice = checkBalanceService.getGasPrice();
        gasPrice = currentGasPrice.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));

        // If the user provided a gas price, use the higher of the two
        if (request.getGasPrice() != null && request.getGasPrice().compareTo(gasPrice) > 0) {
            gasPrice = request.getGasPrice();
        }


        BigInteger gasLimit = request.getGasLimit();
        // If gasLimit is not provided or is zero, estimate it
        if (gasLimit == null || gasLimit.compareTo(BigInteger.ZERO) <= 0) {
            gasLimit = checkBalanceService.estimateGas(
                request.getAddress(), // from address
                request.getTo(),
                valueInWei, // Use the converted Wei value
                request.getData()
            );
            // Add a buffer to the estimated gas for safety (e.g., 20%)
            gasLimit = gasLimit.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
        }

        // 4. Create the RawTransaction
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                request.getTo(),
                valueInWei, // Use the converted Wei value
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
