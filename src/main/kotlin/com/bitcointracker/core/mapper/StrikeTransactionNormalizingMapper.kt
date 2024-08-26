package com.bitcointracker.core.mapper

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.transaction.strike.StrikeTransaction
import com.bitcointracker.model.transaction.strike.StrikeTransactionType

class StrikeTransactionNormalizingMapper() : NormalizingMapper<StrikeTransaction> {
    override fun normalizeTransaction(transaction: StrikeTransaction): NormalizedTransaction {
        // println("Normalizing transaction " + transaction.transactionId)

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
            type = NormalizedTransactionType.DEPOSIT,
            transactionAmountUSD = transaction.asset1!!,
            fee = ExchangeAmount(0.0, "USD"), // Deposits are free
            assetAmount = ExchangeAmount(0.0, "USD"),
            assetValueUSD = ExchangeAmount(0.0, "USD"),
            timestamp = transaction.date
        )

    private fun normalizeTrade(transaction: StrikeTransaction): NormalizedTransaction {
        var transactionType: NormalizedTransactionType
        var transactionAmountUSD: ExchangeAmount
        var assetAmount: ExchangeAmount
        if (transaction.asset1!!.unit != "USD") {
            transactionType = NormalizedTransactionType.SELL
            transactionAmountUSD = transaction.asset2!!
            assetAmount = transaction.asset1
        } else {
            transactionType = NormalizedTransactionType.BUY
            transactionAmountUSD = transaction.asset1 * -1.0
            assetAmount = transaction.asset2!!
        }

        return NormalizedTransaction(
            id = transaction.transactionId,
            type = transactionType,
            transactionAmountUSD = transactionAmountUSD,
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = assetAmount,
            assetValueUSD = transaction.assetValue ?: ExchangeAmount(0.0, "USD"),
            timestamp = transaction.date,
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
            transactionAmountUSD = transaction.asset1!! * -1.0,
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = transaction.asset1!! * -1.0,
            assetValueUSD = transaction.asset1!! * -1.0,
            timestamp = transaction.date,
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
                transactionAmountUSD = ExchangeAmount(-1.0, "USD"), // TODO populate
                fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
                assetAmount = transaction.asset2!! * -1.0,
                assetValueUSD = ExchangeAmount(-1.0, "USD"), // TODO populate
                timestamp = transaction.date,
        )
    }

    private fun normalizeOnChainTransaction(transaction: StrikeTransaction): NormalizedTransaction
        = NormalizedTransaction(
            id = transaction.transactionId,
            type = NormalizedTransactionType.WITHDRAWAL,
            transactionAmountUSD = ExchangeAmount(-1.0, "USD"),
            fee = transaction.fee ?: ExchangeAmount(0.0, "USD"),
            assetAmount = transaction.asset2!! * -1.0,
            assetValueUSD = ExchangeAmount(0.0, "USD"), // TODO call API for this
            timestamp = transaction.date,
            address = transaction.destination ?: ""
        )

    private fun normalizeStrikeCredit(transaction: StrikeTransaction): NormalizedTransaction
        = NormalizedTransaction(
            id = transaction.transactionId,
            type = NormalizedTransactionType.BROKER_CREDIT,
            transactionAmountUSD = transaction.asset1!!,
            fee = ExchangeAmount(0.0, "USD"), // Deposits are free
            assetAmount = transaction.asset1!!,
            assetValueUSD = ExchangeAmount(-1.0, "USD"), // TODO populate
            timestamp = transaction.date
        )
}
