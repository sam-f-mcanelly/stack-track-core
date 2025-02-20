package com.bitcointracker.core.mapper

import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseStandardTransaction
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseTransactionType
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.TransactionSource
import javax.inject.Inject

/**
 * A mapper that normalizes Coinbase standard transactions.
 *
 * This class implements the [NormalizingMapper] interface for [CoinbaseStandardTransaction]
 * objects and transforms them into [NormalizedTransaction] objects. It maps the specific
 * Coinbase transaction types to generalized normalized transaction types.
 *
 * @constructor Creates an instance of [CoinbaseStandardTransactionNormalizingMapper].
 * @inject This class is intended to be used with dependency injection.
 */
class CoinbaseStandardTransactionNormalizingMapper @Inject constructor() : NormalizingMapper<CoinbaseStandardTransaction> {

    /**
     * Normalizes a given [CoinbaseStandardTransaction] to a [NormalizedTransaction].
     *
     * This method transforms the transaction type from Coinbase-specific values to standardized
     * values as defined in [NormalizedTransactionType]. It also sets other transaction details,
     * such as fiat amounts, fees, asset quantities, and timestamps.
     *
     * @param transaction The [CoinbaseStandardTransaction] to be normalized.
     * @return A [NormalizedTransaction] representing the normalized version of the input transaction.
     */
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
            filedWithIRS = false,
        )
    }
}