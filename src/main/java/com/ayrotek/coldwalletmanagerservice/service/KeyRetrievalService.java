package com.ayrotek.coldwalletmanagerservice.service;

import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.interfaces.ECPublicKey;

@Service
public class KeyRetrievalService {

    private final Provider pkcs11Provider;
    private final ColdWalletService coldWalletService;
    
    // Hardcoded PIN for now based on docker-compose
    private static final char[] PIN = "1234".toCharArray();

    public KeyRetrievalService(Provider pkcs11Provider, ColdWalletService coldWalletService) {
        this.pkcs11Provider = pkcs11Provider;
        this.coldWalletService = coldWalletService;
    }

    public KeyPairResult getKeyPairByAddress(String address) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS11", pkcs11Provider);
        keyStore.load(null, PIN);

        // Iterate through aliases to find the key pair matching the address
        //Private key is just the reference to our actual private key we are not exposing our key.
        java.util.Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                java.security.Key key = keyStore.getKey(alias, PIN);
                if (key instanceof PrivateKey) {
                    java.security.cert.Certificate cert = keyStore.getCertificate(alias);
                    if (cert != null && cert.getPublicKey() instanceof ECPublicKey) {
                        ECPublicKey publicKey = (ECPublicKey) cert.getPublicKey();
                        // Generate keypair just to get address. We can just use the public key actually if we have a helper method.
                        // Let's create a temporary KeyPair just to use the existing getAddressFromKey method.
                        java.security.KeyPair tempKeyPair = new java.security.KeyPair(publicKey, (PrivateKey) key);
                        String derivedAddress = coldWalletService.getAddressFromKey(tempKeyPair);
                        
                        if (derivedAddress.equalsIgnoreCase(address)) {
                            return new KeyPairResult((PrivateKey) key, publicKey);
                        }
                    }
                }
            }
        }
        
        throw new RuntimeException("Key pair not found for address: " + address);
    }
    
    public static class KeyPairResult {
        private final PrivateKey privateKey;
        private final ECPublicKey publicKey;

        public KeyPairResult(PrivateKey privateKey, ECPublicKey publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public PrivateKey getPrivateKey() {
            return privateKey;
        }

        public ECPublicKey getPublicKey() {
            return publicKey;
        }
    }
}
