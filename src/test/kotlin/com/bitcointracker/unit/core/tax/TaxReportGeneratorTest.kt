package com.bitcointracker.unit.core.tax

import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.core.tax.TaxCalculator
import com.bitcointracker.core.tax.TaxReportGenerator
import com.bitcointracker.core.tax.TransactionTracker
import com.bitcointracker.core.tax.TransactionTrackerFactory
import com.bitcointracker.model.api.exception.TaxReportProcessingException
import com.bitcointracker.model.api.tax.TaxReportRequest
import com.bitcointracker.model.api.tax.TaxTreatment
import com.bitcointracker.model.api.tax.TaxableEventParameters
import com.bitcointracker.model.internal.tax.TaxableEventResult
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.TransactionSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Date

@ExtendWith(MockKExtension::class)
class TaxReportGeneratorTest {

    @MockK
    private lateinit var transactionRepository: TransactionRepository

    @MockK
    private lateinit var taxCalculator: TaxCalculator

    @MockK
    private lateinit var transactionTrackerFactory: TransactionTrackerFactory

    @MockK
    private lateinit var transactionTracker: TransactionTracker

    @InjectMockKs
    private lateinit var taxReportGenerator: TaxReportGenerator

    // Test constants
    private val sellTxId = "sell1"
    private val buyTx1Id = "buy1"
    private val buyTx2Id = "buy2"
    private val requestId = "request1"
    private val btcUnit = "BTC"
    private val usdUnit = "USD"

    // Base date for creating transactions with correct chronology
    private val baseDate = Date(1000000000000) // A fixed point in time
    private val beforeBaseDate = Date(900000000000) // Earlier than baseDate

    @BeforeEach
    fun setUp() {
        // Setup transaction tracker factory
        every { transactionTrackerFactory.create() } returns transactionTracker
    }

    @Test
    fun `processTaxReport processes events in correct order with CUSTOM first`() = runBlocking {
        // Arrange
        val customEvent = TaxableEventParameters(
            sellId = "customSell",
            taxTreatment = TaxTreatment.CUSTOM,
            buyTransactionIds = listOf(buyTx1Id)
        )

        val fifoEvent = TaxableEventParameters(
            sellId = "fifoSell",
            taxTreatment = TaxTreatment.FIFO
        )

        val request = TaxReportRequest(
            requestId = requestId,
            taxableEvents = listOf(fifoEvent, customEvent) // FIFO first, should be reordered
        )

        // Mock sell transactions - must be after buy transactions
        val customSellTx = createMockTransaction(
            "customSell",
            NormalizedTransactionType.SELL,
            timestamp = baseDate // Sell happens at baseDate
        )

        val fifoSellTx = createMockTransaction(
            "fifoSell",
            NormalizedTransactionType.SELL,
            timestamp = baseDate // Sell happens at baseDate
        )

        coEvery { transactionRepository.getTransactionById("customSell") } returns customSellTx
        coEvery { transactionRepository.getTransactionById("fifoSell") } returns fifoSellTx

        // Mock buy transactions - must be before sell transactions
        val buyTx = createMockTransaction(
            buyTx1Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate // Buy happens BEFORE sell
        )

        coEvery {
            transactionRepository.getTransactionById(buyTx1Id)
        } returns buyTx

        // Mock filtered transactions for FIFO
        coEvery {
            transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf(btcUnit)
            )
        } returns listOf(buyTx)

        // Mock transaction tracker behavior
        every { transactionTracker.getAvailableAmount(any()) } returns ExchangeAmount(1.0, btcUnit)

        // Mock tax calculator results
        val customResult = mockTaxableEventResult("customSell")
        val fifoResult = mockTaxableEventResult("fifoSell")

        every {
            taxCalculator.calculateTaxableEvent(
                eq(customSellTx),
                any(),
                eq(transactionTracker)
            )
        } returns customResult

        every {
            taxCalculator.calculateTaxableEvent(
                eq(fifoSellTx),
                any(),
                eq(transactionTracker)
            )
        } returns fifoResult

        // Act
        val result = taxReportGenerator.processTaxReport(request)

        // Assert
        assertEquals(requestId, result.requestId)
        assertEquals(2, result.results.size)

        // First result should be from the CUSTOM strategy (should be processed first)
        assertEquals("customSell", result.results[0].sellTransactionId)

        // Second result should be from the FIFO strategy
        assertEquals("fifoSell", result.results[1].sellTransactionId)

        // Verify the correct order of calls
        coVerify(exactly = 1) {
            transactionRepository.getTransactionById("customSell")
            transactionRepository.getTransactionById(buyTx1Id)
        }

        coVerify(exactly = 1) {
            transactionRepository.getTransactionById("fifoSell")
            transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf(btcUnit)
            )
        }
    }

    @Test
    fun `processFIFO should use oldest buy transactions first`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.FIFO
        )

        val sellTransaction = createMockTransaction(
            sellTxId,
            NormalizedTransactionType.SELL,
            timestamp = baseDate // Sell happens at baseDate
        )

        val oldBuyTx = createMockTransaction(
            buyTx1Id,
            NormalizedTransactionType.BUY,
            timestamp = Date(800000000000) // Oldest buy
        )

        val newBuyTx = createMockTransaction(
            buyTx2Id,
            NormalizedTransactionType.BUY,
            timestamp = Date(900000000000) // Newer buy, but still before sell
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns sellTransaction
        coEvery {
            transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf(btcUnit)
            )
        } returns listOf(newBuyTx, oldBuyTx) // Deliberately unsorted

        every { transactionTracker.getAvailableAmount(any()) } returns ExchangeAmount(1.0, btcUnit)

        val expectedResult = mockTaxableEventResult(sellTxId)

        // Capture the buy transactions passed to calculateTaxableEvent to verify order
        val buyTxSlot = slot<List<NormalizedTransaction>>()

        every {
            taxCalculator.calculateTaxableEvent(
                eq(sellTransaction),
                capture(buyTxSlot),
                eq(transactionTracker)
            )
        } returns expectedResult

        // Act
        val result = taxReportGenerator.processTaxReport(
            TaxReportRequest(requestId, listOf(event))
        )

        // Assert
        assertEquals(expectedResult, result.results[0])

        // Verify buy transactions are passed in date order (oldest first)
        assertEquals(2, buyTxSlot.captured.size)
        assertEquals(oldBuyTx.id, buyTxSlot.captured[0].id) // Oldest should be first
        assertEquals(newBuyTx.id, buyTxSlot.captured[1].id)
    }

    @Test
    fun `processLIFO should use newest buy transactions first`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.LIFO
        )

        val sellTransaction = createMockTransaction(
            sellTxId,
            NormalizedTransactionType.SELL,
            timestamp = baseDate // Sell happens at baseDate
        )

        val oldBuyTx = createMockTransaction(
            buyTx1Id,
            NormalizedTransactionType.BUY,
            timestamp = Date(800000000000) // Older timestamp
        )

        val newBuyTx = createMockTransaction(
            buyTx2Id,
            NormalizedTransactionType.BUY,
            timestamp = Date(900000000000) // Newer timestamp, but still before sell
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns sellTransaction
        coEvery {
            transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf(btcUnit)
            )
        } returns listOf(oldBuyTx, newBuyTx) // Deliberately unsorted

        every { transactionTracker.getAvailableAmount(any()) } returns ExchangeAmount(1.0, btcUnit)

        val expectedResult = mockTaxableEventResult(sellTxId)

        // Capture the buy transactions passed to calculateTaxableEvent to verify order
        val buyTxSlot = slot<List<NormalizedTransaction>>()

        every {
            taxCalculator.calculateTaxableEvent(
                eq(sellTransaction),
                capture(buyTxSlot),
                eq(transactionTracker)
            )
        } returns expectedResult

        // Act
        val result = taxReportGenerator.processTaxReport(
            TaxReportRequest(requestId, listOf(event))
        )

        // Assert
        assertEquals(expectedResult, result.results[0])

        // Verify buy transactions are passed in reverse date order (newest first)
        assertEquals(2, buyTxSlot.captured.size)
        assertEquals(newBuyTx.id, buyTxSlot.captured[0].id) // Newest should be first
        assertEquals(oldBuyTx.id, buyTxSlot.captured[1].id)
    }

    @Test
    fun `processMaxProfit should use lowest value buy transactions first`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.MAX_PROFIT
        )

        val sellTransaction = createMockTransaction(
            sellTxId,
            NormalizedTransactionType.SELL,
            timestamp = baseDate // Sell happens at baseDate
        )

        val cheapBuyTx = createMockTransaction(
            buyTx1Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate, // Must be before sell date
            assetValueFiat = ExchangeAmount(100.0, usdUnit) // Lower value
        )

        val expensiveBuyTx = createMockTransaction(
            buyTx2Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate, // Must be before sell date
            assetValueFiat = ExchangeAmount(200.0, usdUnit) // Higher value
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns sellTransaction
        coEvery {
            transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf(btcUnit)
            )
        } returns listOf(expensiveBuyTx, cheapBuyTx) // Deliberately unsorted

        every { transactionTracker.getAvailableAmount(any()) } returns ExchangeAmount(1.0, btcUnit)

        val expectedResult = mockTaxableEventResult(sellTxId)

        // Capture the buy transactions passed to calculateTaxableEvent to verify order
        val buyTxSlot = slot<List<NormalizedTransaction>>()

        every {
            taxCalculator.calculateTaxableEvent(
                eq(sellTransaction),
                capture(buyTxSlot),
                eq(transactionTracker)
            )
        } returns expectedResult

        // Act
        val result = taxReportGenerator.processTaxReport(
            TaxReportRequest(requestId, listOf(event))
        )

        // Assert
        assertEquals(expectedResult, result.results[0])

        // Verify buy transactions are passed in value order (cheapest first)
        assertEquals(2, buyTxSlot.captured.size)
        assertEquals(cheapBuyTx.id, buyTxSlot.captured[0].id) // Cheapest should be first
        assertEquals(expensiveBuyTx.id, buyTxSlot.captured[1].id)
    }

    @Test
    fun `processMinProfit should use highest value buy transactions first`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.MIN_PROFIT
        )

        val sellTransaction = createMockTransaction(
            sellTxId,
            NormalizedTransactionType.SELL,
            timestamp = baseDate // Sell happens at baseDate
        )

        val cheapBuyTx = createMockTransaction(
            buyTx1Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate, // Must be before sell date
            assetValueFiat = ExchangeAmount(100.0, usdUnit) // Lower value
        )

        val expensiveBuyTx = createMockTransaction(
            buyTx2Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate, // Must be before sell date
            assetValueFiat = ExchangeAmount(200.0, usdUnit) // Higher value
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns sellTransaction
        coEvery {
            transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf(btcUnit)
            )
        } returns listOf(cheapBuyTx, expensiveBuyTx) // Deliberately unsorted

        every { transactionTracker.getAvailableAmount(any()) } returns ExchangeAmount(1.0, btcUnit)

        val expectedResult = mockTaxableEventResult(sellTxId)

        // Capture the buy transactions passed to calculateTaxableEvent to verify order
        val buyTxSlot = slot<List<NormalizedTransaction>>()

        every {
            taxCalculator.calculateTaxableEvent(
                eq(sellTransaction),
                capture(buyTxSlot),
                eq(transactionTracker)
            )
        } returns expectedResult

        // Act
        val result = taxReportGenerator.processTaxReport(
            TaxReportRequest(requestId, listOf(event))
        )

        // Assert
        assertEquals(expectedResult, result.results[0])

        // Verify buy transactions are passed in reverse value order (most expensive first)
        assertEquals(2, buyTxSlot.captured.size)
        assertEquals(expensiveBuyTx.id, buyTxSlot.captured[0].id) // Most expensive should be first
        assertEquals(cheapBuyTx.id, buyTxSlot.captured[1].id)
    }

    @Test
    fun `processCustom should use specified buy transactions`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.CUSTOM,
            buyTransactionIds = listOf(buyTx1Id, buyTx2Id)
        )

        val sellTransaction = createMockTransaction(
            sellTxId,
            NormalizedTransactionType.SELL,
            timestamp = baseDate // Sell happens at baseDate
        )

        val buyTx1 = createMockTransaction(
            buyTx1Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate // Buy happens BEFORE sell
        )

        val buyTx2 = createMockTransaction(
            buyTx2Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate // Buy happens BEFORE sell
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns sellTransaction
        coEvery { transactionRepository.getTransactionById(buyTx1Id) } returns buyTx1
        coEvery { transactionRepository.getTransactionById(buyTx2Id) } returns buyTx2

        every { transactionTracker.getAvailableAmount(any()) } returns ExchangeAmount(1.0, btcUnit)

        val expectedResult = mockTaxableEventResult(sellTxId)

        // Capture the buy transactions passed to calculateTaxableEvent
        val buyTxSlot = slot<List<NormalizedTransaction>>()

        every {
            taxCalculator.calculateTaxableEvent(
                eq(sellTransaction),
                capture(buyTxSlot),
                eq(transactionTracker)
            )
        } returns expectedResult

        // Act
        val result = taxReportGenerator.processTaxReport(
            TaxReportRequest(requestId, listOf(event))
        )

        // Assert
        assertEquals(expectedResult, result.results[0])

        // Verify both specified buy transactions are used
        assertEquals(2, buyTxSlot.captured.size)

        // Both specified buys should be included
        val capturedIds = buyTxSlot.captured.map { it.id }
        assertTrue(capturedIds.contains(buyTx1Id))
        assertTrue(capturedIds.contains(buyTx2Id))

        // Verify repository was called for each specified buy
        coVerify(exactly = 1) {
            transactionRepository.getTransactionById(buyTx1Id)
            transactionRepository.getTransactionById(buyTx2Id)
        }
    }

    @Test
    fun `processCustom should throw exception when buyTransactionIds is empty`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.CUSTOM,
            buyTransactionIds = emptyList() // Empty list
        )

        // Act & Assert
        val exception = assertThrows(TaxReportProcessingException::class.java) {
            runBlocking {
                taxReportGenerator.processTaxReport(
                    TaxReportRequest(requestId, listOf(event))
                )
            }
        }

        // Check for exception cause rather than direct exception message
        val cause = exception.cause
        assertTrue(cause is IllegalArgumentException)
        assertTrue(cause?.message?.contains("Custom tax treatment requires buy transaction IDs") ?: false)
    }

    @Test
    fun `processCustom should throw exception when buy transaction is not found`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.CUSTOM,
            buyTransactionIds = listOf(buyTx1Id)
        )

        val sellTransaction = createMockTransaction(
            sellTxId,
            NormalizedTransactionType.SELL,
            timestamp = baseDate
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns sellTransaction
        coEvery { transactionRepository.getTransactionById(buyTx1Id) } returns null // Not found

        // Act & Assert
        val exception = assertThrows(TaxReportProcessingException::class.java) {
            runBlocking {
                taxReportGenerator.processTaxReport(
                    TaxReportRequest(requestId, listOf(event))
                )
            }
        }

        // Check for exception cause rather than direct exception message
        val cause = exception.cause
        assertTrue(cause is IllegalArgumentException)
        assertTrue(cause?.message?.contains("No valid buy transactions") ?: false)
    }

    @Test
    fun `processTaxReport should throw exception when sell transaction is not found`() = runBlocking {
        // Arrange
        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.FIFO
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns null // Not found

        // Act & Assert
        val exception = assertThrows(TaxReportProcessingException::class.java) {
            runBlocking {
                taxReportGenerator.processTaxReport(
                    TaxReportRequest(requestId, listOf(event))
                )
            }
        }

        // Check for exception cause rather than direct exception message
        val cause = exception.cause
        assertTrue(cause is IllegalArgumentException)
        assertTrue(cause?.message?.contains("Sell transaction $sellTxId not found") ?: false)
    }

    @Test
    fun `getAvailableBuyTransactionsBeforeSell should filter correctly`() = runBlocking {
        // Arrange
        val sellDate = baseDate
        val asset = btcUnit

        val validBuyTx = createMockTransaction(
            buyTx1Id,
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate // Before sell date
        )

        val invalidBuyTx1 = createMockTransaction(
            "invalid1",
            NormalizedTransactionType.BUY,
            timestamp = Date(baseDate.time + 1000) // After sell date
        )

        val invalidBuyTx2 = createMockTransaction(
            "invalid2",
            NormalizedTransactionType.BUY,
            timestamp = beforeBaseDate,
            filedWithIRS = true // Filed with IRS
        )

        coEvery {
            transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf(asset)
            )
        } returns listOf(validBuyTx, invalidBuyTx1, invalidBuyTx2)

        // Mock available amounts - only valid transaction has available amount
        every { transactionTracker.getAvailableAmount(validBuyTx) } returns ExchangeAmount(1.0, btcUnit)
        every { transactionTracker.getAvailableAmount(invalidBuyTx1) } returns ExchangeAmount(1.0, btcUnit)
        every { transactionTracker.getAvailableAmount(invalidBuyTx2) } returns ExchangeAmount(0.0, btcUnit)

        val event = TaxableEventParameters(
            sellId = sellTxId,
            taxTreatment = TaxTreatment.FIFO
        )

        val sellTransaction = createMockTransaction(
            sellTxId,
            NormalizedTransactionType.SELL,
            timestamp = sellDate
        )

        coEvery { transactionRepository.getTransactionById(sellTxId) } returns sellTransaction

        val expectedResult = mockTaxableEventResult(sellTxId)

        // Capture the buy transactions passed to calculateTaxableEvent
        val buyTxSlot = slot<List<NormalizedTransaction>>()

        every {
            taxCalculator.calculateTaxableEvent(
                eq(sellTransaction),
                capture(buyTxSlot),
                eq(transactionTracker)
            )
        } returns expectedResult

        // Act
        taxReportGenerator.processTaxReport(
            TaxReportRequest(requestId, listOf(event))
        )

        // Assert
        assertEquals(1, buyTxSlot.captured.size)
        assertEquals(validBuyTx.id, buyTxSlot.captured[0].id)

        // invalidBuyTx1 is filtered out due to date
        // invalidBuyTx2 is filtered out due to no available amount
    }

    /**
     * Helper method to create a transaction for testing
     */
    private fun createMockTransaction(
        id: String,
        type: NormalizedTransactionType,
        timestamp: Date = Date(),
        assetValueFiat: ExchangeAmount = ExchangeAmount(100.0, usdUnit),
        assetAmount: ExchangeAmount = ExchangeAmount(1.0, btcUnit),
        filedWithIRS: Boolean = false
    ): NormalizedTransaction {
        val transactionSource = io.mockk.mockk<TransactionSource>()

        return NormalizedTransaction(
            id = id,
            source = transactionSource,
            type = type,
            transactionAmountFiat = ExchangeAmount(100.0, usdUnit),
            fee = ExchangeAmount(10.0, usdUnit),
            assetAmount = assetAmount,
            assetValueFiat = assetValueFiat,
            timestamp = timestamp,
            timestampText = timestamp.toString(),
            address = "0xabc123",
            notes = "Test transaction",
            filedWithIRS = filedWithIRS
        )
    }

    /**
     * Helper method to create a mock taxable event result
     */
    private fun mockTaxableEventResult(
        sellTxId: String
    ): TaxableEventResult {
        return io.mockk.mockk {
            every { sellTransactionId } returns sellTxId
        }
    }
}
