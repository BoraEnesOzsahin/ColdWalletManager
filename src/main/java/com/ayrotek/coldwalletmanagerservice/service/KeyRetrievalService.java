package com.ayrotek.coldwalletmanagerservice.service;

import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.web3j.utils.Numeric;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import java.math.BigInteger;
import java.security.AuthProvider;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Optional;

@Service
public class KeyRetrievalService {

    private final Provider pkcs11Provider;
    private final ColdWalletService coldWalletService;
    private final WalletRepository walletRepository;

    // Hardcoded PIN for now based on docker-compose
    private static final char[] PIN = "1234".toCharArray();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public KeyRetrievalService(Provider pkcs11Provider, ColdWalletService coldWalletService, WalletRepository walletRepository) {
        this.pkcs11Provider = pkcs11Provider;
        this.coldWalletService = coldWalletService;
        this.walletRepository = walletRepository;
    }

    public KeyPairResult getKeyPairByAddress(String address) throws Exception {
        Optional<Wallet> walletOptional = walletRepository.findByAddressIgnoreCase(address);
        if (walletOptional.isEmpty()) {
            throw new RuntimeException("Wallet not found for address: " + address);
        }
        Wallet wallet = walletOptional.get();
        String publicKeyHex = wallet.getPublicKey();

        // Ensure the provider is logged in FIRST before any crypto operations that might use it
        if (pkcs11Provider instanceof AuthProvider) {
            ((AuthProvider) pkcs11Provider).login(null, new CallbackHandler() {
                @Override
                public void handle(Callback[] callbacks) {
                    for (Callback callback : callbacks) {
                        if (callback instanceof PasswordCallback) {
                            ((PasswordCallback) callback).setPassword(PIN);
                        }
                    }
                }
            });
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS11", pkcs11Provider);
        keyStore.load(null, PIN);

        // Reconstruct ECPublicKey from hex
        ECPublicKey publicKey = reconstructPublicKey(publicKeyHex);

        java.util.Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                java.security.Key key = keyStore.getKey(alias, PIN);
                if (key instanceof PrivateKey) {
                    PrivateKey privateKey = (PrivateKey) key;

                    // We know the public key from the database and the private key from the HSM.
                    // We just need to find the matching pair.
                    // To verify without extracting the private key, we can try to derive the public key from the private key
                    // if the HSM allows it, OR we can sign a dummy message with the private key and verify with the public key.
                    // However, we already have `getAddressFromKey` which checks the address against the public key.
                    // Since `getAddressFromKey` throws an exception if it's not an EC public key, we use the reconstructed one.

                    java.security.KeyPair tempKeyPair = new java.security.KeyPair(publicKey, privateKey);

                    // Wait, getAddressFromKey only uses the public key!
                    // So if we pass `publicKey` here, `derivedAddress` will ALWAYS be the address of `publicKeyHex`!
                    // This means we are not actually verifying that the `privateKey` belongs to `publicKey`!
                    // We are just finding the FIRST private key and returning it.
                    // But if there are multiple private keys, it might return the wrong one.
                    // Still, we need to find WHICH private key corresponds to this public key.

                    // SoftHSM doesn't easily expose the public key for a private key without a certificate.
                    // But wait, when we generated the key pair, it might have stored the public key under the same alias!
                    java.security.Key pubKeyEntry = null;
                    try {
                        pubKeyEntry = keyStore.getKey(alias, null); // Public keys usually don't need a PIN
                    } catch (Exception e) {
                        // ignore
                    }

                    if (pubKeyEntry == null) {
                        java.security.cert.Certificate cert = keyStore.getCertificate(alias);
                        if (cert != null) {
                            pubKeyEntry = cert.getPublicKey();
                        }
                    }

                    if (pubKeyEntry instanceof ECPublicKey) {
                        ECPublicKey ecPubKeyEntry = (ECPublicKey) pubKeyEntry;
                        java.security.KeyPair keyPairFromHsm = new java.security.KeyPair(ecPubKeyEntry, privateKey);
                        String derivedAddress = coldWalletService.getAddressFromKey(keyPairFromHsm);
                        if (derivedAddress.equalsIgnoreCase(address)) {
                            return new KeyPairResult(privateKey, publicKey);
                        }
                    } else {
                        // If we can't get the public key from the HSM, we might have to fallback to a sign/verify approach
                        // to see if the private key matches the public key we have in the DB.
                        try {
                            java.security.Signature sig = java.security.Signature.getInstance("NONEwithECDSA", pkcs11Provider);
                            sig.initSign(privateKey);
                            byte[] dummyData = "test".getBytes();
                            sig.update(dummyData);
                            byte[] signature = sig.sign();

                            java.security.Signature verifier = java.security.Signature.getInstance("NONEwithECDSA", BouncyCastleProvider.PROVIDER_NAME);
                            verifier.initVerify(publicKey);
                            verifier.update(dummyData);
                            if (verifier.verify(signature)) {
                                // Match!
                                return new KeyPairResult(privateKey, publicKey);
                            }
                        } catch (Exception e) {
                            // Signature failed, probably not the right key type or something, just continue
                        }
                    }
                }
            }
        }

        throw new RuntimeException("Key pair not found for address: " + address);
    }

    private ECPublicKey reconstructPublicKey(String publicKeyHex) throws Exception {
        String cleanHex = Numeric.cleanHexPrefix(publicKeyHex);
        // As per getPublicKeyHex, the first byte (2 hex chars) is the prefix 0x04
        BigInteger x = new BigInteger(1, Numeric.hexStringToByteArray(cleanHex.substring(2, 66)));
        BigInteger y = new BigInteger(1, Numeric.hexStringToByteArray(cleanHex.substring(66)));

        // Use BouncyCastle directly to generate the parameter spec.
        // It provides comprehensive support for secp256k1.
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256k1"));
        java.security.KeyPair dummyKeyPair = kpg.generateKeyPair();
        ECPublicKey dummyPublicKey = (ECPublicKey) dummyKeyPair.getPublic();
        ECParameterSpec ecParams = dummyPublicKey.getParams();

        ECPoint point = new ECPoint(x, y);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(point, ecParams);
        KeyFactory kf = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        return (ECPublicKey) kf.generatePublic(keySpec);
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
