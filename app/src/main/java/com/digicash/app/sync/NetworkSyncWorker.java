package com.digicash.app.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.digicash.app.data.local.AppDatabase;
import com.digicash.app.data.local.entity.SyncStatus;
import com.digicash.app.data.local.entity.TransactionEntity;
import com.digicash.app.data.remote.ApiService;
import com.digicash.app.data.remote.RetrofitClient;
import com.digicash.app.data.remote.dto.SyncRequest;
import com.digicash.app.data.remote.dto.SyncResponse;
import com.digicash.app.data.remote.dto.TransactionSyncRequest;
import com.digicash.app.data.remote.dto.TransactionSyncResult;
import com.digicash.app.security.CryptoManager;
import com.digicash.app.security.PublicKeyInfo;
import com.digicash.app.utils.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

public class NetworkSyncWorker extends Worker {

    private static final String TAG = "DigiCashSync";

    public NetworkSyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {

        Log.d(TAG, "========== SYNC STARTED ==========");
        Log.d(TAG, "Attempt: " + (getRunAttemptCount() + 1));
        Log.d(TAG, "API URL: " + Constants.API_BASE_URL);

        AppDatabase appDatabase =
                AppDatabase.getInstance(getApplicationContext());

        CryptoManager cryptoManager = new CryptoManager();

        List<TransactionEntity> pendingTransactions;
        String deviceUserId;

        try {

            cryptoManager.generateKeyPairIfNotExists();

            PublicKeyInfo publicKeyInfo =
                    cryptoManager.getPublicKeyInfo();

            deviceUserId =
                    publicKeyInfo.getSha256Fingerprint();

            Log.d(TAG, "Device/User ID: " + deviceUserId);

            pendingTransactions =
                    appDatabase.transactionDao().getPendingTransactions();

            Log.d(TAG,
                    "Pending transactions: "
                            + pendingTransactions.size());

        } catch (CryptoManager.CryptoOperationException e) {

            Log.e(TAG,
                    "CRYPTO/KEYSTORE ERROR",
                    e);

            return retryOrFail();
        }

        if (pendingTransactions.isEmpty()) {

            Log.d(TAG, "No pending transactions.");
            Log.d(TAG, "========== SYNC FINISHED ==========");

            return Result.success();
        }

        SyncRequest request =
                buildSyncRequest(
                        deviceUserId,
                        pendingTransactions);

        ApiService apiService =
                RetrofitClient.getApiService();

        Response<SyncResponse> response;

        try {

            Log.d(TAG,
                    "Sending sync request to: "
                            + Constants.API_BASE_URL);

            Call<SyncResponse> call =
                    apiService.syncTransactions(request);

            response = call.execute();

            Log.d(TAG,
                    "HTTP response received. Code: "
                            + response.code());

        } catch (IOException networkFailure) {

            Log.e(TAG,
                    "========== NETWORK ERROR ==========");

            Log.e(TAG,
                    "Exception type: "
                            + networkFailure.getClass().getName());

            Log.e(TAG,
                    "Exception message: "
                            + networkFailure.getMessage());

            Log.e(TAG,
                    "Full network exception:",
                    networkFailure);

            Log.e(TAG,
                    "====================================");

            return retryOrFail();
        }

        if (!response.isSuccessful()) {

            Log.e(TAG,
                    "========== HTTP ERROR ==========");

            Log.e(TAG,
                    "HTTP code: " + response.code());

            Log.e(TAG,
                    "HTTP message: " + response.message());

            try {

                if (response.errorBody() != null) {

                    Log.e(TAG,
                            "Error body: "
                                    + response.errorBody().string());
                }

            } catch (IOException e) {

                Log.e(TAG,
                        "Could not read error body",
                        e);
            }

            Log.e(TAG,
                    "================================");

            return retryOrFail();
        }

        if (response.body() == null) {

            Log.e(TAG,
                    "HTTP request successful but response body is NULL.");

            return retryOrFail();
        }

        SyncResponse syncResponse =
                response.body();

        List<TransactionSyncResult> results =
                syncResponse.getResults();

        if (results == null || results.isEmpty()) {

            Log.e(TAG,
                    "Server returned EMPTY results.");

            return retryOrFail();
        }

        Log.d(TAG,
                "Server returned "
                        + results.size()
                        + " transaction result(s).");

        for (TransactionSyncResult result : results) {

            if (result == null) {
                Log.e(TAG, "Received NULL transaction result.");
                continue;
            }

            Log.d(TAG,
                    "Transaction ID: "
                            + result.getTransactionId()
                            + " | Status: "
                            + result.getStatus());

            reconcileSingleResult(
                    appDatabase,
                    result);
        }

        Log.d(TAG, "========== SYNC SUCCESS ==========");

        return Result.success();
    }

    private SyncRequest buildSyncRequest(
            String deviceUserId,
            List<TransactionEntity> pendingTransactions) {

        List<TransactionSyncRequest> dtoList =
                new ArrayList<>(
                        pendingTransactions.size());

        for (TransactionEntity entity :
                pendingTransactions) {

            dtoList.add(
                    new TransactionSyncRequest(
                            entity.getTransactionId(),
                            entity.getSenderId(),
                            entity.getReceiverId(),
                            entity.getAmountMinorUnits(),
                            entity.getTimestamp(),
                            entity.getExpiryTimestamp(),
                            entity.getNonce(),
                            entity.getSenderPublicKeyId(),
                            entity.getSignature(),
                            entity.getTransactionType().name()));
        }

        Log.d(TAG,
                "Sync request built with "
                        + dtoList.size()
                        + " transaction(s).");

        return new SyncRequest(
                deviceUserId,
                dtoList);
    }

    private void reconcileSingleResult(
            AppDatabase appDatabase,
            TransactionSyncResult result) {

        if (result == null
                || result.getTransactionId() == null
                || result.getStatus() == null) {

            Log.e(TAG,
                    "Invalid transaction result received.");

            return;
        }

        SyncStatus mappedStatus;

        if (TransactionSyncResult.STATUS_CONFIRMED
                .equals(result.getStatus())) {

            mappedStatus = SyncStatus.SYNCED;

        } else if (TransactionSyncResult.STATUS_REJECTED
                .equals(result.getStatus())) {

            mappedStatus = SyncStatus.REJECTED;

        } else {

            Log.e(TAG,
                    "Unknown server status: "
                            + result.getStatus());

            return;
        }

        appDatabase.applyServerSyncResult(
                result.getTransactionId(),
                mappedStatus);

        Log.d(TAG,
                "Local transaction updated: "
                        + result.getTransactionId()
                        + " -> "
                        + mappedStatus);
    }

    private Result retryOrFail() {

        int attempt =
                getRunAttemptCount() + 1;

        if (attempt >= Constants.MAX_SYNC_RUN_ATTEMPTS) {

            Log.e(TAG,
                    "Maximum sync attempts reached: "
                            + attempt);

            return Result.failure();
        }

        Log.d(TAG,
                "Scheduling retry. Attempt: "
                        + attempt);

        return Result.retry();
    }
}