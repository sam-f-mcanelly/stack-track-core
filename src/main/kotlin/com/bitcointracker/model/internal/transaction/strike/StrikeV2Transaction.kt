package com.bitcointracker.model.internal.transaction.strike

import java.util.Date

/**
 * Model representing Strike V2 monthly statement transactions
 */
data class StrikeV2Transaction(
    val reference: String,
    val date: Date,
    val type: StrikeV2TransactionType,
    val amountUsd: Double?,
    val feeUsd: Double?,
    val amountBtc: Double?,
    val feeBtc: Double?,
    val btcPrice: Double?,
    val costBasisUsd: Double?,
    val destination: String?,
    val description: String?,
    val note: String?
)