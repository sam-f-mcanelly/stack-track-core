
package com.bitcointracker.model.internal.transaction.fidelity

/**
 * Represents the different types of Fidelity transactions.
 */
enum class FidelityTransactionType {
    BUY,
    SELL,
    UNKNOWN;

    companion object {
        /**
         * Converts a Fidelity action string to a transaction type.
         */
        fun fromAction(action: String): FidelityTransactionType {
            return when {
                action.contains("YOU BOUGHT", ignoreCase = true) -> BUY
                action.contains("YOU SOLD", ignoreCase = true) -> SELL
                else -> UNKNOWN
            }
        }
    }
}