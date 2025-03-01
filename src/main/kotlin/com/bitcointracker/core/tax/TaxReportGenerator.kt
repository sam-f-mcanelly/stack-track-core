package com.bitcointracker.core.tax

import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.model.api.exception.TaxReportProcessingException
import com.bitcointracker.model.api.tax.TaxReportRequest
import com.bitcointracker.model.api.tax.TaxTreatment
import com.bitcointracker.model.api.tax.TaxableEventParameters
import com.bitcointracker.model.internal.tax.TaxReportResult
import com.bitcointracker.model.internal.tax.TaxType
import com.bitcointracker.model.internal.tax.TaxableEventResult
import com.bitcointracker.model.internal.tax.UsedBuyTransaction
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import org.slf4j.LoggerFactory
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processes tax reports by analyzing cryptocurrency transactions according to different tax treatment strategies.
 * Supports FIFO, LIFO, custom matching, and profit optimization strategies.
 *
 * @property transactionRepository Repository for accessing transaction data
 */
@Singleton
class TaxReportGenerator @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TaxReportGenerator::class.java)
    }
    /**
     * Processes a tax report request containing multiple taxable events.
     * Each event is processed according to its specified tax treatment strategy.
     *
     * @param request The tax report request containing events to process
     * @return TaxReportResult containing the processed results for each event
     * @throws com.bitcointracker.model.api.exception.TaxReportProcessingException if any event fails to process
     */
    suspend fun processTaxReport(request: TaxReportRequest): TaxReportResult {
        logger.info("processTaxReport($request)")
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
        logger.info("processFIFO($event)")

        val sellTransaction = getSellTransaction(event.sellId) ?:
        throw IllegalArgumentException("Sell transaction ${event.sellId} not found")

        val buyTransactions = getAllBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit
        )
            .sortedBy { it.timestamp }

        return processEvent(sellTransaction, buyTransactions.sortedBy { it.timestamp })
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

        val buyTransactions = getAllBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit
        )
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

        val buyTransactions = getAllBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit
        )
            .sortedBy { it.assetValueFiat }

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

        val buyTransactions = getAllBuyTransactionsBeforeSell(
            sellTransaction.timestamp,
            sellTransaction.assetAmount.unit
        )
            .sortedByDescending { it.assetValueFiat }

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
     * If there aren't enough buy transactions to cover the sell amount, the remaining
     * amount is treated as having a cost basis of 0.0.
     *
     * @param sellTransaction The sell transaction to process
     * @param buyTransactions List of buy transactions to use, in order of preference
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     */
    private fun processEvent(
        sellTransaction: NormalizedTransaction,
        buyTransactions: List<NormalizedTransaction>
    ): TaxableEventResult {
        var remainingSellAmount = sellTransaction.assetAmount
        val usedBuyTransactions = mutableListOf<UsedBuyTransaction>()
        var totalCostBasis = ExchangeAmount(0.0, sellTransaction.transactionAmountFiat.unit)

        // Calculate the portion of buy transactions that can be covered
        for (buyTx in buyTransactions) {
            logger.info("processing buy: ${buyTx}")

            if (remainingSellAmount <= ExchangeAmount(0.0, sellTransaction.assetAmount.unit)) break

            val amountToUse = minOf(remainingSellAmount, buyTx.assetAmount)
            val costBasis = ExchangeAmount(
                (amountToUse.amount / buyTx.assetAmount.amount) * buyTx.transactionAmountFiat.amount,
                buyTx.transactionAmountFiat.unit
            )
            logger.info("Cost basis: $costBasis")

            // Calculate holding period
            val holdingPeriodMillis = sellTransaction.timestamp.time - buyTx.timestamp.time
            val holdingPeriodDays = holdingPeriodMillis / (1000 * 60 * 60 * 24)
            val taxType = if (holdingPeriodDays >= 365) TaxType.LONG_TERM else TaxType.SHORT_TERM

            logger.info("Determined tax type: $taxType")

            usedBuyTransactions.add(
                UsedBuyTransaction(
                    transactionId = buyTx.id,
                    amountUsed = amountToUse,
                    costBasis = costBasis,
                    taxType = taxType,
                    originalTransaction = buyTx,
                )
            )

            totalCostBasis += costBasis
            remainingSellAmount -= amountToUse

            logger.info("Total cost basis so far: $totalCostBasis")
            logger.info("Remaining sell: $remainingSellAmount")
        }

        // Calculate uncovered amount and value
        val uncoveredSellAmount = if (remainingSellAmount.amount > 0.0001) {
            remainingSellAmount
        } else {
            ExchangeAmount(0.0, sellTransaction.assetAmount.unit)
        }

        // Calculate the proportion of the total sell value that's uncovered
        val uncoveredRatio = uncoveredSellAmount.amount / sellTransaction.assetAmount.amount
        val uncoveredSellValue = ExchangeAmount(
            sellTransaction.transactionAmountFiat.amount * uncoveredRatio,
            sellTransaction.transactionAmountFiat.unit
        )

        // Add the uncovered portion directly to the gain (cost basis = 0)
        val proceeds = sellTransaction.transactionAmountFiat
        val gain = proceeds - totalCostBasis

        return TaxableEventResult(
            sellTransactionId = sellTransaction.id,
            proceeds = proceeds,
            costBasis = totalCostBasis,
            gain = gain,
            sellTransaction = sellTransaction,
            usedBuyTransactions = usedBuyTransactions,
            uncoveredSellAmount = uncoveredSellAmount,
            uncoveredSellValue = uncoveredSellValue
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
     * @param asset The asset to look up
     * @return List of buy transactions before the sell date
     */
    private suspend fun getAllBuyTransactionsBeforeSell(sellDate: Date, asset: String): List<NormalizedTransaction> {
        return transactionRepository.getFilteredTransactions(
            types = listOf(NormalizedTransactionType.BUY),
            assets = listOf(asset),
        )
            .filter { !it.filedWithIRS }
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
        return ids.map { id ->
            val transaction = transactionRepository.getTransactionById(id)
            if (transaction?.type != NormalizedTransactionType.BUY) {
                throw IllegalArgumentException("Transaction $id is not a buy transaction")
            }
            transaction
        }
    }
}
