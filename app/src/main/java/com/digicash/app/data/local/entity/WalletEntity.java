package com.digicash.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Local wallet balance for a single user identity on this device.
 * Balance is stored strictly as a long representing minor currency units
 * (e.g. paise for INR: Rs. 10.50 = 1050L) - never float or double, to
 * avoid floating-point rounding errors in financial arithmetic.
 */
@Entity(tableName = "wallet_table")
public class WalletEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "userId")
    private String userId;

    @ColumnInfo(name = "balanceMinorUnits")
    private long balanceMinorUnits;

    public WalletEntity(@NonNull String userId, long balanceMinorUnits) {
        this.userId = userId;
        this.balanceMinorUnits = balanceMinorUnits;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    public long getBalanceMinorUnits() {
        return balanceMinorUnits;
    }

    public void setBalanceMinorUnits(long balanceMinorUnits) {
        this.balanceMinorUnits = balanceMinorUnits;
    }
}
