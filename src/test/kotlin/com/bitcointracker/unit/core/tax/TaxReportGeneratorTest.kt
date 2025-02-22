package com.bitcointracker.unit.core.tax

import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.core.tax.TaxReportGenerator
import com.bitcointracker.model.api.exception.TaxReportProcessingException
import com.bitcointracker.model.api.tax.TaxReportRequest
import com.bitcointracker.model.api.tax.TaxTreatment
import com.bitcointracker.model.api.tax.TaxableEventParameters
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.TransactionSource
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Date
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class TaxReportGeneratorTest {

    @MockK
    private lateinit var transactionRepository: TransactionRepository

    @InjectMockKs
    private lateinit var processor: TaxReportGenerator

    private val baseDate = Date(1700000000000) // Fixed date for testing
    private val buyDate1 = Date(1699900000000) // 1 day earlier
    private val buyDate2 = Date(1699800000000) // 2 days earlier

    @Nested
    inner class FIFO {
        @Test
        fun `should process FIFO tax treatment with sufficient buy transactions`() = runTest {
            // Arrange
            val sellTx = createTransaction(
                id = "sell-1",
                type = NormalizedTransactionType.SELL,
                amount = 2.0,
                value = 100000.0,
                date = baseDate
            )

            val buyTx1 = createTransaction(
                id = "buy-1",
                type = NormalizedTransactionType.BUY,
                amount = 1.0,
                value = 40000.0,
                date = buyDate1
            )

            val buyTx2 = createTransaction(
                id = "buy-2",
                type = NormalizedTransactionType.BUY,
                amount = 1.0,
                value = 45000.0,
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getTransactionsByType(NormalizedTransactionType.BUY) } returns listOf(
                buyTx1,
                buyTx2
            )

            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.FIFO
                    )
                )
            )

            // Act
            val result = processor.processTaxReport(request)

            // Assert
            assertEquals(1, result.results.size)
            with(result.results[0]) {
                assertEquals("sell-1", sellTransactionId)
                assertEquals(100000.0, proceeds)
                assertEquals(85000.0, costBasis)
                assertEquals(15000.0, gain)
                assertEquals(2, usedBuyTransactions.size)
                assertEquals("buy-2", usedBuyTransactions[0].transactionId)
                assertEquals("buy-1", usedBuyTransactions[1].transactionId)
            }
        }

        @Test
        fun `should throw exception when insufficient buy transactions for FIFO`() = runTest {
            // Arrange
            val sellTx = createTransaction(
                id = "sell-1",
                type = NormalizedTransactionType.SELL,
                amount = 2.0,
                value = 100000.0,
                date = baseDate
            )

            val buyTx = createTransaction(
                id = "buy-1",
                type = NormalizedTransactionType.BUY,
                amount = 1.0,
                value = 40000.0,
                date = buyDate1
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getTransactionsByType(NormalizedTransactionType.BUY) } returns listOf(buyTx)

            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.FIFO
                    )
                )
            )

            // Act & Assert
            assertThrows<TaxReportProcessingException> {
                runBlocking { processor.processTaxReport(request) }
            }
        }
    }

    @Nested
    inner class LIFO {
        @Test
        fun `should process LIFO tax treatment correctly`() = runTest {
            // Arrange
            val sellTx = createTransaction(
                id = "sell-1",
                type = NormalizedTransactionType.SELL,
                amount = 1.5,
                value = 75000.0,
                date = baseDate
            )

            val buyTx1 = createTransaction(
                id = "buy-1",
                type = NormalizedTransactionType.BUY,
                amount = 1.0,
                value = 40000.0,
                date = buyDate1
            )

            val buyTx2 = createTransaction(
                id = "buy-2",
                type = NormalizedTransactionType.BUY,
                amount = 1.0,
                value = 45000.0,
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getTransactionsByType(NormalizedTransactionType.BUY) } returns listOf(
                buyTx1,
                buyTx2
            )

            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.LIFO
                    )
                )
            )

            // Act
            val result = processor.processTaxReport(request)

            // Assert
            with(result.results[0]) {
                assertEquals(75000.0, proceeds)
                assertEquals(62500.0, costBasis)
                assertEquals(12500.0, gain)
            }
        }
    }

    @Nested
    inner class CustomMatching {
        @Test
        fun `should process custom tax treatment with valid buy transactions`() = runTest {
            // Arrange
            val sellTx = createTransaction(
                id = "sell-1",
                type = NormalizedTransactionType.SELL,
                amount = 1.0,
                value = 50000.0,
                date = baseDate
            )

            val buyTx = createTransaction(
                id = "buy-1",
                type = NormalizedTransactionType.BUY,
                amount = 1.0,
                value = 40000.0,
                date = buyDate1
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getTransactionById("buy-1") } returns buyTx

            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.CUSTOM,
                        buyTransactionIds = listOf("buy-1")
                    )
                )
            )

            // Act
            val result = processor.processTaxReport(request)

            // Assert
            with(result.results[0]) {
                assertEquals(50000.0, proceeds)
                assertEquals(40000.0, costBasis)
                assertEquals(10000.0, gain)
            }
        }

        @Test
        fun `should throw exception for custom treatment without buy IDs`() = runTest {
            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.CUSTOM
                    )
                )
            )

            assertThrows<TaxReportProcessingException> {
                processor.processTaxReport(request)
            }
        }
    }

    @Nested
    inner class ProfitOptimization {
        @Test
        fun `should process MAX_PROFIT strategy correctly`() = runTest {
            // Arrange and implementation similar to other tests
            // Should verify that highest profit transactions are selected first
        }

        @Test
        fun `should process MIN_PROFIT strategy correctly`() = runTest {
            // Arrange and implementation similar to other tests
            // Should verify that lowest profit transactions are selected first
        }
    }

    private fun createTransaction(
        id: String,
        type: NormalizedTransactionType,
        amount: Double,
        value: Double,
        date: Date
    ): NormalizedTransaction {
        return NormalizedTransaction(
            id = id,
            type = type,
            source = TransactionSource.COINBASE_STANDARD,
            transactionAmountFiat = ExchangeAmount(value, "USD"),
            fee = ExchangeAmount(0.0, "USD"),
            assetAmount = ExchangeAmount(amount, "BTC"),
            assetValueFiat = ExchangeAmount(value, "USD"),
            timestamp = date,
            timestampText = date.toString(),
            notes = ""
        )
    }
}