package com.bitcointracker.core

import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType

class NormalizedTransactionAnalyzer {
    fun calculateAssetPurchased(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        val purchased = transactions.filter { it.type == NormalizedTransactionType.BUY }
            .filter { it.assetAmount.unit == asset }
            .map { it.assetAmount }
            .reduceOrNull { acc, i -> acc + i  }

        return purchased ?: ExchangeAmount(0.0, asset)
    }

    fun calculateUSDSpentOnAsset(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        val spent = transactions.filter { it.type == NormalizedTransactionType.BUY }
            .filter { it.assetAmount.unit == asset }
            .map { it.transactionAmountUSD }
            .reduceOrNull { acc, i -> acc + i  }

        return spent ?: ExchangeAmount(0.0, asset)
    }

    fun calculateWithdrawals(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        val withdrawals = transactions.filter { it.type == NormalizedTransactionType.WITHDRAWAL }
            .filter { it.assetAmount.unit == asset }
            .map { it.transactionAmountUSD }
            .reduceOrNull { acc, i -> acc + i  }

        return withdrawals ?: ExchangeAmount(0.0, asset)
    }

    fun calculateProfitStatement(transactions: List<NormalizedTransaction>, assetValue: ExchangeAmount = ExchangeAmount(64100.0, "USD")): ProfitStatement {
        val buyTransactions = transactions.filter { transaction -> transaction.type == NormalizedTransactionType.BUY }

        val costBasis = buyTransactions.map { it.transactionAmountUSD }.reduce { acc, i -> acc + i  }
        val units = buyTransactions.map { it.assetAmount }.reduce { acc, i -> acc + i  }

        val currentValue = assetValue * units
        val profit = currentValue - costBasis
        val profitPercentage = (profit.amount / costBasis.amount) * 100.0

        return ProfitStatement(
            units = units,
            costBasis = costBasis,
            profit = profit,
            profitPercentage = profitPercentage
        )
    }
}
