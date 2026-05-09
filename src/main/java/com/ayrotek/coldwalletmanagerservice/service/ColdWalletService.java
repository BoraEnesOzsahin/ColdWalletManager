package com.ayrotek.coldwalletmanagerservice.service;

import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;

/** RECONFIGURE SOFTHSM2 (Option 2: Install SoftHSM in your Spring Boot App's Container (Recommended for Java))*/

@Service
public class ColdWalletService {

    private final Provider pkcs11Provider;

    public ColdWalletService(Provider pkcs11Provider) {
        this.pkcs11Provider = pkcs11Provider;
    }

    public KeyPair generateECKeyPair() throws Exception {
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

    /**
     * Generates a new EC key pair using the PKCS11 provider and derives its Ethereum address.
     *
     * @return The newly generated Ethereum address as a hex string with a "0x" prefix.
     * @throws Exception if key generation fails.
     */
    public String generateNewWalletAddress() throws Exception {
        KeyPair keyPair = generateECKeyPair();
        return getAddressFromKey(keyPair);
    }
}
