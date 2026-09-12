package com.digicash.app.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.digicash.app.data.local.entity.SyncStatus;
import com.digicash.app.data.local.entity.TransactionEntity;
import com.digicash.app.data.local.entity.TransactionType;
import com.digicash.app.data.local.entity.WalletEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseInstrumentedTest {

    private AppDatabase database;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // allowMainThreadQueries() is safe ONLY inside this synchronous test
        // runner - production code never queries Room off a background
        // executor for this exact reason.
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        database.close();
    }

    private TransactionEntity buildTransaction(
            String transactionId, String senderId, String receiverId,
            long amount, String nonce, TransactionType type) {
        long now = System.currentTimeMillis();
        return new TransactionEntity(
                transactionId, senderId, receiverId, amount, now, now + 60_000L,
                nonce, "sender-pubkey-base64", "fake-signature-base64",
                SyncStatus.PENDING, type);
    }

    @Test
    public void recordOutgoingPayment_debitsBalanceAndInsertsLedgerRowAtomically() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("alice", 10_000L));

        database.recordOutgoingPayment(buildTransaction(
                "TXN-1", "alice", "bob", 2_500L, "nonce-1", TransactionType.SENT));

        assertEquals(7_500L, database.walletDao().getWalletByUserId("alice").getBalanceMinorUnits());
        TransactionEntity stored = database.transactionDao().findByTransactionId("TXN-1");
        assertNotNull(stored);
        assertEquals(SyncStatus.PENDING, stored.getSyncStatus());
    }

    @Test
    public void recordOutgoingPayment_insufficientBalance_isRejectedAndRolledBack() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("alice", 1_000L));
        try {
            database.recordOutgoingPayment(buildTransaction(
                    "TXN-2", "alice", "bob", 5_000L, "nonce-2", TransactionType.SENT));
            org.junit.Assert.fail("Expected InsufficientBalanceException");
        } catch (AppDatabase.InsufficientBalanceException expected) {
            // Verify atomic rollback: balance untouched, no ledger row inserted.
            assertEquals(1_000L, database.walletDao().getWalletByUserId("alice").getBalanceMinorUnits());
            assertEquals(0, database.transactionDao().countByTransactionId("TXN-2"));
        }
    }

    @Test
    public void recordOutgoingPayment_duplicateTransactionId_isRejected() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("alice", 10_000L));
        database.recordOutgoingPayment(buildTransaction(
                "TXN-DUP", "alice", "bob", 1_000L, "nonce-a", TransactionType.SENT));
        try {
            database.recordOutgoingPayment(buildTransaction(
                    "TXN-DUP", "alice", "bob", 1_000L, "nonce-b", TransactionType.SENT));
            org.junit.Assert.fail("Expected DuplicateTransactionException");
        } catch (AppDatabase.DuplicateTransactionException expected) {
            // Balance must reflect only the first, successful debit.
            assertEquals(9_000L, database.walletDao().getWalletByUserId("alice").getBalanceMinorUnits());
        }
    }

    @Test
    public void recordIncomingPayment_creditsBalanceAndInsertsLedgerRowAtomically() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("bob", 0L));
        database.recordIncomingPayment(buildTransaction(
                "TXN-3", "alice", "bob", 3_000L, "nonce-3", TransactionType.RECEIVED));

        assertEquals(3_000L, database.walletDao().getWalletByUserId("bob").getBalanceMinorUnits());
        assertNotNull(database.transactionDao().findByTransactionId("TXN-3"));
    }

    @Test
    public void recordIncomingPayment_reusedNonceFromSameSender_isRejected() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("bob", 0L));
        database.recordIncomingPayment(buildTransaction(
                "TXN-4", "alice", "bob", 1_000L, "shared-nonce", TransactionType.RECEIVED));
        try {
            database.recordIncomingPayment(buildTransaction(
                    "TXN-5", "alice", "bob", 1_000L, "shared-nonce", TransactionType.RECEIVED));
            org.junit.Assert.fail("Expected ReplayDetectedException");
        } catch (AppDatabase.ReplayDetectedException expected) {
            // Only the first credit should have applied.
            assertEquals(1_000L, database.walletDao().getWalletByUserId("bob").getBalanceMinorUnits());
        }
    }

    @Test
    public void getPendingTransactions_returnsOnlyPendingRows() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("alice", 10_000L));
        database.walletDao().insertWalletIfNotExists(new WalletEntity("bob", 0L));

        database.recordOutgoingPayment(buildTransaction(
                "TXN-P1", "alice", "bob", 1_000L, "nonce-p1", TransactionType.SENT));
        database.recordOutgoingPayment(buildTransaction(
                "TXN-P2", "alice", "bob", 1_000L, "nonce-p2", TransactionType.SENT));
        database.transactionDao().markTransactionSynced("TXN-P1");

        List<TransactionEntity> pending = database.transactionDao().getPendingTransactions();
        assertEquals(1, pending.size());
        assertEquals("TXN-P2", pending.get(0).getTransactionId());
    }

    @Test
    public void applyServerSyncResult_confirmed_marksSyncedWithoutChangingBalance() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("alice", 10_000L));
        database.recordOutgoingPayment(buildTransaction(
                "TXN-SYNC-OK", "alice", "bob", 1_000L, "nonce-sync-ok", TransactionType.SENT));

        assertTrue(database.applyServerSyncResult("TXN-SYNC-OK", SyncStatus.SYNCED));

        assertEquals(SyncStatus.SYNCED, database.transactionDao().findByTransactionId("TXN-SYNC-OK").getSyncStatus());
        assertEquals(9_000L, database.walletDao().getWalletByUserId("alice").getBalanceMinorUnits());
    }

    @Test
    public void applyServerSyncResult_rejectedSentTransaction_reversesDebit() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("alice", 10_000L));
        database.recordOutgoingPayment(buildTransaction(
                "TXN-SYNC-REJ", "alice", "bob", 4_000L, "nonce-sync-rej", TransactionType.SENT));
        assertEquals(6_000L, database.walletDao().getWalletByUserId("alice").getBalanceMinorUnits());

        database.applyServerSyncResult("TXN-SYNC-REJ", SyncStatus.REJECTED);

        assertEquals(10_000L, database.walletDao().getWalletByUserId("alice").getBalanceMinorUnits());
        assertEquals(SyncStatus.REJECTED, database.transactionDao().findByTransactionId("TXN-SYNC-REJ").getSyncStatus());
    }

    @Test
    public void applyServerSyncResult_calledTwice_isIdempotent() {
        database.walletDao().insertWalletIfNotExists(new WalletEntity("alice", 10_000L));
        database.recordOutgoingPayment(buildTransaction(
                "TXN-IDEMP", "alice", "bob", 1_000L, "nonce-idemp", TransactionType.SENT));

        assertTrue(database.applyServerSyncResult("TXN-IDEMP", SyncStatus.SYNCED));
        assertFalse(database.applyServerSyncResult("TXN-IDEMP", SyncStatus.SYNCED));
    }
}
