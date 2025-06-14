package com.bitcointracker.model.internal.transaction.strike

/**
 * Types of transactions available in Strike V2 format
 */
enum class StrikeV2TransactionType {
    DEPOSIT,
    PURCHASE,
    WITHDRAWAL,
    SELL,
    SEND,
    UNKNOWN;

    companion object {
        /**
         * Converts a string value to the corresponding transaction type
         *
         * @param value String value to convert
         * @return The matching transaction type or UNKNOWN if no match found
         */
        fun fromString(value: String): StrikeV2TransactionType =
            values().find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
