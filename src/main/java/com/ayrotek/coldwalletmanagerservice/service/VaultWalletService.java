package com.ayrotek.coldwalletmanagerservice.service;

import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.core.VaultTemplate;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VaultWalletService {

    private final VaultTemplate vaultTemplate;
    private final WalletRepository walletRepository;

    public VaultWalletService(VaultTemplate vaultTemplate, WalletRepository walletRepository) {
        this.vaultTemplate = vaultTemplate;
        this.walletRepository = walletRepository;
    }

    /**
     * Generates a new EC key pair, derives its Ethereum address, saves it to the database,
     * and securely stores the private key in HashiCorp Vault.
     *
     * @param name The logical name of the wallet
     * @return The newly generated Wallet entity.
     */
    @Transactional
    public Wallet generateNewWalletAddress(String name) {
        String keyName = "wallet-" + UUID.randomUUID().toString();

        try {
            org.web3j.crypto.ECKeyPair ecKeyPair = Keys.createEcKeyPair();
            String privateKeyHex = Numeric.toHexStringNoPrefix(ecKeyPair.getPrivateKey());
            String publicKeyHex = Numeric.toHexStringNoPrefix(ecKeyPair.getPublicKey());
            String address = "0x" + Keys.getAddress(ecKeyPair);

            // Store the private key in Vault KV engine
            // By default, HashiCorp vault in dev mode enables a KV v2 engine at "secret/"
            Map<String, Object> secret = new HashMap<>();
            secret.put("privateKey", privateKeyHex);
            vaultTemplate.opsForVersionedKeyValue("secret").put("wallets/" + keyName, secret);

            LocalDateTime now = LocalDateTime.now();
            Wallet wallet = new Wallet(name, address, publicKeyHex, now);
            wallet.setHsmAlias(keyName); // Store the vault key name here
            
            return walletRepository.save(wallet);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate and store wallet in Vault", e);
        }
    }
}
