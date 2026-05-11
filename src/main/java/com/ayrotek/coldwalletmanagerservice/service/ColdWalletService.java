package com.ayrotek.coldwalletmanagerservice.service;

import com.ayrotek.coldwalletmanagerservice.entity.Wallet;
import com.ayrotek.coldwalletmanagerservice.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import java.math.BigInteger;
import java.security.AuthProvider;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Provider;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.time.LocalDateTime;

@Service
public class ColdWalletService {

    private final Provider pkcs11Provider;
    private final WalletRepository walletRepository;
    private static final char[] PIN = "1234".toCharArray();

    public ColdWalletService(Provider pkcs11Provider, WalletRepository walletRepository) {
        this.pkcs11Provider = pkcs11Provider;
        this.walletRepository = walletRepository;
    }

    public KeyPair generateECKeyPair() throws Exception {
        // Log in to the token using KeyStore.load before attempting to generate keys
        KeyStore keyStore = KeyStore.getInstance("PKCS11", pkcs11Provider);
        keyStore.load(null, PIN);
        
        // Ensure the provider is logged in
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

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", pkcs11Provider);
        // Ethereum uses the secp256k1 curve
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
        kpg.initialize(ecSpec);
        return kpg.generateKeyPair();
    }

    /**
     * Derives the Ethereum address from an EC key pair.
     * The address is the last 20 bytes of the Keccak-256 hash of the public key.
     *
     * @param keyPair The EC key pair.
     * @return The Ethereum address as a hex string with a "0x" prefix.
     */
    public String getAddressFromKey(KeyPair keyPair) {
        if (!(keyPair.getPublic() instanceof ECPublicKey ecPublicKey)) {
            throw new IllegalArgumentException("Public key is not an EC public key.");
        }

        // Extract X and Y coordinates from the public key
        ECPoint ecPoint = ecPublicKey.getW();
        BigInteger x = ecPoint.getAffineX();
        BigInteger y = ecPoint.getAffineY();

        // The public key for Ethereum is the 64-byte concatenation of X and Y
        byte[] xBytes = Numeric.toBytesPadded(x, 32);
        byte[] yBytes = Numeric.toBytesPadded(y, 32);
        byte[] publicKeyBytes = new byte[64];
        System.arraycopy(xBytes, 0, publicKeyBytes, 0, 32);
        System.arraycopy(yBytes, 0, publicKeyBytes, 32, 32);

        // Convert the public key bytes to a BigInteger
        BigInteger publicKeyBigInt = new BigInteger(1, publicKeyBytes);

        // web3j's Keys.getAddress handles the hashing (Keccak-256) and returns the last 20 bytes as hex
        String addressHex = Keys.getAddress(publicKeyBigInt);
        
        // Add the '0x' prefix as requested
        return "0x" + addressHex;
    }

    public String getPublicKeyHex(KeyPair keyPair) {
        if (!(keyPair.getPublic() instanceof ECPublicKey ecPublicKey)) {
            throw new IllegalArgumentException("Public key is not an EC public key.");
        }

        ECPoint ecPoint = ecPublicKey.getW();
        BigInteger x = ecPoint.getAffineX();
        BigInteger y = ecPoint.getAffineY();

        byte[] xBytes = Numeric.toBytesPadded(x, 32);
        byte[] yBytes = Numeric.toBytesPadded(y, 32);
        byte[] publicKeyBytes = new byte[64];
        System.arraycopy(xBytes, 0, publicKeyBytes, 0, 32);
        System.arraycopy(yBytes, 0, publicKeyBytes, 32, 32);
        
        // The uncompressed format prefix is 0x04, so we prepend it
        return "0x04" + Numeric.toHexStringNoPrefix(publicKeyBytes);
    }

    /**
     * Generates a new EC key pair using the PKCS11 provider, derives its Ethereum address,
     * and saves it to the database.
     *
     * @param name The logical name of the wallet (e.g. "Company treasure wallet")
     * @return The newly generated Wallet entity.
     * @throws Exception if key generation fails.
     */
    @Transactional
    public Wallet generateNewWalletAddress(String name) throws Exception {
        KeyPair keyPair = generateECKeyPair();
        String address = getAddressFromKey(keyPair);
        String publicKeyHex = getPublicKeyHex(keyPair);
        
        LocalDateTime now = LocalDateTime.now();

        Wallet wallet = new Wallet(name, address, publicKeyHex, now);
        return walletRepository.save(wallet);
    }
}
