package com.digicash.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Per-transaction outcome returned by the backend after authoritative
 * validation (balance/double-spend check server-side). "status" is a
 * plain wire-format string (not the local SyncStatus enum directly) so
 * this DTO has zero coupling to Room's persistence types; NetworkSyncWorker
 * maps it to SyncStatus explicitly and defensively.
 */
public class TransactionSyncResult {

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    @SerializedName("transactionId")
    private String transactionId;

    @SerializedName("status")
    private String status;

    @SerializedName("reason")
    private String reason;

    public String getTransactionId() {
        return transactionId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
