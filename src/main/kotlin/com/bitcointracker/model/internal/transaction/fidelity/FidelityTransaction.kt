package com.bitcointracker.model.internal.transaction.fidelity

import java.time.Instant

/**
 * Represents a transaction from Fidelity account statements.
 */
data class FidelityTransaction(
    val runDate: Instant,
    val account: String,
    val accountNumber: String,
    val action: String,
    val symbol: String?,
    val description: String,
    val type: String,
    val quantity: Double?,
    val price: Double?,
    val commission: Double?,
    val fees: Double?,
    val accruedInterest: Double?,
    val amount: Double,
    val settlementDate: Instant
)