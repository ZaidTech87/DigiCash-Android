package com.digicash.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Local ledger record of a single DIGICASH transaction, either sent or
 * received by this device. Amounts are stored strictly as long minor
 * currency units. This entity is written and read only inside atomic
 * Room transactions defined in AppDatabase - it must never be inserted
 * or updated independently of the corresponding WalletEntity balance
 * change.
 *
 * Indices:
 *  - Unique index on transactionId: prevents the same transaction being
 *    recorded twice.
 *  - Index on syncStatus: supports fast lookup of PENDING transactions
 *    for WorkManager sync without a full table scan.
 *  - Unique composite index on (senderPublicKeyId, nonce): defense-in-depth
 *    replay protection at the database level.
 */
@Entity(
        tableName = "transaction_table",
        indices = {
                @Index(value = "transactionId", unique = true),
                @Index(value = "syncStatus"),
                @Index(value = {"senderPublicKeyId", "nonce"}, unique = true)
        }
)
public class TransactionEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "transactionId")
    private String transactionId;

    @NonNull
    @ColumnInfo(name = "senderId")
    private String senderId;

    @NonNull
    @ColumnInfo(name = "receiverId")
    private String receiverId;

    @ColumnInfo(name = "amountMinorUnits")
    private long amountMinorUnits;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "expiryTimestamp")
    private long expiryTimestamp;

    @NonNull
    @ColumnInfo(name = "nonce")
    private String nonce;

    @NonNull
    @ColumnInfo(name = "senderPublicKeyId")
    private String senderPublicKeyId;

    @NonNull
    @ColumnInfo(name = "signature")
    private String signature;

    @NonNull
    @ColumnInfo(name = "syncStatus")
    private SyncStatus syncStatus;

    @NonNull
    @ColumnInfo(name = "transactionType")
    private TransactionType transactionType;

    public TransactionEntity(
            @NonNull String transactionId,
            @NonNull String senderId,
            @NonNull String receiverId,
            long amountMinorUnits,
            long timestamp,
            long expiryTimestamp,
            @NonNull String nonce,
            @NonNull String senderPublicKeyId,
            @NonNull String signature,
            @NonNull SyncStatus syncStatus,
            @NonNull TransactionType transactionType) {
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amountMinorUnits = amountMinorUnits;
        this.timestamp = timestamp;
        this.expiryTimestamp = expiryTimestamp;
        this.nonce = nonce;
        this.senderPublicKeyId = senderPublicKeyId;
        this.signature = signature;
        this.syncStatus = syncStatus;
        this.transactionType = transactionType;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(@NonNull String transactionId) {
        this.transactionId = transactionId;
    }

    @NonNull
    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(@NonNull String senderId) {
        this.senderId = senderId;
    }

    @NonNull
    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(@NonNull String receiverId) {
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

    @NonNull
    public String getNonce() {
        return nonce;
    }

    public void setNonce(@NonNull String nonce) {
        this.nonce = nonce;
    }

    @NonNull
    public String getSenderPublicKeyId() {
        return senderPublicKeyId;
    }

    public void setSenderPublicKeyId(@NonNull String senderPublicKeyId) {
        this.senderPublicKeyId = senderPublicKeyId;
    }

    @NonNull
    public String getSignature() {
        return signature;
    }

    public void setSignature(@NonNull String signature) {
        this.signature = signature;
    }

    @NonNull
    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(@NonNull SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    @NonNull
    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(@NonNull TransactionType transactionType) {
        this.transactionType = transactionType;
    }
}
