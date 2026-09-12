package com.digicash.app.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Central cryptographic service for DIGICASH.
 *
 * Responsibilities:
 *  - Generate and retain the device's RSA identity key pair inside the
 *    Android Keystore.
 *  - Expose the public key and SHA-256 fingerprint.
 *  - Sign and verify payment/transaction payloads with SHA256withRSA.
 *  - Provide SecureRandom-backed nonce and transaction ID generation.
 *  - Provide safe Base64 encode/decode helpers.
 */
public class CryptoManager {

    private static final String LOG_TAG = "DigiCashCryptoManager";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Ensures that a complete and usable DIGICASH RSA identity exists.
     *
     * Important:
     * containsAlias() alone is NOT considered sufficient.
     * A physical device can have an alias without a usable certificate.
     *
     * In that case, only the DIGICASH identity alias is removed and a fresh
     * RSA identity is generated.
     */
    public synchronized void generateKeyPairIfNotExists()
            throws CryptoOperationException {

        KeyStore keyStore = loadKeyStore();

        try {
            if (keyStore.containsAlias(
                    SecurityConstants.KEY_ALIAS_RSA_IDENTITY)) {

                Certificate certificate =
                        keyStore.getCertificate(
                                SecurityConstants.KEY_ALIAS_RSA_IDENTITY);

                if (certificate != null
                        && certificate.getPublicKey() != null) {

                    return;
                }

                /*
                 * The alias exists but the certificate/public key is missing.
                 * Treat the identity as incomplete and repair it.
                 */
                Log.w(
                        LOG_TAG,
                        "DIGICASH identity alias exists but has no usable "
                                + "certificate/public key. Recreating identity."
                );

                keyStore.deleteEntry(
                        SecurityConstants.KEY_ALIAS_RSA_IDENTITY);
            }

        } catch (KeyStoreException e) {

            Log.e(
                    LOG_TAG,
                    "Failed while checking DIGICASH identity in Android Keystore",
                    e
            );

            throw new CryptoOperationException(
                    "Failed to check DIGICASH identity in Android Keystore",
                    e
            );

        } catch (ProviderException e) {

            /*
             * Do not delete the key on a provider-level failure.
             * The Keystore itself may temporarily be unavailable.
             */
            Log.e(
                    LOG_TAG,
                    "Android Keystore provider failed while checking identity",
                    e
            );

            throw new CryptoOperationException(
                    "Android Keystore provider failed while checking identity",
                    e
            );
        }

        /*
         * Generate the identity. If the physical device's Keystore provider
         * rejects the first attempt, clean up the DIGICASH alias and retry
         * exactly once.
         */
        try {

            attemptGenerateKeyPair();

            verifyGeneratedIdentity();

        } catch (ProviderException firstAttemptFailure) {

            Log.e(
                    LOG_TAG,
                    "RSA key generation failed on first attempt. "
                            + "Cleaning up DIGICASH identity and retrying.",
                    firstAttemptFailure
            );

            deleteIdentityAliasSafely(keyStore);

            try {

                attemptGenerateKeyPair();

                verifyGeneratedIdentity();

            } catch (ProviderException secondAttemptFailure) {

                Log.e(
                        LOG_TAG,
                        "RSA key generation failed again on retry.",
                        secondAttemptFailure
                );

                throw new CryptoOperationException(
                        "This device's secure hardware could not generate "
                                + "the required RSA key",
                        secondAttemptFailure
                );
            }
        }
    }

    /**
     * Performs the actual Android Keystore RSA key generation.
     */
    private void attemptGenerateKeyPair()
            throws CryptoOperationException {

        try {

            KeyPairGenerator keyPairGenerator =
                    KeyPairGenerator.getInstance(
                            SecurityConstants.KEY_ALGORITHM_RSA,
                            SecurityConstants.ANDROID_KEYSTORE_PROVIDER
                    );

            KeyGenParameterSpec spec =
                    new KeyGenParameterSpec.Builder(
                            SecurityConstants.KEY_ALIAS_RSA_IDENTITY,
                            KeyProperties.PURPOSE_SIGN
                                    | KeyProperties.PURPOSE_VERIFY
                    )
                            .setKeySize(
                                    SecurityConstants.RSA_KEY_SIZE_BITS
                            )
                            .setDigests(
                                    KeyProperties.DIGEST_SHA256
                            )
                            .setSignaturePaddings(
                                    KeyProperties.SIGNATURE_PADDING_RSA_PKCS1
                            )
                            .setUserAuthenticationRequired(false)
                            .build();

            keyPairGenerator.initialize(spec);
            keyPairGenerator.generateKeyPair();

        } catch (NoSuchAlgorithmException
                 | NoSuchProviderException
                 | InvalidAlgorithmParameterException e) {

            Log.e(
                    LOG_TAG,
                    "Failed to generate RSA key pair in Android Keystore",
                    e
            );

            throw new CryptoOperationException(
                    "Failed to generate RSA key pair in Android Keystore",
                    e
            );
        }
    }

    /**
     * Verifies that successful generation really produced a usable
     * public certificate.
     */
    private void verifyGeneratedIdentity()
            throws CryptoOperationException {

        try {

            KeyStore keyStore = loadKeyStore();

            Certificate certificate =
                    keyStore.getCertificate(
                            SecurityConstants.KEY_ALIAS_RSA_IDENTITY
                    );

            if (certificate == null) {

                throw new CryptoOperationException(
                        "RSA identity was generated but its public "
                                + "certificate could not be retrieved",
                        null
                );
            }

            PublicKey publicKey = certificate.getPublicKey();

            if (publicKey == null) {

                throw new CryptoOperationException(
                        "RSA identity was generated but its public "
                                + "key is unavailable",
                        null
                );
            }

            Log.d(
                    LOG_TAG,
                    "DIGICASH RSA identity generated and verified successfully"
            );

        } catch (KeyStoreException e) {

            Log.e(
                    LOG_TAG,
                    "Failed to verify generated DIGICASH identity",
                    e
            );

            throw new CryptoOperationException(
                    "Failed to verify generated DIGICASH identity",
                    e
            );

        } catch (ProviderException e) {

            Log.e(
                    LOG_TAG,
                    "Android Keystore provider failed while verifying identity",
                    e
            );

            throw new CryptoOperationException(
                    "Android Keystore provider failed while verifying identity",
                    e
            );
        }
    }

    /**
     * Safely deletes only the DIGICASH RSA identity alias.
     */
    private void deleteIdentityAliasSafely(KeyStore keyStore) {

        try {

            if (keyStore.containsAlias(
                    SecurityConstants.KEY_ALIAS_RSA_IDENTITY)) {

                keyStore.deleteEntry(
                        SecurityConstants.KEY_ALIAS_RSA_IDENTITY
                );
            }

        } catch (KeyStoreException e) {

            Log.e(
                    LOG_TAG,
                    "Failed to delete DIGICASH identity alias",
                    e
            );
        }
    }

    public boolean isKeyPairPresent()
            throws CryptoOperationException {

        try {

            KeyStore keyStore = loadKeyStore();

            if (!keyStore.containsAlias(
                    SecurityConstants.KEY_ALIAS_RSA_IDENTITY)) {

                return false;
            }

            Certificate certificate =
                    keyStore.getCertificate(
                            SecurityConstants.KEY_ALIAS_RSA_IDENTITY
                    );

            return certificate != null
                    && certificate.getPublicKey() != null;

        } catch (KeyStoreException e) {

            Log.e(
                    LOG_TAG,
                    "Failed to check DIGICASH identity",
                    e
            );

            throw new CryptoOperationException(
                    "Failed to check DIGICASH identity",
                    e
            );

        } catch (ProviderException e) {

            Log.e(
                    LOG_TAG,
                    "Android Keystore provider failed while checking identity",
                    e
            );

            throw new CryptoOperationException(
                    "Android Keystore provider failed while checking identity",
                    e
            );
        }
    }

    /**
     * Retrieves this device's RSA public key from Android Keystore.
     */
    public PublicKey getPublicKey()
            throws CryptoOperationException {

        try {

            KeyStore keyStore = loadKeyStore();

            Certificate certificate =
                    keyStore.getCertificate(
                            SecurityConstants.KEY_ALIAS_RSA_IDENTITY
                    );

            if (certificate == null) {

                throw new CryptoOperationException(
                        "No public key found for alias '"
                                + SecurityConstants.KEY_ALIAS_RSA_IDENTITY
                                + "'. Call generateKeyPairIfNotExists() first.",
                        null
                );
            }

            PublicKey publicKey = certificate.getPublicKey();

            if (publicKey == null) {

                throw new CryptoOperationException(
                        "Public certificate exists but contains no public key",
                        null
                );
            }

            return publicKey;

        } catch (KeyStoreException e) {

            Log.e(
                    LOG_TAG,
                    "Failed to retrieve public key from Android Keystore",
                    e
            );

            throw new CryptoOperationException(
                    "Failed to retrieve public key from Android Keystore",
                    e
            );

        } catch (ProviderException e) {

            Log.e(
                    LOG_TAG,
                    "Android Keystore provider failed while retrieving public key",
                    e
            );

            throw new CryptoOperationException(
                    "Android Keystore provider failed while retrieving public key",
                    e
            );
        }
    }

    /**
     * Builds public key information including Base64 public key and
     * SHA-256 fingerprint.
     */
    public PublicKeyInfo getPublicKeyInfo()
            throws CryptoOperationException {

        PublicKey publicKey = getPublicKey();

        String base64PublicKey =
                encodeToBase64(publicKey.getEncoded());

        String fingerprint =
                generateFingerprint(publicKey);

        return new PublicKeyInfo(
                publicKey.getAlgorithm(),
                SecurityConstants.RSA_KEY_SIZE_BITS,
                base64PublicKey,
                fingerprint
        );
    }

    /**
     * Reconstructs an RSA public key from Base64 X.509 encoded data.
     */
    public PublicKey decodePublicKeyFromBase64(
            String base64PublicKey)
            throws CryptoOperationException {

        if (base64PublicKey == null
                || base64PublicKey.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "base64PublicKey must not be null or empty"
            );
        }

        try {

            byte[] keyBytes =
                    decodeFromBase64(base64PublicKey);

            X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(keyBytes);

            KeyFactory keyFactory =
                    KeyFactory.getInstance(
                            SecurityConstants.KEY_ALGORITHM_RSA
                    );

            return keyFactory.generatePublic(keySpec);

        } catch (IllegalArgumentException e) {

            throw new CryptoOperationException(
                    "Invalid Base64 encoding for public key",
                    e
            );

        } catch (InvalidKeySpecException
                 | NoSuchAlgorithmException e) {

            throw new CryptoOperationException(
                    "Failed to reconstruct public key from Base64 data",
                    e
            );
        }
    }

    /**
     * Generates SHA-256 fingerprint of the public key.
     */
    public String generateFingerprint(
            PublicKey publicKey)
            throws CryptoOperationException {

        if (publicKey == null) {
            throw new IllegalArgumentException(
                    "publicKey must not be null"
            );
        }

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            SecurityConstants.DIGEST_ALGORITHM_SHA256
                    );

            byte[] hash =
                    digest.digest(publicKey.getEncoded());

            return toColonSeparatedHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new CryptoOperationException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    /**
     * Signs transaction data using the Keystore-resident RSA private key.
     */
    public String sign(byte[] data)
            throws CryptoOperationException {

        if (data == null || data.length == 0) {

            throw new IllegalArgumentException(
                    "data to sign must not be null or empty"
            );
        }

        try {

            PrivateKey privateKey = getPrivateKey();

            Signature signature =
                    Signature.getInstance(
                            SecurityConstants.SIGNATURE_ALGORITHM
                    );

            signature.initSign(privateKey);
            signature.update(data);

            byte[] signedBytes =
                    signature.sign();

            return encodeToBase64(signedBytes);

        } catch (NoSuchAlgorithmException
                 | InvalidKeyException
                 | SignatureException e) {

            throw new CryptoOperationException(
                    "Failed to sign data using SHA256withRSA",
                    e
            );
        }
    }

    /**
     * Verifies SHA256withRSA signature.
     */
    public boolean verify(
            byte[] data,
            String base64Signature,
            PublicKey publicKey)
            throws CryptoOperationException {

        if (data == null || data.length == 0) {

            throw new IllegalArgumentException(
                    "data to verify must not be null or empty"
            );
        }

        if (base64Signature == null
                || base64Signature.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "base64Signature must not be null or empty"
            );
        }

        if (publicKey == null) {

            throw new IllegalArgumentException(
                    "publicKey must not be null"
            );
        }

        byte[] signatureBytes;

        try {

            signatureBytes =
                    decodeFromBase64(base64Signature);

        } catch (IllegalArgumentException malformedBase64) {

            return false;
        }

        try {

            Signature signature =
                    Signature.getInstance(
                            SecurityConstants.SIGNATURE_ALGORITHM
                    );

            signature.initVerify(publicKey);
            signature.update(data);

            return signature.verify(signatureBytes);

        } catch (SignatureException malformedSignatureBytes) {

            return false;

        } catch (NoSuchAlgorithmException
                 | InvalidKeyException e) {

            throw new CryptoOperationException(
                    "Failed to verify signature using SHA256withRSA",
                    e
            );
        }
    }

    /**
     * Generates a cryptographically secure nonce.
     */
    public String generateSecureNonce() {

        byte[] nonceBytes =
                new byte[SecurityConstants.NONCE_LENGTH_BYTES];

        SECURE_RANDOM.nextBytes(nonceBytes);

        return encodeToBase64(nonceBytes);
    }

    /**
     * Generates a unique transaction ID.
     */
    public String generateTransactionId() {

        long timestampMillis =
                System.currentTimeMillis();

        byte[] randomBytes =
                new byte[
                        SecurityConstants.TRANSACTION_ID_RANDOM_BYTES
                        ];

        SECURE_RANDOM.nextBytes(randomBytes);

        String randomPart =
                Base64.encodeToString(
                        randomBytes,
                        Base64.URL_SAFE
                                | Base64.NO_WRAP
                                | Base64.NO_PADDING
                );

        return SecurityConstants.TRANSACTION_ID_PREFIX
                + "-"
                + timestampMillis
                + "-"
                + randomPart;
    }

    /**
     * Encodes raw bytes to Base64.
     */
    public String encodeToBase64(byte[] rawBytes) {

        if (rawBytes == null) {

            throw new IllegalArgumentException(
                    "rawBytes must not be null"
            );
        }

        return Base64.encodeToString(
                rawBytes,
                Base64.NO_WRAP
        );
    }

    /**
     * Decodes Base64 data.
     */
    public byte[] decodeFromBase64(
            String base64Value) {

        if (base64Value == null) {

            throw new IllegalArgumentException(
                    "base64Value must not be null"
            );
        }

        return Base64.decode(
                base64Value,
                Base64.NO_WRAP
        );
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private PrivateKey getPrivateKey()
            throws CryptoOperationException {

        try {

            KeyStore keyStore = loadKeyStore();

            KeyStore.Entry entry =
                    keyStore.getEntry(
                            SecurityConstants.KEY_ALIAS_RSA_IDENTITY,
                            null
                    );

            if (!(entry instanceof KeyStore.PrivateKeyEntry)) {

                throw new CryptoOperationException(
                        "No RSA private key entry found for alias '"
                                + SecurityConstants.KEY_ALIAS_RSA_IDENTITY
                                + "'. Call generateKeyPairIfNotExists() first.",
                        null
                );
            }

            return ((KeyStore.PrivateKeyEntry) entry)
                    .getPrivateKey();

        } catch (KeyStoreException
                 | NoSuchAlgorithmException
                 | UnrecoverableEntryException e) {

            throw new CryptoOperationException(
                    "Failed to retrieve private key from Android Keystore",
                    e
            );
        }
    }

    private KeyStore loadKeyStore()
            throws CryptoOperationException {

        try {

            KeyStore keyStore =
                    KeyStore.getInstance(
                            SecurityConstants.ANDROID_KEYSTORE_PROVIDER
                    );

            keyStore.load(null);

            return keyStore;

        } catch (KeyStoreException
                 | NoSuchAlgorithmException
                 | CertificateException
                 | IOException e) {

            Log.e(
                    LOG_TAG,
                    "Failed to load AndroidKeyStore",
                    e
            );

            throw new CryptoOperationException(
                    "Failed to load AndroidKeyStore",
                    e
            );

        } catch (ProviderException e) {

            Log.e(
                    LOG_TAG,
                    "Android Keystore provider failed while loading",
                    e
            );

            throw new CryptoOperationException(
                    "Android Keystore provider failed while loading",
                    e
            );
        }
    }

    private String toColonSeparatedHex(
            byte[] bytes) {

        StringBuilder builder =
                new StringBuilder(bytes.length * 3);

        for (int i = 0; i < bytes.length; i++) {

            builder.append(
                    String.format(
                            Locale.US,
                            "%02X",
                            bytes[i]
                    )
            );

            if (i != bytes.length - 1) {

                builder.append(
                        SecurityConstants.FINGERPRINT_BYTE_SEPARATOR
                );
            }
        }

        return builder.toString();
    }

    /**
     * Checked exception wrapping recoverable Android Keystore/JCA failures.
     */
    public static final class CryptoOperationException
            extends Exception {

        public CryptoOperationException(
                String message,
                Throwable cause) {

            super(message, cause);
        }
    }
}