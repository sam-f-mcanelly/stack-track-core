package com.bitcointracker.core

import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType

class TransactionCache(
    private val transactions: List<NormalizedTransaction>
) {

    private var transactionsByType: Map<NormalizedTransactionType, List<NormalizedTransaction>>
    private var transactionsById: Map<String, NormalizedTransaction>
    private var transactionsByAsset: Map<String, List<NormalizedTransaction>>

    init {
        transactionsByType = transactions.groupBy { it.type }
        transactionsById = transactions.associateBy { it.id }
        transactionsByAsset = transactions.groupBy { it.assetAmount.unit }
    }

    fun getAllTransactions() = transactions

    fun getTransactionsByType(vararg types: NormalizedTransactionType): List<NormalizedTransaction> {
        return types.flatMap {
            transactionsByType.getOrDefault(it, listOf())
        }
    }

    fun getTransactionsByAsset(asset: String) = transactionsByAsset.getOrDefault(asset, listOf())

    fun getTransactionById(id: String) = transactionsById[id]
}