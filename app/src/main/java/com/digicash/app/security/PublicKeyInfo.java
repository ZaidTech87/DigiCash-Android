package com.digicash.app.security;

/**
 * Immutable, serialization-friendly representation of this device's RSA
 * public key. Contains ONLY public information - never references or
 * carries private key material.
 */
public final class PublicKeyInfo {

    private final String algorithm;
    private final int keySizeBits;
    private final String base64PublicKey;
    private final String sha256Fingerprint;

    public PublicKeyInfo(String algorithm, int keySizeBits, String base64PublicKey, String sha256Fingerprint) {
        if (algorithm == null || algorithm.trim().isEmpty()) {
            throw new IllegalArgumentException("algorithm must not be null or empty");
        }
        if (base64PublicKey == null || base64PublicKey.trim().isEmpty()) {
            throw new IllegalArgumentException("base64PublicKey must not be null or empty");
        }
        if (sha256Fingerprint == null || sha256Fingerprint.trim().isEmpty()) {
            throw new IllegalArgumentException("sha256Fingerprint must not be null or empty");
        }
        if (keySizeBits <= 0) {
            throw new IllegalArgumentException("keySizeBits must be positive");
        }

        this.algorithm = algorithm;
        this.keySizeBits = keySizeBits;
        this.base64PublicKey = base64PublicKey;
        this.sha256Fingerprint = sha256Fingerprint;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getKeySizeBits() {
        return keySizeBits;
    }

    public String getBase64PublicKey() {
        return base64PublicKey;
    }

    public String getSha256Fingerprint() {
        return sha256Fingerprint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicKeyInfo)) {
            return false;
        }
        PublicKeyInfo that = (PublicKeyInfo) other;
        return keySizeBits == that.keySizeBits
                && algorithm.equals(that.algorithm)
                && base64PublicKey.equals(that.base64PublicKey)
                && sha256Fingerprint.equals(that.sha256Fingerprint);
    }

    @Override
    public int hashCode() {
        int result = algorithm.hashCode();
        result = 31 * result + keySizeBits;
        result = 31 * result + base64PublicKey.hashCode();
        result = 31 * result + sha256Fingerprint.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PublicKeyInfo{"
                + "algorithm='" + algorithm + '\''
                + ", keySizeBits=" + keySizeBits
                + ", sha256Fingerprint='" + sha256Fingerprint + '\''
                + '}';
    }
}
