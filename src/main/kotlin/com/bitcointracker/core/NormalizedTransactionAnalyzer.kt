package com.bitcointracker.core

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction

class NormalizedTransactionAnalyzer {
    companion object {
        // TODO
    }

    fun calculateProfit(transactions: List<NormalizedTransaction>): ExchangeAmount {
        return ExchangeAmount(
            amount = 1.0,
            unit = "USD"
        )
    }
}