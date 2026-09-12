package com.digicash.app.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.digicash.app.utils.Constants;

import java.util.concurrent.TimeUnit;

/**
 * Single entry point for scheduling DIGICASH server synchronization work.
 * Both the manual "Sync with Server" button and any automatic trigger go
 * through this class rather than constructing WorkRequests directly, so
 * retry/backoff/constraint policy stays consistent everywhere.
 */
public final class SyncManager {

    private SyncManager() {
        // Static accessor only.
    }

    /**
     * Enqueues a single immediate sync attempt, constrained to run only
     * while the device has network connectivity (NetworkType.CONNECTED).
     * If a manual sync is already pending/running, this REPLACEs it
     * rather than stacking a second one, since both would attempt to
     * sync the same PENDING transaction set.
     */
    public static void triggerImmediateSync(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(NetworkSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(Constants.WORK_NAME_IMMEDIATE_SYNC, ExistingWorkPolicy.REPLACE, request);
    }

    /**
     * Schedules recurring background synchronization that runs
     * automatically whenever the device has network connectivity,
     * approximately every Constants.PERIODIC_SYNC_INTERVAL_MINUTES
     * minutes (WorkManager's own platform-enforced minimum for periodic
     * work is 15 minutes). Safe to call multiple times (e.g. on every app
     * launch) - ExistingPeriodicWorkPolicy.KEEP means an already-scheduled
     * periodic sync is left untouched rather than being duplicated or reset.
     */
    public static void enableAutomaticSync(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                        NetworkSyncWorker.class,
                        Constants.PERIODIC_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,
                        PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        Constants.WORK_NAME_PERIODIC_SYNC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /**
     * Cancels any scheduled automatic background sync. Provided for
     * completeness (e.g. a future settings screen toggle) - not called
     * from anywhere in the current UI.
     */
    public static void disableAutomaticSync(@NonNull Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(Constants.WORK_NAME_PERIODIC_SYNC);
    }
}
