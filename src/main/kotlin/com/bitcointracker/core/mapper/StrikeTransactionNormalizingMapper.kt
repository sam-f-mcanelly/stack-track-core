package com.bitcointracker.core.mapper

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.TransactionSource
import com.bitcointracker.model.internal.transaction.strike.StrikeTransaction
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionSource
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionType
import javax.inject.Inject

// TODO handle reversed deposits
class StrikeTransactionNormalizingMapper @Inject constructor () : NormalizingMapper<StrikeTransaction> {
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
            filedWithIRS = FiledTransactions.filteredTransactionIds.contains(transaction.transactionId)
    )

    private fun normalizeTrade(transaction: StrikeTransaction): NormalizedTransaction {
        var transactionType: NormalizedTransactionType
        var transactionAmountFiat: ExchangeAmount
        var assetAmount: ExchangeAmount
        if (transaction.asset1!!.unit != "USD") {
            transactionType = NormalizedTransactionType.SELL
            transactionAmountFiat = transaction.asset2!!
            assetAmount = transaction.asset1
        } else {
            transactionType = NormalizedTransactionType.BUY
            transactionAmountFiat = transaction.asset1.absoluteValue
            assetAmount = transaction.asset2!!
        }

        return NormalizedTransaction(
            id = transaction.transactionId,
            type = transactionType,
            source = getSource(transaction),
            transactionAmountFiat = transactionAmountFiat,
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = assetAmount,
            assetValueFiat = transaction.assetValue ?: ExchangeAmount(0.0, "USD"),
            timestamp = transaction.date,
            timestampText = transaction.date.toString(),
            filedWithIRS = FiledTransactions.filteredTransactionIds.contains(transaction.transactionId)
        )
    }

    private fun normalizeWithdrawal(transaction: StrikeTransaction): NormalizedTransaction {
        // println("Normalizing withdrawal: " + transaction)

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
            filedWithIRS = FiledTransactions.filteredTransactionIds.contains(transaction.transactionId)
        )
    }

    /**
     * Special handling for Strike's old style of reporting BTC withdrawals as a withdrawal
     * instead of labelling it ONCHAIN.
     *
     * @param transaction StrikeTransaction
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
            filedWithIRS = FiledTransactions.filteredTransactionIds.contains(transaction.transactionId)
        )
    }

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
            filedWithIRS = FiledTransactions.filteredTransactionIds.contains(transaction.transactionId)
        )

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
            filedWithIRS = FiledTransactions.filteredTransactionIds.contains(transaction.transactionId)
        )

    private fun getSource(transaction: StrikeTransaction)
        = when(transaction.source) {
            StrikeTransactionSource.MONTHLY_STATEMENT -> TransactionSource.STRIKE_MONTHLY
            StrikeTransactionSource.ANNUAL_STATEMENT -> TransactionSource.STRIKE_ANNUAL
        }
}
