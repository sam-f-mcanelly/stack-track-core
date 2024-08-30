package com.bitcointracker.core.mapper

import com.bitcointracker.model.transaction.coinbase.CoinbaseFillsSide
import com.bitcointracker.model.transaction.coinbase.CoinbaseFillsTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.transaction.normalized.TransactionSource
import javax.inject.Inject

class CoinbaseProFillsNormalizingMapper @Inject constructor() : NormalizingMapper<CoinbaseFillsTransaction> {
    override fun normalizeTransaction(transaction: CoinbaseFillsTransaction): NormalizedTransaction {
        val type = when (transaction.side) {
            CoinbaseFillsSide.BUY -> NormalizedTransactionType.BUY
            CoinbaseFillsSide.SELL -> NormalizedTransactionType.SELL
        }

        return NormalizedTransaction(
            id = transaction.tradeId,
            type = type,
            source = TransactionSource.COINBASE_PRO_FILL,
            transactionAmountFiat = transaction.total.absoluteValue,
            fee = transaction.fee.absoluteValue,
            assetAmount = transaction.size.absoluteValue,
            assetValueFiat = transaction.price.absoluteValue,
            timestamp = transaction.createdAt,
        )
    }
}