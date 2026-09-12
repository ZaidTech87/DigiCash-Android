package com.digicash.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Wire representation of a single PENDING transaction sent to the
 * backend for authoritative validation and double-spend reconciliation.
 * Contains ONLY public/verifiable data already present in the local
 * ledger and already exchanged via the Payment QR - no private key
 * material exists anywhere in this app outside the Android Keystore, so
 * there is nothing of that kind to accidentally include here.
 */
public class TransactionSyncRequest {

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

    @SerializedName("senderPublicKeyId")
    private String senderPublicKeyId;

    @SerializedName("signature")
    private String signature;

    @SerializedName("transactionType")
    private String transactionType;

    public TransactionSyncRequest(
            String transactionId,
            String senderId,
            String receiverId,
            long amountMinorUnits,
            long timestamp,
            long expiryTimestamp,
            String nonce,
            String senderPublicKeyId,
            String signature,
            String transactionType) {
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amountMinorUnits = amountMinorUnits;
        this.timestamp = timestamp;
        this.expiryTimestamp = expiryTimestamp;
        this.nonce = nonce;
        this.senderPublicKeyId = senderPublicKeyId;
        this.signature = signature;
        this.transactionType = transactionType;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public String getNonce() {
        return nonce;
    }

    public String getSenderPublicKeyId() {
        return senderPublicKeyId;
    }

    public String getSignature() {
        return signature;
    }

    public String getTransactionType() {
        return transactionType;
    }
}
