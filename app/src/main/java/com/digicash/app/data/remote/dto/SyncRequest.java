package com.digicash.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Top-level request body for POST /api/sync. Wraps the calling device's
 * user identifier (public key fingerprint - never any private material)
 * and the batch of locally PENDING transactions to be reconciled.
 */
public class SyncRequest {

    @SerializedName("deviceUserId")
    private String deviceUserId;

    @SerializedName("transactions")
    private List<TransactionSyncRequest> transactions;

    public SyncRequest(String deviceUserId, List<TransactionSyncRequest> transactions) {
        this.deviceUserId = deviceUserId;
        this.transactions = transactions;
    }

    public String getDeviceUserId() {
        return deviceUserId;
    }

    public List<TransactionSyncRequest> getTransactions() {
        return transactions;
    }
}
