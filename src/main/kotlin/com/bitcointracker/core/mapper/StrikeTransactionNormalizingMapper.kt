package com.bitcointracker.core.mapper

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.transaction.strike.StrikeTransaction
import com.bitcointracker.model.transaction.strike.StrikeTransactionType

class StrikeTransactionNormalizingMapper() : NormalizingMapper<StrikeTransaction> {
    override fun normalizeTransaction(transaction: StrikeTransaction): NormalizedTransaction 
        = when(transaction.type) {
            StrikeTransactionType.DEPOSIT -> normalizeDeposit(transaction)
            StrikeTransactionType.TRADE -> normalizeDeposit(transaction) // TODO fix
            StrikeTransactionType.WITHDRAWAL -> normalizeDeposit(transaction) // TODO fix
            StrikeTransactionType.ONCHAIN -> normalizeDeposit(transaction) // TODO fix
        }

    private fun normalizeDeposit(transaction: StrikeTransaction): NormalizedTransaction
        = NormalizedTransaction(
            id = transaction.transactionId,
            type = NormalizedTransactionType.DEPOSIT,
            transactionAmount = transaction.assetOut!!,
            fee = ExchangeAmount(0.0, "USD"), // TODO fix
            assetAmount = ExchangeAmount(0.0, "USD"),
            assetValueUSD = ExchangeAmount(0.0, "USD"),
            timestamp = transaction.date
        )
}