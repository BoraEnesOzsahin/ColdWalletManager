package com.ayrotek.coldwalletmanagerservice.controller;

import com.ayrotek.coldwalletmanagerservice.service.ColdWalletService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final ColdWalletService coldWalletService;

    public WalletController(ColdWalletService coldWalletService) {
        this.coldWalletService = coldWalletService;
    }

    @PostMapping("/generate")
    public String generateWallet() throws Exception {
        return coldWalletService.generateNewWalletAddress();
    }
}
