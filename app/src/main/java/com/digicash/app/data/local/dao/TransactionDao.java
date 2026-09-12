package com.digicash.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.digicash.app.data.local.entity.SyncStatus;
import com.digicash.app.data.local.entity.TransactionEntity;

import java.util.List;

/**
 * Data access for the local transaction ledger.
 *
 * insertTransaction uses OnConflictStrategy.ABORT (Room's default) rather
 * than IGNORE or REPLACE: silently ignoring or overwriting a conflicting
 * transactionId/nonce row would hide a duplicate-insert bug or a replay
 * attempt. Callers are expected to pre-check countByTransactionId /
 * countBySenderAndNonce and raise a caller-visible error rather than rely
 * on this DAO swallowing the conflict.
 */
@Dao
public interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertTransaction(TransactionEntity transaction);

    @Query("SELECT * FROM transaction_table WHERE transactionId = :transactionId LIMIT 1")
    TransactionEntity findByTransactionId(String transactionId);

    @Query("SELECT COUNT(*) FROM transaction_table WHERE transactionId = :transactionId")
    int countByTransactionId(String transactionId);

    /**
     * Replay/duplicate check: counts existing transactions from the same
     * sender public key using the same nonce. A non-zero result means the
     * incoming payment reuses a nonce already recorded from that sender
     * and must be rejected before insertion.
     */
    @Query("SELECT COUNT(*) FROM transaction_table WHERE senderPublicKeyId = :senderPublicKeyId AND nonce = :nonce")
    int countBySenderAndNonce(String senderPublicKeyId, String nonce);

    @Query("SELECT * FROM transaction_table WHERE syncStatus = :status ORDER BY timestamp ASC")
    List<TransactionEntity> getTransactionsByStatus(SyncStatus status);

    @Query("SELECT * FROM transaction_table WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC")
    List<TransactionEntity> getPendingTransactions();

    @Query("SELECT * FROM transaction_table WHERE senderId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    List<TransactionEntity> getTransactionsForUser(String userId);

    @Query("UPDATE transaction_table SET syncStatus = :newStatus WHERE transactionId = :transactionId")
    int updateSyncStatus(String transactionId, SyncStatus newStatus);

    /**
     * Convenience wrapper over updateSyncStatus for the SYNCED terminal state.
     */
    default int markTransactionSynced(String transactionId) {
        return updateSyncStatus(transactionId, SyncStatus.SYNCED);
    }

    /**
     * Convenience wrapper over updateSyncStatus for the REJECTED terminal state.
     */
    default int markTransactionRejected(String transactionId) {
        return updateSyncStatus(transactionId, SyncStatus.REJECTED);
    }
}
