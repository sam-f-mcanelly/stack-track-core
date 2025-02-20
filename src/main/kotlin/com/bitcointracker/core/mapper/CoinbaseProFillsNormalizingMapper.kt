package com.bitcointracker.core.mapper

import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseFillsSide
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseFillsTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.TransactionSource
import javax.inject.Inject

/**
 * A mapper class that normalizes Coinbase Pro fill transactions into a standardized format.
 * This mapper converts Coinbase-specific transaction data into a normalized format that can be
 * used consistently across the application, regardless of the source exchange.
 *
 * Key features:
 * - Converts Coinbase Pro buy/sell sides to normalized transaction types
 * - Preserves all monetary amounts while ensuring positive values
 * - Maintains transaction metadata including timestamps and IRS filing status
 * - Handles both spot and market orders from Coinbase Pro
 *
 * Implementation of [NormalizingMapper] specifically for [CoinbaseFillsTransaction] objects.
 *
 * @see NormalizingMapper
 * @see CoinbaseFillsTransaction
 * @see NormalizedTransaction
 */
class CoinbaseProFillsNormalizingMapper @Inject constructor() : NormalizingMapper<CoinbaseFillsTransaction> {

    /**
     * Converts a Coinbase Pro fills transaction into a normalized transaction format.
     *
     * The normalization process includes:
     * - Converting the transaction side (BUY/SELL) to normalized transaction types
     * - Converting amounts to absolute values to ensure consistency
     * - Preserving the original trade ID for tracking
     * - Adding IRS filing status based on the transaction ID
     *
     * @param transaction The Coinbase Pro fills transaction to normalize
     * @return A normalized transaction containing standardized transaction data
     *
     * @see CoinbaseFillsTransaction
     * @see NormalizedTransaction
     * @see CoinbaseFillsSide
     * @see NormalizedTransactionType
     * @see TransactionSource
     */
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
            timestampText = transaction.createdAt.toString(),
            filedWithIRS = false,
        )
    }
}