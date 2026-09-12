package com.digicash.app.data.local.entity;

/**
 * Server-synchronization state of a locally recorded transaction.
 * Every transaction starts as PENDING at the moment it is recorded
 * offline, and is only ever transitioned by WorkManager sync logic
 * after a server round-trip - never by UI code directly.
 */
public enum SyncStatus {
    PENDING,
    SYNCED,
    REJECTED
}
