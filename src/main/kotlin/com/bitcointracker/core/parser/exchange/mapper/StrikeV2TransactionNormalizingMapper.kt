package com.bitcointracker.core.parser.exchange.mapper

import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.strike.StrikeV2Transaction
import com.bitcointracker.model.internal.transaction.strike.StrikeV2TransactionType
import org.slf4j.LoggerFactory
import jakarta.inject.Inject
import kotlin.math.absoluteValue

/**
 * Mapper for normalizing Strike V2 transactions into a standardized format.
 */
class StrikeV2TransactionNormalizingMapper @Inject constructor() : NormalizingMapper<StrikeV2Transaction> {

    companion object {
        private val logger = LoggerFactory.getLogger(StrikeV2TransactionNormalizingMapper::class.java)

        private const val USD_CURRENCY = "USD"
        private const val BTC_CURRENCY = "BTC"
        private const val DEFAULT_VALUE = 0.0
        private const val USD_TO_USD_RATE = 1.0
    }

    /**
     * Normalizes a list of Strike V2 transactions.
     *
     * @param transactions List of Strike V2 transactions to normalize
     * @return List of normalized transactions
     */
    override fun normalizeTransactions(transactions: List<StrikeV2Transaction>): List<NormalizedTransaction> =
        transactions.map { normalizeTransaction(it) }

    /**
     * Converts a Strike V2 transaction into a normalized format.
     *
     * @param transaction The transaction to normalize
     * @return A normalized transaction with standardized fields
     */
    override fun normalizeTransaction(transaction: StrikeV2Transaction): NormalizedTransaction {
        return when (transaction.type) {
            StrikeV2TransactionType.DEPOSIT -> normalizeDeposit(transaction)
            StrikeV2TransactionType.PURCHASE -> normalizePurchase(transaction)
            StrikeV2TransactionType.WITHDRAWAL -> normalizeWithdrawal(transaction)
            StrikeV2TransactionType.SELL -> normalizeSell(transaction)
            StrikeV2TransactionType.SEND -> normalizeSend(transaction)
            else -> normalizeUnknown(transaction)
        }
    }

    /**
     * Normalizes a deposit transaction.
     */
    private fun normalizeDeposit(transaction: StrikeV2Transaction): NormalizedTransaction =
        NormalizedTransaction(
            id = transaction.reference,
            source = TransactionSource.STRIKE_MONTHLY_V2,
            type = NormalizedTransactionType.DEPOSIT,
            transactionAmountFiat = ExchangeAmount(transaction.amountUsd ?: DEFAULT_VALUE, USD_CURRENCY),
            fee = ExchangeAmount(transaction.feeUsd ?: DEFAULT_VALUE, USD_CURRENCY),
            assetAmount = ExchangeAmount(transaction.amountUsd ?: DEFAULT_VALUE, USD_CURRENCY),
            assetValueFiat = ExchangeAmount(USD_TO_USD_RATE, USD_CURRENCY),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )

    /**
     * Normalizes a purchase transaction.
     */
    private fun normalizePurchase(transaction: StrikeV2Transaction): NormalizedTransaction =
        NormalizedTransaction(
            id = transaction.reference,
            source = TransactionSource.STRIKE_MONTHLY_V2,
            type = NormalizedTransactionType.BUY,
            // Fees are included in the transaction amount so they need to be subtracted here
            transactionAmountFiat = ExchangeAmount(
                (transaction.amountUsd ?: DEFAULT_VALUE).absoluteValue - (transaction.feeUsd ?: DEFAULT_VALUE).absoluteValue,
                USD_CURRENCY
            ),
            fee = ExchangeAmount(transaction.feeUsd ?: DEFAULT_VALUE, USD_CURRENCY),
            assetAmount = ExchangeAmount(transaction.amountBtc ?: DEFAULT_VALUE, BTC_CURRENCY),
            assetValueFiat = ExchangeAmount(transaction.btcPrice ?: DEFAULT_VALUE, USD_CURRENCY),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )

    /**
     * Normalizes a withdrawal transaction.
     */
    private fun normalizeWithdrawal(transaction: StrikeV2Transaction): NormalizedTransaction =
        NormalizedTransaction(
            id = transaction.reference,
            source = TransactionSource.STRIKE_MONTHLY_V2,
            type = NormalizedTransactionType.WITHDRAWAL,
            transactionAmountFiat = ExchangeAmount(
                (transaction.amountUsd ?: DEFAULT_VALUE).absoluteValue,
                USD_CURRENCY
            ),
            fee = ExchangeAmount(transaction.feeUsd ?: DEFAULT_VALUE, USD_CURRENCY),
            assetAmount = ExchangeAmount(
                (transaction.amountUsd ?: DEFAULT_VALUE).absoluteValue,
                USD_CURRENCY
            ),
            assetValueFiat = ExchangeAmount(USD_TO_USD_RATE, USD_CURRENCY),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )

    /**
     * Normalizes a Send which is a withdrawal of bitcoin.
     *
     * The negative -1.0 indicates no data. It will be replaced later
     */
    private fun normalizeSend(transaction: StrikeV2Transaction): NormalizedTransaction =
        NormalizedTransaction(
            id = transaction.reference,
            source = TransactionSource.STRIKE_MONTHLY_V2,
            type = NormalizedTransactionType.WITHDRAWAL,
            transactionAmountFiat = ExchangeAmount(-1.0, USD_CURRENCY),
            fee = ExchangeAmount(transaction.feeUsd ?: DEFAULT_VALUE, USD_CURRENCY),
            assetAmount = ExchangeAmount(transaction.amountBtc!!.absoluteValue, BTC_CURRENCY),
            assetValueFiat = ExchangeAmount(-1.0, USD_CURRENCY),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )

    /**
     * Normalizes a sale transaction.
     */
    private fun normalizeSell(transaction: StrikeV2Transaction): NormalizedTransaction =
        NormalizedTransaction(
            id = transaction.reference,
            source = TransactionSource.STRIKE_MONTHLY_V2,
            type = NormalizedTransactionType.SELL,
            transactionAmountFiat = ExchangeAmount(
                (transaction.amountUsd ?: DEFAULT_VALUE).absoluteValue,
                USD_CURRENCY
            ),
            fee = ExchangeAmount(transaction.feeUsd ?: DEFAULT_VALUE, USD_CURRENCY),
            assetAmount = ExchangeAmount(
                (transaction.amountBtc ?: DEFAULT_VALUE).absoluteValue,
                BTC_CURRENCY
            ),
            assetValueFiat = ExchangeAmount(transaction.btcPrice ?: DEFAULT_VALUE, USD_CURRENCY),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )

    /**
     * Normalizes an unknown transaction type.
     */
    private fun normalizeUnknown(transaction: StrikeV2Transaction): NormalizedTransaction = throw IllegalArgumentException("Unknown transaction: $transaction")
}