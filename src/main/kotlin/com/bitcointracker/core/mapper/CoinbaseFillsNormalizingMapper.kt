package com.bitcointracker.core.mapper

import com.bitcointracker.model.transaction.coinbase.CoinbaseFillsSide
import com.bitcointracker.model.transaction.coinbase.CoinbaseFillsTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.transaction.normalized.TransactionSource

class CoinbaseFillsNormalizingMapper() : NormalizingMapper<CoinbaseFillsTransaction> {
    override fun normalizeTransaction(transaction: CoinbaseFillsTransaction): NormalizedTransaction {
        // println("Normalizing transaction " + transaction.tradeId)

        val type = when (transaction.side) {
            CoinbaseFillsSide.BUY -> NormalizedTransactionType.BUY
            CoinbaseFillsSide.SELL -> NormalizedTransactionType.SELL
        }

        return NormalizedTransaction(
            id = transaction.tradeId,
            type = type,
            source = TransactionSource.COINBASE_FILL,
            transactionAmountUSD = transaction.total.absoluteValue,
            fee = transaction.fee.absoluteValue,
            assetAmount = transaction.size.absoluteValue,
            assetValueUSD = transaction.price.absoluteValue,
            timestamp = transaction.createdAt,
        )
    }
}