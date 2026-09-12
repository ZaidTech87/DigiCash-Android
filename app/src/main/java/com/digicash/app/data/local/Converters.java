package com.digicash.app.data.local;

import androidx.room.TypeConverter;

import com.digicash.app.data.local.entity.SyncStatus;
import com.digicash.app.data.local.entity.TransactionType;

/**
 * Room TypeConverters for the enum columns used by TransactionEntity.
 * Enums are stored as their String name() in SQLite for readability
 * during manual DB inspection/debugging.
 */
public class Converters {

    @TypeConverter
    public static String fromSyncStatus(SyncStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static SyncStatus toSyncStatus(String value) {
        return value == null ? null : SyncStatus.valueOf(value);
    }

    @TypeConverter
    public static String fromTransactionType(TransactionType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static TransactionType toTransactionType(String value) {
        return value == null ? null : TransactionType.valueOf(value);
    }
}
