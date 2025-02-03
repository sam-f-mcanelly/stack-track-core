package com.bitcointracker.model.internal.transaction.strike

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.util.Date

data class StrikeTransaction(
    val transactionId: String,
    val date: Date, // TODO replace with better library?
    val type: StrikeTransactionType,
    val source: StrikeTransactionSource,
    val state: StrikeTransactionState,
    val fee: ExchangeAmount? = null,
    val asset1: ExchangeAmount? = null,
    val asset2: ExchangeAmount? = null,
    val assetValue: ExchangeAmount? = null,
    val balance: ExchangeAmount? = null,
    val balanceBtc: ExchangeAmount? = null,
    val destination: String? = null,
    val description: String? = "",
)