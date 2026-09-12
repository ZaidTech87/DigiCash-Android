package com.digicash.app.utils;

import java.util.concurrent.TimeUnit;

/**
 * App-wide constants for networking and background work identifiers.
 * Kept separate from SecurityConstants (which is crypto-only) to avoid
 * mixing unrelated concerns in one file.
 */
public final class Constants {

    private Constants() {
        // Constants holder only.
    }

    /**
     * Base URL of the DIGICASH backend sync API. This is a placeholder -
     * the Spring Boot backend itself is out of scope for the Android
     * project. Replace with the real deployed backend URL when it exists.
     * Must end with a trailing slash for Retrofit's baseUrl() contract.
     */
    public static final String API_BASE_URL = "http://10.162.4.19:8080/";

    public static final long HTTP_CONNECT_TIMEOUT_SECONDS = 15L;
    public static final long HTTP_READ_TIMEOUT_SECONDS = 20L;
    public static final long HTTP_WRITE_TIMEOUT_SECONDS = 20L;
    public static final TimeUnit HTTP_TIMEOUT_UNIT = TimeUnit.SECONDS;

    /** Unique WorkManager work name for a manually triggered, immediate sync. */
    public static final String WORK_NAME_IMMEDIATE_SYNC = "digicash_immediate_sync";

    /** Unique WorkManager work name for the recurring background sync. */
    public static final String WORK_NAME_PERIODIC_SYNC = "digicash_periodic_sync";

    /** Minimum allowed PeriodicWorkRequest interval enforced by WorkManager itself. */
    public static final long PERIODIC_SYNC_INTERVAL_MINUTES = 15L;

    /** Number of NetworkSyncWorker attempts (including the first) before giving up for this enqueue. */
    public static final int MAX_SYNC_RUN_ATTEMPTS = 5;
}
