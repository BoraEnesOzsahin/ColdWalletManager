package com.ayrotek.coldwalletmanagerservice.service;

import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.core.VaultTemplate;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;
import org.springframework.vault.support.VaultTransitKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

import java.time.LocalDateTime;
import java.util.Base64;
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
            // 1. Instruct Vault to generate the key internally. The private key never leaves Vault.
            VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
                    .type("ecdsa-secp256k1")
                    .exportable(false) // Ensures the private key can never be read via API
                    .build();
            vaultTemplate.opsForTransit().createKey(keyName, request);

            // 2. Retrieve only the Public Key metadata from Vault
            VaultTransitKey vaultKey = vaultTemplate.opsForTransit().getKey(keyName);
            
            if (vaultKey == null) {
                throw new IllegalStateException("Failed to retrieve the generated key from Vault. The returned key is null.");
            }
            
            // 3. Extract the public key string (Usually base64 DER encoded in Vault)
            // Note: Vault returns a DER encoded public key. You will need a utility method
            // here to decode the DER into a raw 64-byte uncompressed hex string for Web3j.
            String publicKeyHex = extractRawPublicKey(vaultKey);
            String address = "0x" + Keys.getAddress(publicKeyHex);

            LocalDateTime now = LocalDateTime.now();
            Wallet wallet = new Wallet(name, address, publicKeyHex, now);
            wallet.setHsmAlias(keyName); // Store the vault key name here
            
            return walletRepository.save(wallet);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate and store wallet in Vault", e);
        }
    }

    /**
     * Extracts the raw 64-byte uncompressed public key from a Vault Transit Key.
     */
    private String extractRawPublicKey(VaultTransitKey vaultKey) {
        if (vaultKey == null) {
            throw new IllegalArgumentException("VaultTransitKey cannot be null");
        }

        // Vault returns the public key as a PEM-encoded X.509 SubjectPublicKeyInfo
        Object pemKeyObj = vaultKey.getKeys().get(String.valueOf(vaultKey.getLatestVersion()));
        if (pemKeyObj == null) {
            throw new RuntimeException("Public key not found in Vault response");
        }
        
        String pemKey = pemKeyObj.toString();

        // Clean the PEM string wrappers and newlines
        String base64Key = pemKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        // Parse the ASN.1 DER structure to extract the raw public key bytes
        byte[] decodedDer = Base64.getDecoder().decode(base64Key);
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(decodedDer);
        byte[] pubKeyBytes = spki.getPublicKeyData().getBytes();

        // The public key bytes from Vault are uncompressed, meaning they are prefixed with a 0x04 byte.
        // Web3j expects the raw 64 bytes without the 0x04 prefix to calculate the address hash.
        byte[] rawKey = new byte[64];
        System.arraycopy(pubKeyBytes, 1, rawKey, 0, 64);

        return Numeric.toHexStringNoPrefix(rawKey);
    }
}
