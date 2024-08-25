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

    /**
     * Simple profit statement.
     *
     * TODO: more accurate math
     */
    fun calculateUnrealizedProfit(transactions: List<NormalizedTransaction>, asset: String, assetValue: ExchangeAmount = ExchangeAmount(64400.0, "USD")): ProfitStatement {
        val filteredTransactions = transactions
                .filter { transaction -> transaction.assetAmount.unit == asset }

        val buyTransactions = filteredTransactions
            .filter { transaction -> transaction.type == NormalizedTransactionType.BUY }

//        val sellTransactions = transactions
//            .filter { transaction -> transaction.type == NormalizedTransactionType.SELL }

        val costBasis = buyTransactions.map { it.transactionAmountUSD }.reduce { acc, i -> acc + i  }
        val units = buyTransactions.map { it.assetAmount }.reduce { acc, i -> acc + i  }

        val currentValue = assetValue * units
        val profit = currentValue - costBasis
        val profitPercentage = (profit.amount / costBasis.amount) * 100.0

        return ProfitStatement(
            units = units,
            costBasis = costBasis,
            currentValue = ExchangeAmount(units.amount * assetValue.amount, assetValue.unit),
            profit = profit,
            profitPercentage = profitPercentage
        )
    }
}
