package com.ayrotek.coldwalletmanagerservice.service;

import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
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

        // Read the secret from Vault KV engine
        VaultResponse response = vaultTemplate.read("secret/data/wallets/" + hsmAlias);
        
        if (response == null || response.getData() == null) {
            throw new RuntimeException("Private key not found in Vault for alias: " + hsmAlias);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
        
        if (data == null || !data.containsKey("privateKey")) {
             throw new RuntimeException("Invalid data format in Vault for alias: " + hsmAlias);
        }

        String privateKeyHex = (String) data.get("privateKey");
        
        // Construct the Web3j Credentials/ECKeyPair from the raw private key
        Credentials credentials = Credentials.create(privateKeyHex);
        return credentials.getEcKeyPair();
    }
}
