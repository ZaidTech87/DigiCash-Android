package com.digicash.app.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.digicash.app.data.local.dao.TransactionDao;
import com.digicash.app.data.local.dao.WalletDao;
import com.digicash.app.data.local.entity.SyncStatus;
import com.digicash.app.data.local.entity.TransactionEntity;
import com.digicash.app.data.local.entity.TransactionType;
import com.digicash.app.data.local.entity.WalletEntity;

/**
 * Room database for DIGICASH's local persistence layer.
 *
 * This class also owns all atomic multi-table payment operations
 * (recordOutgoingPayment / recordIncomingPayment / applyServerSyncResult).
 * They are intentionally placed here rather than split across DAOs,
 * because Room's cross-DAO transactional guarantee is expressed via
 * RoomDatabase.runInTransaction(), which requires a reference to the
 * database instance itself. Every write path that touches both the
 * ledger and a wallet balance MUST go through one of these methods; no
 * other code in the app is permitted to call WalletDao balance updates
 * and TransactionDao writes separately.
 */
@Database(
        entities = {WalletEntity.class, TransactionEntity.class},
        version = 1,
        exportSchema = true
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "digicash_database";

    private static volatile AppDatabase instance;

    public abstract WalletDao walletDao();

    public abstract TransactionDao transactionDao();

    public static AppDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    /**
     * Atomically records a payment this device SENT: the transaction
     * ledger row is inserted and the sender's own wallet is debited in a
     * single Room transaction. If either step fails, both are rolled back
     * - the ledger and balance can never diverge.
     *
     * @throws DuplicateTransactionException if a transaction with the same
     *         transactionId already exists.
     * @throws InsufficientBalanceException if the sender's wallet does not
     *         have enough balance to cover the amount.
     */
    public void recordOutgoingPayment(@NonNull TransactionEntity transaction) {
        if (transaction.getTransactionType() != TransactionType.SENT) {
            throw new IllegalArgumentException("recordOutgoingPayment requires transactionType SENT");
        }
        runInTransaction(() -> {
            if (transactionDao().countByTransactionId(transaction.getTransactionId()) > 0) {
                throw new DuplicateTransactionException(
                        "Transaction already recorded: " + transaction.getTransactionId());
            }

            int rowsDebited = walletDao().debitBalanceIfSufficient(
                    transaction.getSenderId(), transaction.getAmountMinorUnits());
            if (rowsDebited == 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance or unknown wallet for sender: " + transaction.getSenderId());
            }

            transactionDao().insertTransaction(transaction);
        });
    }

    /**
     * Atomically records a payment this device RECEIVED: the transaction
     * ledger row is inserted and the receiver's own wallet is credited in
     * a single Room transaction. If either step fails, both are rolled
     * back - the ledger and balance can never diverge.
     *
     * A replay check on (senderPublicKeyId, nonce) is performed inside the
     * same transaction as a second line of defense beyond the database's
     * own unique index on that column pair.
     *
     * @throws DuplicateTransactionException if a transaction with the same
     *         transactionId already exists.
     * @throws ReplayDetectedException if this sender/nonce combination has
     *         already been recorded.
     */
    public void recordIncomingPayment(@NonNull TransactionEntity transaction) {
        if (transaction.getTransactionType() != TransactionType.RECEIVED) {
            throw new IllegalArgumentException("recordIncomingPayment requires transactionType RECEIVED");
        }
        runInTransaction(() -> {
            if (transactionDao().countByTransactionId(transaction.getTransactionId()) > 0) {
                throw new DuplicateTransactionException(
                        "Transaction already recorded: " + transaction.getTransactionId());
            }

            if (transactionDao().countBySenderAndNonce(
                    transaction.getSenderPublicKeyId(), transaction.getNonce()) > 0) {
                throw new ReplayDetectedException(
                        "Nonce already used by sender: " + transaction.getSenderPublicKeyId());
            }

            transactionDao().insertTransaction(transaction);

            walletDao().creditBalance(transaction.getReceiverId(), transaction.getAmountMinorUnits());
        });
    }

    /**
     * Atomically applies a server-confirmed sync result to a single local
     * transaction: updates its syncStatus and, if necessary, reverses its
     * earlier local balance effect - all inside one Room transaction so
     * status and balance can never diverge.
     *
     * Reconciliation rules:
     *  - newStatus == SYNCED: the server has confirmed this transaction
     *    exactly as recorded locally. No balance change is made, because
     *    the balance effect (debit for SENT, credit for RECEIVED) was
     *    already correctly applied at the moment the transaction was
     *    first recorded locally. Only the status moves from PENDING to
     *    SYNCED.
     *  - newStatus == REJECTED: the server's authoritative check (e.g. a
     *    double-spend it detected across devices) invalidates this
     *    transaction. Its earlier local balance effect must be reversed:
     *    for a SENT transaction, the amount is credited back to the local
     *    sender's wallet; for a RECEIVED transaction, the amount is
     *    debited back out of the local receiver's wallet via the
     *    unconditional reverseCreditBalance.
     *
     * Idempotency: this method is a no-op if the local transaction is not
     * currently PENDING (already SYNCED or already REJECTED), so calling
     * it more than once for the same transactionId can never double-apply
     * a balance reversal or a status change.
     *
     * @return true if a PENDING local transaction was found and updated;
     *         false if no matching PENDING transaction exists locally.
     */
    public boolean applyServerSyncResult(@NonNull String transactionId, @NonNull SyncStatus newStatus) {
        if (newStatus == SyncStatus.PENDING) {
            throw new IllegalArgumentException("applyServerSyncResult cannot set status back to PENDING");
        }

        final boolean[] applied = {false};

        runInTransaction(() -> {
            TransactionEntity existing = transactionDao().findByTransactionId(transactionId);
            if (existing == null || existing.getSyncStatus() != SyncStatus.PENDING) {
                // Unknown transaction, or already reconciled by a prior sync attempt - no-op.
                return;
            }

            if (newStatus == SyncStatus.REJECTED) {
                if (existing.getTransactionType() == TransactionType.SENT) {
                    walletDao().creditBalance(existing.getSenderId(), existing.getAmountMinorUnits());
                } else {
                    walletDao().reverseCreditBalance(existing.getReceiverId(), existing.getAmountMinorUnits());
                }
            }
            // newStatus == SYNCED: no balance change, per reconciliation rules above.

            transactionDao().updateSyncStatus(transactionId, newStatus);
            applied[0] = true;
        });

        return applied[0];
    }

    /**
     * Thrown when an attempt is made to record a transactionId that
     * already exists in the ledger.
     */
    public static final class DuplicateTransactionException extends RuntimeException {
        public DuplicateTransactionException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when an outgoing payment would debit a wallet below zero.
     */
    public static final class InsufficientBalanceException extends RuntimeException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when an incoming payment reuses a (senderPublicKeyId, nonce)
     * pair already present in the ledger.
     */
    public static final class ReplayDetectedException extends RuntimeException {
        public ReplayDetectedException(String message) {
            super(message);
        }
    }
}
