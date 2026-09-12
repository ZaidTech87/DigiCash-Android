package com.digicash.app.security;

/**
 * Centralized cryptographic and Android Keystore constants for DIGICASH.
 */
public final class SecurityConstants {

    private SecurityConstants() {
        // Prevent instantiation - constants holder only.
    }

    /** Provider name for the hardware/software-backed Android Keystore. */
    public static final String ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore";

    /**
     * Alias under which the app's single RSA identity key pair is stored
     * inside the Android Keystore. The private key associated with this
     * alias never leaves the Keystore in raw form.
     */
    public static final String KEY_ALIAS_RSA_IDENTITY = "digicash_identity_rsa_key";

    /** Asymmetric key algorithm used for the DIGICASH identity key pair. */
    public static final String KEY_ALGORITHM_RSA = "RSA";

    /** RSA key size in bits. */
    public static final int RSA_KEY_SIZE_BITS = 2048;

    /** Signature algorithm used to sign and verify all DIGICASH transaction payloads. */
    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /** Digest algorithm used for public key fingerprints. */
    public static final String DIGEST_ALGORITHM_SHA256 = "SHA-256";

    /** Length, in bytes, of a cryptographically secure nonce before Base64 encoding. */
    public static final int NONCE_LENGTH_BYTES = 16;

    /** Length, in bytes, of the random component of a generated transaction ID. */
    public static final int TRANSACTION_ID_RANDOM_BYTES = 12;

    /** Human-readable prefix applied to every generated transaction ID. */
    public static final String TRANSACTION_ID_PREFIX = "TXN";

    /** Separator used when rendering a SHA-256 fingerprint as colon-delimited hex. */
    public static final String FINGERPRINT_BYTE_SEPARATOR = ":";
}
