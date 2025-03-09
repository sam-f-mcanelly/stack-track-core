package com.bitcointracker.core.parser.exchange.mapper

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import com.bitcointracker.model.internal.transaction.strike.StrikeTransaction
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionSource
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionType
import org.slf4j.LoggerFactory
import javax.inject.Inject


/**
 * Mapper class responsible for normalizing Strike transactions into a standardized format.
 * This class handles various types of Strike transactions including deposits, trades,
 * withdrawals, on-chain transactions, and Strike credits.
 *
 */
class StrikeTransactionNormalizingMapper @Inject constructor () : NormalizingMapper<StrikeTransaction> {
    companion object {
        private val logger = LoggerFactory.getLogger(StrikeTransactionNormalizingMapper::class.java)
    }

    /**
     * Converts a Strike transaction into a normalized transaction format based on its type.
     *
     * @param transaction The Strike transaction to be normalized
     * @return A normalized transaction with standardized fields and formats
     */
    override fun normalizeTransaction(transaction: StrikeTransaction): NormalizedTransaction {
        return when (transaction.type) {
            StrikeTransactionType.DEPOSIT -> normalizeDeposit(transaction)
            StrikeTransactionType.TRADE -> normalizeTrade(transaction)
            StrikeTransactionType.WITHDRAWAL -> normalizeWithdrawal(transaction)
            StrikeTransactionType.ONCHAIN -> normalizeOnChainTransaction(transaction)
            StrikeTransactionType.STRIKE_CREDIT -> normalizeStrikeCredit(transaction)
            StrikeTransactionType.REFERRAL -> normalizeStrikeCredit(transaction)
        }
    }

    /**
     * Normalizes a deposit transaction.
     * Deposits are treated as free transactions with a 1:1 USD conversion rate.
     *
     * @param transaction The deposit transaction to normalize
     * @return A normalized deposit transaction
     */
    private fun normalizeDeposit(transaction: StrikeTransaction): NormalizedTransaction
        = NormalizedTransaction(
            id = transaction.transactionId,
            source = getSource(transaction),
            type = NormalizedTransactionType.DEPOSIT,
            transactionAmountFiat = transaction.asset1!!,
            fee = ExchangeAmount(0.0, "USD"), // Deposits are free
            assetAmount = transaction.asset1!!,
            assetValueFiat = ExchangeAmount(1.0, "USD"),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
    )

    /**
     * Normalizes a trade transaction, determining whether it's a buy or sell based on the assets involved.
     * If asset1 is not USD, it's treated as a sell; otherwise, it's treated as a buy.
     *
     * @param transaction The trade transaction to normalize
     * @return A normalized buy or sell transaction
     */
    private fun normalizeTrade(transaction: StrikeTransaction): NormalizedTransaction {
        var transactionType: NormalizedTransactionType
        var transactionAmountFiat: ExchangeAmount
        var assetAmount: ExchangeAmount
        if (transaction.asset1!!.amount > 0.0) {
            transactionType = NormalizedTransactionType.SELL
            transactionAmountFiat = transaction.asset1.absoluteValue
            assetAmount = transaction.asset2!!.absoluteValue
        } else {
            transactionType = NormalizedTransactionType.BUY
            transactionAmountFiat = transaction.asset1.absoluteValue
            assetAmount = transaction.asset2!!.absoluteValue
        }

        return NormalizedTransaction(
            id = transaction.transactionId,
            type = transactionType,
            source = getSource(transaction),
            transactionAmountFiat = transactionAmountFiat,
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = assetAmount,
            assetValueFiat = transaction.assetValue ?: computeAssetValue(transaction),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )
    }

    /**
     * Normalizes a withdrawal transaction.
     * Handles both current and legacy withdrawal formats, delegating to specialized handling for Bitcoin withdrawals
     * if necessary.
     *
     * @param transaction The withdrawal transaction to normalize
     * @return A normalized withdrawal transaction
     */
    private fun normalizeWithdrawal(transaction: StrikeTransaction): NormalizedTransaction {
        logger.info("Normalizing withdrawal transaction $transaction")

        // old reporting style
        if (transaction.asset1 == null) {
            return normalizeBitcoinWithdrawal(transaction)
        }

        return NormalizedTransaction(
            id = transaction.transactionId,
            type = NormalizedTransactionType.WITHDRAWAL,
            source = getSource(transaction),
            transactionAmountFiat = transaction.asset1.absoluteValue,
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = transaction.asset1.absoluteValue,
            assetValueFiat = transaction.asset1.absoluteValue,
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
            address = transaction.destination ?: "",
        )
    }

    /**
     * Handles Strike's legacy format for Bitcoin withdrawals.
     * This special handling is required for older transactions where Bitcoin withdrawals
     * were reported as regular withdrawals instead of on-chain transactions.
     *
     * @param transaction The Bitcoin withdrawal transaction to normalize
     * @return A normalized Bitcoin withdrawal transaction
     * @see normalizeOnChainTransaction
     */
    private fun normalizeBitcoinWithdrawal(transaction: StrikeTransaction): NormalizedTransaction {
        return NormalizedTransaction(
            id = transaction.transactionId,
            type = NormalizedTransactionType.WITHDRAWAL,
            source = getSource(transaction),
            transactionAmountFiat = ExchangeAmount(-1.0, "USD"), // TODO populate
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = transaction.asset2!!.absoluteValue,
            assetValueFiat = ExchangeAmount(-1.0, "USD"), // TODO populate
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )
    }

    /**
     * Normalizes an on-chain transaction.
     * These are typically Bitcoin transactions that occur on the blockchain.
     * Note: Asset values need to be populated via API calls (currently pending implementation).
     *
     * @param transaction The on-chain transaction to normalize
     * @return A normalized on-chain transaction
     */
    private fun normalizeOnChainTransaction(transaction: StrikeTransaction): NormalizedTransaction
        = NormalizedTransaction(
            id = transaction.transactionId,
            type = NormalizedTransactionType.WITHDRAWAL,
            source = getSource(transaction),
            transactionAmountFiat = ExchangeAmount(-1.0, "USD"),
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = transaction.asset2!!.absoluteValue,
            assetValueFiat = ExchangeAmount(0.0, "USD"), // TODO call API for this
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            address = transaction.destination ?: "",
            filedWithIRS = false,
        )

    /**
     * Normalizes Strike credit transactions, including referral bonuses.
     * These transactions are treated as broker credits with no associated fees.
     *
     * @param transaction The Strike credit transaction to normalize
     * @return A normalized broker credit transaction
     */
    private fun normalizeStrikeCredit(transaction: StrikeTransaction): NormalizedTransaction
        = NormalizedTransaction(
            id = transaction.transactionId,
            type = NormalizedTransactionType.BROKER_CREDIT,
            source = getSource(transaction),
            transactionAmountFiat = transaction.asset1!!.absoluteValue,
            fee = ExchangeAmount(0.0, "USD"), // Deposits are free
            assetAmount = transaction.asset1!!.absoluteValue,
            assetValueFiat = transaction.asset1!!.absoluteValue,
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = false,
        )

    /**
     * Converts Strike-specific transaction sources to normalized transaction sources.
     *
     * @param transaction The Strike transaction containing the source information
     * @return The normalized transaction source
     */
    private fun getSource(transaction: StrikeTransaction)
        = when(transaction.source) {
            StrikeTransactionSource.MONTHLY_STATEMENT -> TransactionSource.STRIKE_MONTHLY
            StrikeTransactionSource.ANNUAL_STATEMENT -> TransactionSource.STRIKE_ANNUAL
        }


    /**
     * Infer the asset value from the transaction.
     *
     * @param transaction StrikeTransaction
     */
    private fun computeAssetValue(transaction: StrikeTransaction): ExchangeAmount {
        val assetValuePerUnit = transaction.asset1!!.absoluteValue.amount / transaction.asset2!!.absoluteValue.amount

        return ExchangeAmount(
            amount = assetValuePerUnit,
            unit = transaction.asset1.unit
        )
    }
}
