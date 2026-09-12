package com.digicash.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * LEGACY / CURRENTLY UNUSED.
 *
 * Early data model from initial planning, superseded by
 * com.digicash.app.qr.model.ReceiverIdentityQrData, which is the model
 * actually used throughout the app for QR-based identity exchange.
 * Retained (not deleted) per explicit consolidation instructions - see
 * the note in PaymentPayload.java in this same package for the full
 * rationale. Inert: never imported or referenced elsewhere.
 */
public class ReceiverIdentity {

    @SerializedName("userId")
    private String userId;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("publicKeyBase64")
    private String publicKeyBase64;

    @SerializedName("publicKeyFingerprint")
    private String publicKeyFingerprint;

    public ReceiverIdentity(
            String userId,
            String displayName,
            String publicKeyBase64,
            String publicKeyFingerprint) {
        this.userId = userId;
        this.displayName = displayName;
        this.publicKeyBase64 = publicKeyBase64;
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }
}
