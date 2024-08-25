package com.bitcointracker.core

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType

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

    fun calculateAssetPurchased(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        return transactions.filter { it.type == NormalizedTransactionType.BUY }
            .filter { it.assetAmount.unit == asset }
                .map { it.assetAmount }
                .reduce { acc, i -> acc + i  }
    }

    fun calculateUSDSpentOnAsset(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        return transactions.filter { it.type == NormalizedTransactionType.BUY }
                .filter { it.assetAmount.unit == asset }
                .map { it.transactionAmountUSD }
                .reduce { acc, i -> acc + i  }
    }

    fun calculateWithdrawals(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        return transactions.filter { it.type == NormalizedTransactionType.WITHDRAWAL }
                .filter { it.assetAmount.unit == asset }
                .map { it.transactionAmountUSD }
                .reduce { acc, i -> acc + i  }
    }
}