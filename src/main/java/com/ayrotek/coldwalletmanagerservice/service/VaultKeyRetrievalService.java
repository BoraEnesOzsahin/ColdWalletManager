package com.ayrotek.coldwalletmanagerservice.service;

import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.Versioned;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

import java.util.Map;

@Service
public class VaultKeyRetrievalService {

    private final VaultTemplate vaultTemplate;

    public VaultKeyRetrievalService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    /**
     * Retrieves the ECKeyPair from Vault using the stored HSM Alias (which is now the Vault key name).
     *
     * @param hsmAlias The name of the key in Vault.
     * @return The ECKeyPair object.
     */
    public ECKeyPair getKeyPairFromVault(String hsmAlias) {
        if (hsmAlias == null || hsmAlias.isBlank()) {
            throw new IllegalArgumentException("HSM Alias cannot be null or empty");
        }

        // Read the secret from Vault KV v2 engine at "secret/" path
        Versioned<Map<String, Object>> response = vaultTemplate.opsForVersionedKeyValue("secret")
                .get("wallets/" + hsmAlias);
        
        if (response == null || !response.hasData() || response.getData() == null) {
            throw new RuntimeException("Private key not found in Vault for alias: " + hsmAlias);
        }

        Map<String, Object> data = response.getData();
        
        if (!data.containsKey("privateKey")) {
             throw new RuntimeException("Invalid data format in Vault for alias: " + hsmAlias);
        }

        String privateKeyHex = (String) data.get("privateKey");
        
        // Construct the Web3j Credentials/ECKeyPair from the raw private key
        Credentials credentials = Credentials.create(privateKeyHex);
        return credentials.getEcKeyPair();
    }
}
