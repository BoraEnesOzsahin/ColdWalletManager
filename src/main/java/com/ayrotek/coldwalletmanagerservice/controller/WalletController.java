package com.ayrotek.coldwalletmanagerservice.controller;

import com.ayrotek.coldwalletmanagerservice.dto.BalanceResponse;
import com.ayrotek.coldwalletmanagerservice.dto.WalletGenerationRequest;
import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import com.ayrotek.coldwalletmanagerservice.service.CheckBalanceService;
import com.ayrotek.coldwalletmanagerservice.service.ColdWalletService;
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

    private final ColdWalletService coldWalletService;
    private final WalletRepository walletRepository;
    private final CheckBalanceService checkBalanceService;

    public WalletController(ColdWalletService coldWalletService, 
                            WalletRepository walletRepository,
                            CheckBalanceService checkBalanceService) {
        this.coldWalletService = coldWalletService;
        this.walletRepository = walletRepository;
        this.checkBalanceService = checkBalanceService;
    }

    @PostMapping("/generate")
    public Wallet generateWallet(@RequestBody WalletGenerationRequest request) throws Exception {
        return coldWalletService.generateNewWalletAddress(request.getName());
    }

    @GetMapping("/allWallets")
    public List<Wallet> getAllWallets() {
        return walletRepository.findAll();
    }

    @GetMapping("/{address}/balance")
    public BalanceResponse getWalletBalance(@PathVariable String address) {
        // Validate if we actually own this wallet in our database.
        // We use findByAddressIgnoreCase because Ethereum addresses are case-insensitive
        // (the mixed case is just an optional checksum).
        walletRepository.findByAddressIgnoreCase(address)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for address: " + address));

        BigDecimal balance = checkBalanceService.getBalance(address);
        return new BalanceResponse(address, balance);
    }
}
