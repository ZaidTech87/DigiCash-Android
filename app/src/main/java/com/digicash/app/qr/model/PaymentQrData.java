package com.digicash.app.qr.model;

import com.google.gson.annotations.SerializedName;

/**
 * Wire-format data embedded in a DIGICASH Payment QR code.
 *
 * This is the actual scanned/serialized transport model - distinct from
 * TransactionEntity (the persisted ledger row). After a receiver verifies
 * an instance of this class, its fields are copied into a new
 * TransactionEntity for storage; this class itself is never persisted via
 * Room.
 *
 * senderPublicKeyId carries the sender's full Base64-encoded (X.509)
 * public key, not merely a short identifier - this is required so a
 * receiver can verify the signature entirely offline from this QR alone,
 * without a prior identity exchange with this specific sender.
 */
public class PaymentQrData {

    /** Fixed discriminator so arbitrary/foreign QR content is never mistaken for a payment. */
    public static final String TYPE_DISCRIMINATOR = "DIGICASH_PAYMENT_V1";

    @SerializedName("type")
    private String type = TYPE_DISCRIMINATOR;

    @SerializedName("protocolVersion")
    private int protocolVersion;

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

    public PaymentQrData(
            int protocolVersion,
            String transactionId,
            String senderId,
            String receiverId,
            long amountMinorUnits,
            long timestamp,
            long expiryTimestamp,
            String nonce,
            String senderPublicKeyId,
            String signature) {
        this.type = TYPE_DISCRIMINATOR;
        this.protocolVersion = protocolVersion;
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amountMinorUnits = amountMinorUnits;
        this.timestamp = timestamp;
        this.expiryTimestamp = expiryTimestamp;
        this.nonce = nonce;
        this.senderPublicKeyId = senderPublicKeyId;
        this.signature = signature;
    }

    public String getType() {
        return type;
    }

    public int getProtocolVersion() {
        return protocolVersion;
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

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
