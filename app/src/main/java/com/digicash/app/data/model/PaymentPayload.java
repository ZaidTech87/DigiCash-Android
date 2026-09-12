package com.digicash.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * LEGACY / CURRENTLY UNUSED.
 *
 * Early data model from initial planning, superseded by
 * com.digicash.app.qr.model.PaymentQrData, which is the model actually
 * used by QRUtils, GenerateQrDialog, and ScannerActivity throughout the
 * app. Retained (not deleted) per explicit consolidation instructions,
 * since it is inert: it is never imported or referenced by any active
 * class, uses its own package, and its class name does not collide with
 * PaymentQrData. Safe to delete manually for a cleaner submission, but
 * leaving it in place does not affect compilation or behavior.
 */
public class PaymentPayload {

    @SerializedName("transactionId")
    private String transactionId;

    @SerializedName("senderId")
    private String senderId;

    @SerializedName("receiverId")
    private String receiverId;

    @SerializedName("amountMinorUnits")
    private long amountMinorUnits;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("expiryTimestamp")
    private long expiryTimestamp;

    @SerializedName("nonce")
    private String nonce;

    @SerializedName("senderPublicKeyBase64")
    private String senderPublicKeyBase64;

    @SerializedName("senderPublicKeyFingerprint")
    private String senderPublicKeyFingerprint;

    @SerializedName("signature")
    private String signature;

    public PaymentPayload(
            String transactionId,
            String senderId,
            String receiverId,
            long amountMinorUnits,
            long timestamp,
            long expiryTimestamp,
            String nonce,
            String senderPublicKeyBase64,
            String senderPublicKeyFingerprint,
            String signature) {
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amountMinorUnits = amountMinorUnits;
        this.timestamp = timestamp;
        this.expiryTimestamp = expiryTimestamp;
        this.nonce = nonce;
        this.senderPublicKeyBase64 = senderPublicKeyBase64;
        this.senderPublicKeyFingerprint = senderPublicKeyFingerprint;
        this.signature = signature;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public void setAmountMinorUnits(long amountMinorUnits) {
        this.amountMinorUnits = amountMinorUnits;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public void setExpiryTimestamp(long expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getSenderPublicKeyBase64() {
        return senderPublicKeyBase64;
    }

    public void setSenderPublicKeyBase64(String senderPublicKeyBase64) {
        this.senderPublicKeyBase64 = senderPublicKeyBase64;
    }

    public String getSenderPublicKeyFingerprint() {
        return senderPublicKeyFingerprint;
    }

    public void setSenderPublicKeyFingerprint(String senderPublicKeyFingerprint) {
        this.senderPublicKeyFingerprint = senderPublicKeyFingerprint;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
