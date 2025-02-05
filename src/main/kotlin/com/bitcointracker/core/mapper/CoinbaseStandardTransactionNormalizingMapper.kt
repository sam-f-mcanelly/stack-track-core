package com.bitcointracker.core.mapper

import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseStandardTransaction
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseTransactionType
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.TransactionSource
import javax.inject.Inject

class CoinbaseStandardTransactionNormalizingMapper @Inject constructor() : NormalizingMapper<CoinbaseStandardTransaction> {
    override fun normalizeTransaction(transaction: CoinbaseStandardTransaction): NormalizedTransaction {
        val type = when (transaction.type) {
            CoinbaseTransactionType.DEPOSIT -> NormalizedTransactionType.DEPOSIT
            CoinbaseTransactionType.PRO_WITHDRAWAL -> NormalizedTransactionType.WITHDRAWAL
            CoinbaseTransactionType.RECEIVE -> NormalizedTransactionType.DEPOSIT
            CoinbaseTransactionType.SEND -> NormalizedTransactionType.WITHDRAWAL
            CoinbaseTransactionType.SELL -> NormalizedTransactionType.SELL
            CoinbaseTransactionType.BUY -> NormalizedTransactionType.BUY
        }

        return NormalizedTransaction(
            id = transaction.id,
            type = type,
            source = TransactionSource.COINBASE_STANDARD,
            transactionAmountFiat = transaction.transactionAmount.absoluteValue,
            fee = transaction.fee.absoluteValue,
            assetAmount = transaction.quantityTransacted.absoluteValue,
            assetValueFiat = transaction.assetValue,
            timestamp = transaction.timestamp,
            timestampText = transaction.timestamp.toString(),
            notes = transaction.notes,
            filedWithIRS = FiledTransactions.filteredTransactionIds.contains(transaction.id)
        )
    }
}