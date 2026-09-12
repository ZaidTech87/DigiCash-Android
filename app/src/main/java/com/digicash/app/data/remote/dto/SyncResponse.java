package com.digicash.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Top-level response body from POST /api/sync.
 */
public class SyncResponse {

    @SerializedName("serverTimestamp")
    private long serverTimestamp;

    @SerializedName("results")
    private List<TransactionSyncResult> results;

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    public List<TransactionSyncResult> getResults() {
        return results;
    }
}
