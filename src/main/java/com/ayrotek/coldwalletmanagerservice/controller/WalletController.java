package com.ayrotek.coldwalletmanagerservice.controller;

import com.ayrotek.coldwalletmanagerservice.dto.BalanceResponse;
import com.ayrotek.coldwalletmanagerservice.dto.WalletGenerationRequest;
import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import com.ayrotek.coldwalletmanagerservice.service.CheckBalanceService;
import com.ayrotek.coldwalletmanagerservice.service.VaultWalletService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final VaultWalletService vaultWalletService;
    private final WalletRepository walletRepository;
    private final CheckBalanceService checkBalanceService;

    public WalletController(VaultWalletService vaultWalletService, 
                            WalletRepository walletRepository,
                            CheckBalanceService checkBalanceService) {
        this.vaultWalletService = vaultWalletService;
        this.walletRepository = walletRepository;
        this.checkBalanceService = checkBalanceService;
    }

    @PostMapping("/generate")
    public Wallet generateWallet(@RequestBody WalletGenerationRequest request) throws Exception {
        return vaultWalletService.generateNewWalletAddress(request.getName());
    }

    @GetMapping("/allWallets")
    public List<Wallet> getAllWallets() {
        System.out.println("Selamın Aleykum");
        return walletRepository.findAll();
    }

    @GetMapping("/{address}/balance")
    public BalanceResponse getWalletBalance(@PathVariable String address) {
        walletRepository.findByAddressIgnoreCase(address)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for address: " + address));

        BigDecimal balance = checkBalanceService.getBalance(address);
        return new BalanceResponse(address, balance);
    }
}
