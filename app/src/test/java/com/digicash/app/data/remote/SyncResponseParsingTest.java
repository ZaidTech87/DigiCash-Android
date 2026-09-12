package com.digicash.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.digicash.app.data.remote.dto.SyncResponse;
import com.digicash.app.data.remote.dto.TransactionSyncResult;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.List;

/**
 * Pure JVM test - Gson has no Android dependency, so this needs no
 * emulator/device and runs as a standard ./gradlew test target.
 */
public class SyncResponseParsingTest {

    private final Gson gson = new Gson();

    @Test
    public void parsesConfirmedAndRejectedResultsCorrectly() {
        String json = "{"
                + "\"serverTimestamp\":1735689600000,"
                + "\"results\":["
                + "{\"transactionId\":\"TXN-1\",\"status\":\"CONFIRMED\",\"reason\":null},"
                + "{\"transactionId\":\"TXN-2\",\"status\":\"REJECTED\",\"reason\":\"DOUBLE_SPEND\"}"
                + "]}";

        SyncResponse response = gson.fromJson(json, SyncResponse.class);

        assertEquals(1735689600000L, response.getServerTimestamp());
        List<TransactionSyncResult> results = response.getResults();
        assertEquals(2, results.size());

        assertEquals("TXN-1", results.get(0).getTransactionId());
        assertEquals(TransactionSyncResult.STATUS_CONFIRMED, results.get(0).getStatus());

        assertEquals("TXN-2", results.get(1).getTransactionId());
        assertEquals(TransactionSyncResult.STATUS_REJECTED, results.get(1).getStatus());
        assertEquals("DOUBLE_SPEND", results.get(1).getReason());
    }

    @Test
    public void missingResultsField_parsesToNullNotCrash() {
        String json = "{\"serverTimestamp\":1735689600000}";
        SyncResponse response = gson.fromJson(json, SyncResponse.class);
        assertNull(response.getResults());
        // NetworkSyncWorker.doWork() explicitly treats a null/empty results
        // list as "retry later" rather than crashing on a null list.
    }

    @Test
    public void unrecognizedStatusValue_isPreservedAsRawString() {
        String json = "{\"serverTimestamp\":1,\"results\":["
                + "{\"transactionId\":\"TXN-3\",\"status\":\"SOME_FUTURE_STATUS\"}]}";
        SyncResponse response = gson.fromJson(json, SyncResponse.class);
        assertEquals("SOME_FUTURE_STATUS", response.getResults().get(0).getStatus());
        // NetworkSyncWorker.reconcileSingleResult() leaves such a transaction
        // PENDING rather than guessing a mapping.
    }
}
