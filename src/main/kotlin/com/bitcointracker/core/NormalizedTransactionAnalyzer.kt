package com.bitcointracker.core

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NormalizedTransactionAnalyzer @Inject constructor(
    private val transactionRepository: TransactionRepository
){

    suspend fun getAccumulation(days: Int, asset: String): List<ExchangeAmount> {
        // Calculate the cutoff date n days ago
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND) }.time
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }.time

        // Filter transactions that are buys within the last n days
        // TODO abstract this out into its own function
        val recentBuys = transactionRepository.getFilteredTransactions(types = listOf(NormalizedTransactionType.BUY), assets = listOf("BTC"), startDate = cutoffDate).sortedBy { it.timestamp }
        val recentSells = transactionRepository.getFilteredTransactions(types = listOf(NormalizedTransactionType.SELL), assets = listOf("BTC"), startDate = cutoffDate).sortedBy { it.timestamp }

        // Initialize the cumulative amounts list
        val cumulativeAmounts = MutableList(days) { ExchangeAmount(0.0, asset) }
        var totalAmount = ExchangeAmount(0.0, asset)

        // Track each day's cumulative total
        for (day in 0 until days) {
            val dayStart = Calendar.getInstance().apply {
                time = today
                add(Calendar.DAY_OF_YEAR, -(days - 1 - day))
            }.time
            val dayEnd = Calendar.getInstance().apply {
                time = dayStart
                add(Calendar.DAY_OF_YEAR, 1)
            }.time

            // Sum up transactions that occurred on this specific day
            val dayBuys = recentBuys.filter {
                it.timestamp.after(dayStart) && it.timestamp.before(dayEnd)
            }
            val daySells = recentSells.filter {
                it.timestamp.after(dayStart) && it.timestamp.before(dayEnd)
            }


            for (transaction in dayBuys) {
                totalAmount += transaction.assetAmount
            }
            for (transaction in daySells) {
                totalAmount -= transaction.assetAmount
            }

            // Store the cumulative amount for the day
            cumulativeAmounts[day] = totalAmount
        }

        return cumulativeAmounts
    }
}
