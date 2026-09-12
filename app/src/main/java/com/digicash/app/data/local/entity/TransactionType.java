package com.digicash.app.data.local.entity;

/**
 * Direction of a transaction relative to the local device's wallet.
 * SENT means this device was the paying sender; RECEIVED means this
 * device was the receiving party.
 */
public enum TransactionType {
    SENT,
    RECEIVED
}
