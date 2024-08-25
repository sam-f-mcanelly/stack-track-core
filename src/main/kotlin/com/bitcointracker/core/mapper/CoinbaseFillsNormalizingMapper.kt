package com.bitcointracker.core.mapper

import com.bitcointracker.model.transaction.coinbase.CoinbaseFillsSide
import com.bitcointracker.model.transaction.coinbase.CoinbaseFillsTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType

class CoinbaseFillsNormalizingMapper() : NormalizingMapper<CoinbaseFillsTransaction> {
    override fun normalizeTransaction(transaction: CoinbaseFillsTransaction): NormalizedTransaction {
        println("Normalizing transaction " + transaction.tradeId)

        val type = when (transaction.side) {
            CoinbaseFillsSide.BUY -> NormalizedTransactionType.BUY
            CoinbaseFillsSide.SELL -> NormalizedTransactionType.SELL
        }

        val transaction = NormalizedTransaction(
            id = transaction.tradeId,
            type = type,
            transactionAmountUSD = transaction.total * -1.0,
            fee = transaction.fee,
            assetAmount = transaction.size,
            assetValueUSD = (transaction.total * -1.0) - transaction.fee,
            timestamp = transaction.createdAt,
        )

        println("Normalized Transaction: " + transaction)
        return transaction
    }
}