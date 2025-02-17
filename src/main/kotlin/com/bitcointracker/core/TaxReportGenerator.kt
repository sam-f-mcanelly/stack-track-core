package com.bitcointracker.core

import com.bitcointracker.model.api.tax.TaxReportRequest
import com.bitcointracker.model.api.tax.TaxTreatment
import com.bitcointracker.model.api.tax.TaxableEventParameters
import com.bitcointracker.model.exception.InsufficientBuyTransactionsException
import com.bitcointracker.model.api.exception.TaxReportProcessingException
import com.bitcointracker.model.internal.tax.TaxReportResult
import com.bitcointracker.model.internal.tax.TaxableEventResult
import com.bitcointracker.model.internal.tax.UsedBuyTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import java.util.Date
import javax.inject.Inject

/**
 * Processes tax reports by analyzing cryptocurrency transactions according to different tax treatment strategies.
 * Supports FIFO, LIFO, custom matching, and profit optimization strategies.
 *
 * @property transactionRepository Repository for accessing transaction data
 */
class TaxReportProcessor @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    /**
     * Processes a tax report request containing multiple taxable events.
     * Each event is processed according to its specified tax treatment strategy.
     *
     * @param request The tax report request containing events to process
     * @return TaxReportResult containing the processed results for each event
     * @throws TaxReportProcessingException if any event fails to process
     */
    suspend fun processTaxReport(request: TaxReportRequest): TaxReportResult {
        val results = mutableListOf<TaxableEventResult>()

        for (event in request.taxableEvents) {
            try {
                val result = when (event.taxTreatment) {
                    TaxTreatment.FIFO -> processFIFO(event)
                    TaxTreatment.LIFO -> processLIFO(event)
                    TaxTreatment.MAX_PROFIT -> processMaxProfit(event)
                    TaxTreatment.MIN_PROFIT -> processMinProfit(event)
                    TaxTreatment.CUSTOM -> processCustom(event)
                }
                results.add(result)
            } catch (e: Exception) {
                throw TaxReportProcessingException(
                    "Failed to process event with sellId: ${event.sellId}",
                    event,
                    e
                )
            }
        }

        return TaxReportResult(
            requestId = request.requestId,
            results = results
        )
    }

    /**
     * Processes a taxable event using First In First Out (FIFO) strategy.
     * Matches sell transactions with the oldest available buy transactions.
     *
     * @param event The taxable event to process
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     * @throws IllegalArgumentException if the sell transaction is not found
     */
    private suspend fun processFIFO(event: TaxableEventParameters): TaxableEventResult {
        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAllBuyTransactionsBeforeSell(sellTransaction.timestamp)
            .sortedBy { it.timestamp }

        return processEvent(sellTransaction, buyTransactions)
    }

    /**
     * Processes a taxable event using Last In First Out (LIFO) strategy.
     * Matches sell transactions with the newest available buy transactions.
     *
     * @param event The taxable event to process
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     * @throws IllegalArgumentException if the sell transaction is not found
     */
    private suspend fun processLIFO(event: TaxableEventParameters): TaxableEventResult {
        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAllBuyTransactionsBeforeSell(sellTransaction.timestamp)
            .sortedByDescending { it.timestamp }

        return processEvent(sellTransaction, buyTransactions)
    }

    /**
     * Processes a taxable event using Maximum Profit strategy.
     * Matches sell transactions with buy transactions to maximize taxable gains.
     *
     * @param event The taxable event to process
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     * @throws IllegalArgumentException if the sell transaction is not found
     */
    private suspend fun processMaxProfit(event: TaxableEventParameters): TaxableEventResult {
        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAllBuyTransactionsBeforeSell(sellTransaction.timestamp)
            .sortedBy { it.assetValueFiat.amount / it.assetAmount.amount }

        return processEvent(sellTransaction, buyTransactions)
    }

    /**
     * Processes a taxable event using Minimum Profit strategy.
     * Matches sell transactions with buy transactions to minimize taxable gains.
     *
     * @param event The taxable event to process
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     * @throws IllegalArgumentException if the sell transaction is not found
     */
    private suspend fun processMinProfit(event: TaxableEventParameters): TaxableEventResult {
        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAllBuyTransactionsBeforeSell(sellTransaction.timestamp)
            .sortedByDescending { it.assetValueFiat.amount / it.assetAmount.amount }

        return processEvent(sellTransaction, buyTransactions)
    }

    /**
     * Processes a taxable event using Custom strategy with specified buy transactions.
     * Validates that provided buy transactions exist and can cover the sell amount.
     *
     * @param event The taxable event to process, must include buyTransactionIds
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     * @throws IllegalArgumentException if buy transactions are invalid or insufficient
     */
    private suspend fun processCustom(event: TaxableEventParameters): TaxableEventResult {
        if (event.buyTransactionIds.isNullOrEmpty()) {
            throw IllegalArgumentException("Custom tax treatment requires buy transaction IDs")
        }

        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getBuyTransactionsByIds(event.buyTransactionIds)

        if (buyTransactions.size != event.buyTransactionIds.size) {
            throw IllegalArgumentException("Not all specified buy transactions were found")
        }

        buyTransactions.forEach { buyTx ->
            if (buyTx.timestamp >= sellTransaction.timestamp) {
                throw IllegalArgumentException("Buy transaction ${buyTx.id} occurs after sell transaction")
            }
        }

        return processEvent(sellTransaction, buyTransactions)
    }

    /**
     * Core processing logic for matching buy transactions to a sell transaction.
     * Calculates cost basis and gains based on the provided buy transactions.
     *
     * @param sellTransaction The sell transaction to process
     * @param buyTransactions List of buy transactions to use, in order of preference
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     * @throws InsufficientBuyTransactionsException if buy transactions cannot cover the sell amount
     */
    private suspend fun processEvent(
        sellTransaction: NormalizedTransaction,
        buyTransactions: List<NormalizedTransaction>
    ): TaxableEventResult {
        var remainingSellAmount = sellTransaction.assetAmount.amount
        val usedBuyTransactions = mutableListOf<UsedBuyTransaction>()
        var totalCostBasis = 0.0

        for (buyTx in buyTransactions) {
            if (remainingSellAmount <= 0) break

            val amountToUse = minOf(remainingSellAmount, buyTx.assetAmount.amount)
            val costBasis = (amountToUse / buyTx.assetAmount.amount) * buyTx.assetValueFiat.amount

            usedBuyTransactions.add(
                UsedBuyTransaction(
                    transactionId = buyTx.id,
                    amountUsed = amountToUse,
                    costBasis = costBasis
                )
            )

            totalCostBasis += costBasis
            remainingSellAmount -= amountToUse
        }

        if (remainingSellAmount > 0.0001) {
            throw InsufficientBuyTransactionsException(
                "Not enough buy transactions to cover sell amount of ${sellTransaction.assetAmount.amount}"
            )
        }

        val proceeds = sellTransaction.assetValueFiat.amount
        val gain = proceeds - totalCostBasis

        return TaxableEventResult(
            sellTransactionId = sellTransaction.id,
            proceeds = proceeds,
            costBasis = totalCostBasis,
            gain = gain,
            usedBuyTransactions = usedBuyTransactions
        )
    }

    /**
     * Retrieves a sell transaction by ID and validates its type.
     *
     * @param id The transaction ID to retrieve
     * @return NormalizedTransaction if found and is a sell transaction, null otherwise
     */
    private suspend fun getSellTransaction(id: String): NormalizedTransaction? {
        val transaction = transactionRepository.getTransactionById(id)
        return if (transaction?.type == NormalizedTransactionType.SELL) transaction else null
    }

    /**
     * Retrieves all buy transactions that occurred before the given sell date.
     *
     * @param sellDate The date to filter buy transactions by
     * @return List of buy transactions before the sell date
     */
    private suspend fun getAllBuyTransactionsBeforeSell(sellDate: Date): List<NormalizedTransaction> {
        return transactionRepository.getTransactionsByType(NormalizedTransactionType.BUY)
            .filter { it.timestamp < sellDate }
    }

    /**
     * Retrieves and validates buy transactions by their IDs.
     *
     * @param ids List of transaction IDs to retrieve
     * @return List of validated buy transactions
     * @throws IllegalArgumentException if any transaction is not found or is not a buy transaction
     */
    private suspend fun getBuyTransactionsByIds(ids: List<String>): List<NormalizedTransaction> {
        return ids.mapNotNull { id ->
            val transaction = transactionRepository.getTransactionById(id)
            if (transaction?.type != NormalizedTransactionType.BUY) {
                throw IllegalArgumentException("Transaction $id is not a buy transaction")
            }
            transaction
        }
    }
}


