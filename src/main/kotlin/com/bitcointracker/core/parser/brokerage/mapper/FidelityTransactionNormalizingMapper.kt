package com.bitcointracker.core.parser.brokerage.mapper

import com.bitcointracker.core.parser.NormalizingMapper
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.fidelity.FidelityTransaction
import com.bitcointracker.model.internal.transaction.fidelity.FidelityTransactionType
import org.slf4j.LoggerFactory
import jakarta.inject.Inject
import kotlin.math.absoluteValue

/**
 * Mapper for normalizing Fidelity transactions into a standardized format.
 */
class FidelityTransactionNormalizingMapper @Inject constructor() : NormalizingMapper<FidelityTransaction> {

    companion object {
        private val logger = LoggerFactory.getLogger(FidelityTransactionNormalizingMapper::class.java)

        private const val USD_CURRENCY = "USD"
        private const val BTC_CURRENCY = "BTC"
        private const val DEFAULT_VALUE = 0.0

        // Bitcoin ETF symbols mapped to their Bitcoin exposure per 1000 shares
        // Values represent the decimal equivalent of BTC per 1000 shares
        // https://newhedge.io/bitcoin/bitcoin-etf-tracker
        private val BITCOIN_ETF_CONFIG = mapOf(
            "FBTC" to 0.8744,
            "BITB" to 0.5446,
            "IBIT" to 0.5705,
            "ARKB" to 0.9994,
            "HODL" to 1.1312,
        )
    }

    /**
     * Normalizes a list of Fidelity transactions.
     *
     * @param transactions List of Fidelity transactions to normalize
     * @return List of normalized transactions
     */
    override fun normalizeTransactions(transactions: List<FidelityTransaction>): List<NormalizedTransaction> =
        transactions.map { normalizeTransaction(it) }
            .filter { it.assetAmount.unit == "BTC" }

    /**
     * Converts a Fidelity transaction into a normalized format.
     *
     * @param transaction The transaction to normalize
     * @return A normalized transaction with standardized fields
     */
    override fun normalizeTransaction(transaction: FidelityTransaction): NormalizedTransaction {
        val transactionType = FidelityTransactionType.fromAction(transaction.action)

        return when (transactionType) {
            FidelityTransactionType.BUY -> normalizeBuy(transaction)
            FidelityTransactionType.SELL -> normalizeSell(transaction)
            else -> normalizeUnknown(transaction)
        }
    }

    /**
     * Normalizes a buy transaction.
     */
    private fun normalizeBuy(transaction: FidelityTransaction): NormalizedTransaction {
        val btcConfig = transaction.symbol?.let { BITCOIN_ETF_CONFIG[it] }

        val assetAmount = if (btcConfig != null) {
            // Calculate actual BTC amount: (shares * btc_per_1000_shares) / 1000
            val btcAmount = ((transaction.quantity ?: DEFAULT_VALUE) * btcConfig) / 1000.0
            ExchangeAmount(btcAmount, BTC_CURRENCY)
        } else {
            ExchangeAmount(transaction.quantity ?: DEFAULT_VALUE, transaction.symbol ?: "UNKNOWN")
        }

        return NormalizedTransaction(
            id = generateTransactionId(transaction),
            source = TransactionSource.FIDELITY,
            type = NormalizedTransactionType.BUY,
            transactionAmountFiat = ExchangeAmount(
                transaction.amount.absoluteValue,
                USD_CURRENCY
            ),
            fee = ExchangeAmount(
                (transaction.commission ?: DEFAULT_VALUE) + (transaction.fees ?: DEFAULT_VALUE),
                USD_CURRENCY
            ),
            assetAmount = assetAmount,
            assetValueFiat = ExchangeAmount(-1.0, USD_CURRENCY), // Will be replaced with btc price on that date
            timestamp = transaction.settlementDate,
            timestampText = transaction.settlementDate.toString(),
            notes = buildNotes(transaction),
            filedWithIRS = false,
        )
    }

    /**
     * Normalizes a sell transaction.
     */
    private fun normalizeSell(transaction: FidelityTransaction): NormalizedTransaction {
        val btcConfig = transaction.symbol?.let { BITCOIN_ETF_CONFIG[it] }

        val assetAmount = if (btcConfig != null) {
            // Calculate actual BTC amount: (shares * btc_per_1000_shares) / 1000
            val btcAmount = ((transaction.quantity?.absoluteValue ?: DEFAULT_VALUE) * btcConfig) / 1000.0
            ExchangeAmount(btcAmount, BTC_CURRENCY)
        } else {
            ExchangeAmount(transaction.quantity?.absoluteValue ?: DEFAULT_VALUE, transaction.symbol ?: "UNKNOWN")
        }

        return NormalizedTransaction(
            id = generateTransactionId(transaction),
            source = TransactionSource.FIDELITY,
            type = NormalizedTransactionType.SELL,
            transactionAmountFiat = ExchangeAmount(
                transaction.amount.absoluteValue,
                USD_CURRENCY
            ),
            fee = ExchangeAmount(
                (transaction.commission ?: DEFAULT_VALUE) + (transaction.fees ?: DEFAULT_VALUE),
                USD_CURRENCY
            ),
            assetAmount = assetAmount,
            assetValueFiat = ExchangeAmount(-1.0, USD_CURRENCY), // Will be replaced with btc price on that date
            timestamp = transaction.settlementDate,
            timestampText = transaction.settlementDate.toString(),
            notes = buildNotes(transaction),
            filedWithIRS = false,
        )
    }


    /**
     * Normalizes an unknown transaction type.
     */
    private fun normalizeUnknown(transaction: FidelityTransaction): NormalizedTransaction {
        logger.warn("Unknown transaction type for action: ${transaction.action}")
        throw IllegalArgumentException("Unknown transaction: $transaction")
    }

    /**
     * Generates a unique transaction ID from the Fidelity transaction data using a deterministic hash.
     */
    private fun generateTransactionId(transaction: FidelityTransaction): String {
        val seed = "${transaction.accountNumber}_${transaction.runDate.toEpochMilli()}_${transaction.symbol}_${transaction.amount}"
        return seed.hashCode().toString(16).removePrefix("-")
    }

    /**
     * Builds notes string from transaction details.
     */
    private fun buildNotes(transaction: FidelityTransaction): String {
        val notes = mutableListOf<String>()
        notes.add("Account: ${transaction.account}")
        transaction.symbol?.let { notes.add("Symbol: $it") }
        notes.add("Description: ${transaction.description}")
        return notes.joinToString(" | ")
    }
}