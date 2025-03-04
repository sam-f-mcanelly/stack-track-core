package com.bitcointracker.core.tax

import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.model.api.exception.TaxReportProcessingException
import com.bitcointracker.model.api.tax.TaxReportRequest
import com.bitcointracker.model.api.tax.TaxTreatment
import com.bitcointracker.model.api.tax.TaxableEventParameters
import com.bitcointracker.model.api.tax.TaxReportResult
import com.bitcointracker.model.api.tax.TaxableEventResult
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import org.slf4j.LoggerFactory
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates tax report generation by delegating to appropriate tax strategies.
 */
@Singleton
class TaxReportGenerator @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val taxCalculator: TaxCalculator,
    private val transactionTrackerFactory: TransactionTrackerFactory,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(TaxReportGenerator::class.java)
    }

    /**
     * Processes a tax report request containing multiple taxable events.
     * Events with CUSTOM tax treatment are prioritized.
     *
     * @param request The tax report request containing events to process
     * @return TaxReportResult containing the processed results for each event
     * @throws TaxReportProcessingException if any event fails to process
     */
    suspend fun processTaxReport(request: TaxReportRequest): TaxReportResult {
        logger.info("processTaxReport($request)")
        val results = mutableListOf<TaxableEventResult>()
        val transactionTracker = transactionTrackerFactory.create()

        // Sort events so CUSTOM events are processed first
        val sortedEvents = request.taxableEvents.sortedWith(
            compareBy {
                if (it.taxTreatment == TaxTreatment.CUSTOM) 0 else 1
            }
        )

        logger.info("Processing events in order: ${sortedEvents.map { it.taxTreatment }}")

        for (event in sortedEvents) {
            try {
                val result = when (event.taxTreatment) {
                    TaxTreatment.FIFO -> processFIFO(event, transactionTracker)
                    TaxTreatment.LIFO -> processLIFO(event, transactionTracker)
                    TaxTreatment.MAX_PROFIT -> processMaxProfit(event, transactionTracker)
                    TaxTreatment.MIN_PROFIT -> processMinProfit(event, transactionTracker)
                    TaxTreatment.CUSTOM -> processCustom(event, transactionTracker)
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
     */
    private suspend fun processFIFO(
        event: TaxableEventParameters,
        transactionTracker: TransactionTracker
    ): TaxableEventResult {
        logger.info("processFIFO($event)")

        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAvailableBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit,
            transactionTracker
        ).sortedBy { it.timestamp }

        return taxCalculator.calculateTaxableEvent(sellTransaction, buyTransactions, transactionTracker)
    }

    /**
     * Processes a taxable event using Last In First Out (LIFO) strategy.
     */
    private suspend fun processLIFO(
        event: TaxableEventParameters,
        transactionTracker: TransactionTracker
    ): TaxableEventResult {
        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAvailableBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit,
            transactionTracker
        ).sortedByDescending { it.timestamp }

        return taxCalculator.calculateTaxableEvent(sellTransaction, buyTransactions, transactionTracker)
    }

    /**
     * Processes a taxable event using Maximum Profit strategy.
     */
    private suspend fun processMaxProfit(
        event: TaxableEventParameters,
        transactionTracker: TransactionTracker
    ): TaxableEventResult {
        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAvailableBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit,
            transactionTracker
        ).sortedBy { it.assetValueFiat }

        return taxCalculator.calculateTaxableEvent(sellTransaction, buyTransactions, transactionTracker)
    }

    /**
     * Processes a taxable event using Minimum Profit strategy.
     */
    private suspend fun processMinProfit(
        event: TaxableEventParameters,
        transactionTracker: TransactionTracker
    ): TaxableEventResult {
        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAvailableBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit,
            transactionTracker
        ).sortedByDescending { it.assetValueFiat }

        return taxCalculator.calculateTaxableEvent(sellTransaction, buyTransactions, transactionTracker)
    }

    /**
     * Processes a taxable event using Custom strategy with specified buy transactions.
     */
    private suspend fun processCustom(
        event: TaxableEventParameters,
        transactionTracker: TransactionTracker
    ): TaxableEventResult {
        if (event.buyTransactionIds.isNullOrEmpty()) {
            throw IllegalArgumentException("Custom tax treatment requires buy transaction IDs")
        }

        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAvailableBuyTransactionsByIds(
            event.buyTransactionIds,
            sellTransaction.timestamp,
            transactionTracker
        )

        if (buyTransactions.isEmpty()) {
            throw IllegalArgumentException("No valid buy transactions with available amounts found")
        }

        return taxCalculator.calculateTaxableEvent(sellTransaction, buyTransactions, transactionTracker)
    }

    /**
     * Retrieves a sell transaction by ID and validates its type.
     */
    private suspend fun getSellTransaction(id: String): NormalizedTransaction? {
        val transaction = transactionRepository.getTransactionById(id)
        return if (transaction?.type == NormalizedTransactionType.SELL) transaction else null
    }

    /**
     * Retrieves available buy transactions that occurred before the given sell date.
     */
    private suspend fun getAvailableBuyTransactionsBeforeSell(
        sellDate: Date,
        asset: String,
        transactionTracker: TransactionTracker
    ): List<NormalizedTransaction> {
        return transactionRepository.getFilteredTransactions(
            types = listOf(NormalizedTransactionType.BUY),
            assets = listOf(asset),
        )
            .filter { !it.filedWithIRS }
            .filter { it.timestamp < sellDate }
            .filter { transactionTracker.getAvailableAmount(it).amount > 0.0 }
    }

    /**
     * Retrieves and validates buy transactions by their IDs, checking for available amounts.
     */
    private suspend fun getAvailableBuyTransactionsByIds(
        ids: List<String>,
        sellDate: Date,
        transactionTracker: TransactionTracker
    ): List<NormalizedTransaction> {
        return ids.mapNotNull { id ->
            val transaction = transactionRepository.getTransactionById(id)

            when {
                transaction == null -> {
                    logger.warn("Buy transaction $id not found")
                    null
                }
                transaction.type != NormalizedTransactionType.BUY -> {
                    throw IllegalArgumentException("Transaction $id is not a buy transaction")
                }
                transaction.timestamp >= sellDate -> {
                    throw IllegalArgumentException("Buy transaction $id occurs after sell transaction")
                }
                else -> {
                    // Check if this transaction has available amount
                    val availableAmount = transactionTracker.getAvailableAmount(transaction)
                    if (availableAmount.amount <= 0.0) {
                        logger.warn("Buy transaction $id has no available amount")
                        null
                    } else {
                        transaction
                    }
                }
            }
        }
    }
}
