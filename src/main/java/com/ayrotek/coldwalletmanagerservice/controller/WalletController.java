package com.ayrotek.coldwalletmanagerservice.controller;

import com.ayrotek.coldwalletmanagerservice.dto.WalletGenerationRequest;
import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import com.ayrotek.coldwalletmanagerservice.service.ColdWalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final ColdWalletService coldWalletService;
    private final WalletRepository walletRepository;

    public WalletController(ColdWalletService coldWalletService, WalletRepository walletRepository) {
        this.coldWalletService = coldWalletService;
        this.walletRepository = walletRepository;
    }

    @PostMapping("/generate")
    public Wallet generateWallet(@RequestBody WalletGenerationRequest request) throws Exception {
        return coldWalletService.generateNewWalletAddress(request.getName());
    }

    @GetMapping("/allWallets")
    public List<Wallet> getAllWallets() {
        return walletRepository.findAll();
    }
}
