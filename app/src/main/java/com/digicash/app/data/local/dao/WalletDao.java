package com.digicash.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.digicash.app.data.local.entity.WalletEntity;

/**
 * Data access for the local wallet balance table.
 *
 * The UPDATE methods here are intentionally atomic single-statement SQL
 * operations (not read-modify-write in application code) so that
 * concurrent access cannot produce a lost update. debitBalanceIfSufficient
 * additionally guards against a negative balance at the SQL WHERE-clause
 * level rather than via a separate application-level check-then-act,
 * which would be race-prone.
 */
@Dao
public interface WalletDao {

    @Query("SELECT * FROM wallet_table WHERE userId = :userId LIMIT 1")
    WalletEntity getWalletByUserId(String userId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertWalletIfNotExists(WalletEntity wallet);

    /**
     * Atomically increases a wallet's balance. Returns the number of rows
     * updated (1 if the wallet existed, 0 if it did not).
     */
    @Query("UPDATE wallet_table SET balanceMinorUnits = balanceMinorUnits + :amountMinorUnits WHERE userId = :userId")
    int creditBalance(String userId, long amountMinorUnits);

    /**
     * Atomically decreases a wallet's balance ONLY if sufficient funds
     * exist. Returns the number of rows updated: 1 on success, 0 if the
     * wallet does not exist or the balance was insufficient. This is the
     * sole mechanism by which a NEW outgoing payment may debit a wallet -
     * there is no variant of this method that skips the balance check.
     */
    @Query("UPDATE wallet_table SET balanceMinorUnits = balanceMinorUnits - :amountMinorUnits " +
            "WHERE userId = :userId AND balanceMinorUnits >= :amountMinorUnits")
    int debitBalanceIfSufficient(String userId, long amountMinorUnits);

    /**
     * Atomically reverses a previously applied credit by decreasing the
     * balance UNCONDITIONALLY (no sufficiency check). This method exists
     * for exactly one purpose: server-confirmed reconciliation, when a
     * transaction this device previously RECEIVED (and already credited
     * to its own wallet) is later REJECTED by the backend's authoritative
     * double-spend/balance check. In that case the earlier credit must be
     * undone regardless of the wallet's current balance.
     *
     * This method must NEVER be called from any ordinary spend/payment
     * path - only from AppDatabase.applyServerSyncResult().
     */
    @Query("UPDATE wallet_table SET balanceMinorUnits = balanceMinorUnits - :amountMinorUnits WHERE userId = :userId")
    int reverseCreditBalance(String userId, long amountMinorUnits);
}
