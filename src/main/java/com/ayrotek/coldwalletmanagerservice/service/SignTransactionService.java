package com.ayrotek.coldwalletmanagerservice.service;

import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;

@Service
public class SignTransactionService {

    private final Provider pkcs11Provider;

    public SignTransactionService(Provider pkcs11Provider) {
        this.pkcs11Provider = pkcs11Provider;
    }

    /**
     * Signs an Ethereum RawTransaction using the private key residing in the HSM
     * and constructs the final signed transaction bytes ready for broadcasting.
     *
     * @param rawTransaction The transaction to be signed.
     * @param privateKey     The reference to the private key in the HSM.
     * @param publicKey      The public key corresponding to the private key (needed for recovery ID calculation).
     * @param chainId        The network chain ID (e.g., 1 for Mainnet, 11155111 for Sepolia).
     * @return The hex-encoded signed transaction ready to be broadcasted (e.g., via eth_sendRawTransaction).
     * @throws Exception If signing or ASN.1 parsing fails.
     */
    public String signTransaction(RawTransaction rawTransaction, PrivateKey privateKey, ECPublicKey publicKey, long chainId) throws Exception {
        // 1. Encode the transaction to get the bytes that need to be hashed and signed
        // When signing EIP-155 transactions, the chainId needs to be encoded in the payload before hashing
        byte[] encodedTransaction = TransactionEncoder.encode(rawTransaction, chainId);

        // 2. Calculate the Keccak-256 hash of the encoded transaction
        byte[] transactionHash = org.web3j.crypto.Hash.sha3(encodedTransaction);

        // 3. Ask SoftHSM to sign the hash
        Signature signature = Signature.getInstance("NONEwithECDSA", pkcs11Provider);
        signature.initSign(privateKey);
        signature.update(transactionHash);
        byte[] derSignature = signature.sign();

        // 4. Parse the DER signature to extract R and S components
        BigInteger r;
        BigInteger s;
        try (ASN1InputStream asn1 = new ASN1InputStream(derSignature)) {
            ASN1Sequence seq = (ASN1Sequence) asn1.readObject();
            r = ((ASN1Integer) seq.getObjectAt(0)).getValue();
            s = ((ASN1Integer) seq.getObjectAt(1)).getValue();
        }

        // Ethereum requires S to be in the lower half of the curve to prevent transaction malleability (EIP-2)
        BigInteger curveN = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
        BigInteger halfCurveN = curveN.shiftRight(1);
        if (s.compareTo(halfCurveN) > 0) {
            s = curveN.subtract(s);
        }

        // 5. Calculate the recovery ID (v)
        // Since the HSM only gives us R and S, we need to figure out which of the 2 possible 'v' values is correct.
        // We do this by trying both and seeing which one recovers our known public key.
        int recId = -1;
        BigInteger pubKeyX = publicKey.getW().getAffineX();
        BigInteger pubKeyY = publicKey.getW().getAffineY();
        byte[] pubKeyBytes = new byte[64];
        System.arraycopy(Numeric.toBytesPadded(pubKeyX, 32), 0, pubKeyBytes, 0, 32);
        System.arraycopy(Numeric.toBytesPadded(pubKeyY, 32), 0, pubKeyBytes, 32, 32);
        BigInteger pubKeyBigInt = new BigInteger(1, pubKeyBytes);

        for (int i = 0; i < 2; i++) {
            // Revert back to Sign.SignatureData since ECDSASignature could not be found
            Sign.SignatureData signatureData = new Sign.SignatureData((byte) i, Numeric.toBytesPadded(r, 32), Numeric.toBytesPadded(s, 32));
            BigInteger k = Sign.signedMessageHashToKey(transactionHash, signatureData);
            if (k != null && k.equals(pubKeyBigInt)) {
                recId = i;
                break;
            }
        }

        if (recId == -1) {
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        }

        // 6. Calculate the final V value incorporating the chainId (EIP-155)
        long v = (chainId * 2) + 35 + recId;

        // 7. Create the final SignatureData object
        Sign.SignatureData finalSignatureData = new Sign.SignatureData(
                BigInteger.valueOf(v).toByteArray(),
                Numeric.toBytesPadded(r, 32),
                Numeric.toBytesPadded(s, 32)
        );

        // 8. Re-encode the transaction, this time WITH the signature data
        byte[] signedMessage = TransactionEncoder.encode(rawTransaction, finalSignatureData);

        // Return the final hex string, which can be sent to an Ethereum node
        return Numeric.toHexString(signedMessage);
    }
}
