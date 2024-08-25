package com.bitcointracker.model.transaction.strike

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import java.util.Date

data class StrikeTransaction(
    val transactionId: String,
    val date: Date, // TODO replace with better library?
    val type: StrikeTransactionType,
    val state: StrikeTransactionState,
    val fee: ExchangeAmount? = null,
    val assetOut: ExchangeAmount? = null,
    val assetIn: ExchangeAmount? = null,
    val assetValue: ExchangeAmount? = null,
    val balance: ExchangeAmount? = null,
    val balanceBtc: ExchangeAmount? = null,
    val destination: String? = null,
    val description: String? = "",
)