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
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 2.0,
                fiatAmount = 100000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            val buyTx1 = createBuyTransaction(
                id = "buy-1",
                assetAmount = 1.0,
                fiatAmount = 40000.0, // The USD spent
                date = buyDate1
            )

            val buyTx2 = createBuyTransaction(
                id = "buy-2",
                assetAmount = 1.0,
                fiatAmount = 45000.0, // The USD spent
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf("BTC"),
            )
            } returns listOf(
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
                // Now validating against the correct proceeds amount
                assertEquals(ExchangeAmount(100000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(85000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(15000.0, "USD"), gain)
                assertEquals(2, usedBuyTransactions.size)
                assertEquals("buy-2", usedBuyTransactions[0].transactionId)
                assertEquals("buy-1", usedBuyTransactions[1].transactionId)
                assertEquals(ExchangeAmount(0.0, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(0.0, "USD"), uncoveredSellValue)
            }
        }

        @Test
        fun `should handle insufficient buy transactions for FIFO by using zero cost basis for uncovered portion`() = runTest {
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 2.0,
                fiatAmount = 100000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            val buyTx = createBuyTransaction(
                id = "buy-1",
                assetAmount = 1.0,
                fiatAmount = 40000.0, // The USD spent
                date = buyDate1
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf("BTC"),
            )
            } returns listOf(buyTx)

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
            with(result.results[0]) {
                assertEquals("sell-1", sellTransactionId)
                assertEquals(ExchangeAmount(100000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(40000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(60000.0, "USD"), gain)
                assertEquals(1, usedBuyTransactions.size)
                assertEquals("buy-1", usedBuyTransactions[0].transactionId)
                assertEquals(ExchangeAmount(1.0, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(50000.0, "USD"), uncoveredSellValue)
            }
        }
    }

    @Nested
    inner class LIFO {
        @Test
        fun `should process LIFO tax treatment correctly`() = runTest {
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 1.5,
                fiatAmount = 75000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            val buyTx1 = createBuyTransaction(
                id = "buy-1",
                assetAmount = 1.0,
                fiatAmount = 40000.0, // The USD spent
                date = buyDate1
            )

            val buyTx2 = createBuyTransaction(
                id = "buy-2",
                assetAmount = 1.0,
                fiatAmount = 45000.0, // The USD spent
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf("BTC"),
            )
            } returns listOf(
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
                assertEquals(ExchangeAmount(75000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(62500.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(12500.0, "USD"), gain)
                assertEquals(ExchangeAmount(0.0, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(0.0, "USD"), uncoveredSellValue)
            }
        }

        @Test
        fun `should handle insufficient buy transactions for LIFO by using zero cost basis for uncovered portion`() = runTest {
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 2.5,
                fiatAmount = 125000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            val buyTx1 = createBuyTransaction(
                id = "buy-1",
                assetAmount = 1.0,
                fiatAmount = 40000.0, // The USD spent
                date = buyDate1
            )

            val buyTx2 = createBuyTransaction(
                id = "buy-2",
                assetAmount = 1.0,
                fiatAmount = 45000.0, // The USD spent
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf("BTC"),
            )
            } returns listOf(
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
                assertEquals("sell-1", sellTransactionId)
                assertEquals(ExchangeAmount(125000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(85000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(40000.0, "USD"), gain)
                assertEquals(2, usedBuyTransactions.size)
                assertEquals(ExchangeAmount(0.5, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(25000.0, "USD"), uncoveredSellValue)
            }
        }
    }

    @Nested
    inner class CustomMatching {
        @Test
        fun `should process custom tax treatment with valid buy transactions`() = runTest {
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 1.0,
                fiatAmount = 50000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            val buyTx = createBuyTransaction(
                id = "buy-1",
                assetAmount = 1.0,
                fiatAmount = 40000.0, // The USD spent
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
                assertEquals(ExchangeAmount(50000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(40000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(10000.0, "USD"), gain)
                assertEquals(ExchangeAmount(0.0, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(0.0, "USD"), uncoveredSellValue)
            }
        }

        @Test
        fun `should handle insufficient buy transactions for custom matching by using zero cost basis for uncovered portion`() = runTest {
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 1.5,
                fiatAmount = 75000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            val buyTx = createBuyTransaction(
                id = "buy-1",
                assetAmount = 1.0,
                fiatAmount = 40000.0, // The USD spent
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
                assertEquals(ExchangeAmount(75000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(40000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(35000.0, "USD"), gain)
                assertEquals(ExchangeAmount(0.5, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(25000.0, "USD"), uncoveredSellValue)
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
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 1.0,
                fiatAmount = 50000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            // Creating buy transactions with different cost basis values
            val buyTx1 = createBuyTransaction(
                id = "buy-1",
                assetAmount = 0.5,
                fiatAmount = 15000.0, // Higher cost per coin (30000 per BTC)
                date = buyDate1
            )

            val buyTx2 = createBuyTransaction(
                id = "buy-2",
                assetAmount = 0.5,
                fiatAmount = 10000.0, // Lower cost per coin (20000 per BTC)
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf("BTC"),
            )
            } returns listOf(
                buyTx1,
                buyTx2
            )

            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.MAX_PROFIT
                    )
                )
            )

            // Act
            val result = processor.processTaxReport(request)

            // Assert - should use lower cost basis first to maximize profit
            with(result.results[0]) {
                assertEquals(ExchangeAmount(50000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(25000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(25000.0, "USD"), gain)
                assertEquals(2, usedBuyTransactions.size)
                // Should use the lower cost basis transaction first
                assertEquals("buy-2", usedBuyTransactions[0].transactionId)
                assertEquals("buy-1", usedBuyTransactions[1].transactionId)
                assertEquals(ExchangeAmount(0.0, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(0.0, "USD"), uncoveredSellValue)
            }
        }

        @Test
        fun `should process MIN_PROFIT strategy correctly`() = runTest {
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 1.0,
                fiatAmount = 50000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            // Creating buy transactions with different cost basis values
            val buyTx1 = createBuyTransaction(
                id = "buy-1",
                assetAmount = 0.5,
                fiatAmount = 15000.0, // Higher cost per coin (30000 per BTC)
                date = buyDate1
            )

            val buyTx2 = createBuyTransaction(
                id = "buy-2",
                assetAmount = 0.5,
                fiatAmount = 10000.0, // Lower cost per coin (20000 per BTC)
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf("BTC"),
            )
            } returns listOf(
                buyTx1,
                buyTx2
            )

            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.MIN_PROFIT
                    )
                )
            )

            // Act
            val result = processor.processTaxReport(request)

            // Assert - should use higher cost basis first to minimize profit
            with(result.results[0]) {
                assertEquals(ExchangeAmount(50000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(25000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(25000.0, "USD"), gain)
                assertEquals(2, usedBuyTransactions.size)
                // Should use the higher cost basis transaction first
                assertEquals("buy-1", usedBuyTransactions[0].transactionId)
                assertEquals("buy-2", usedBuyTransactions[1].transactionId)
                assertEquals(ExchangeAmount(0.0, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(0.0, "USD"), uncoveredSellValue)
            }
        }

        @Test
        fun `should handle insufficient buy transactions for MAX_PROFIT strategy`() = runTest {
            // Arrange
            val sellTx = createSellTransaction(
                id = "sell-1",
                assetAmount = 1.5,
                fiatAmount = 75000.0, // The actual USD received
                price = 50000.0, // Price per BTC
                date = baseDate
            )

            val buyTx1 = createBuyTransaction(
                id = "buy-1",
                assetAmount = 0.5,
                fiatAmount = 15000.0,
                date = buyDate1
            )

            val buyTx2 = createBuyTransaction(
                id = "buy-2",
                assetAmount = 0.5,
                fiatAmount = 10000.0,
                date = buyDate2
            )

            coEvery { transactionRepository.getTransactionById("sell-1") } returns sellTx
            coEvery { transactionRepository.getFilteredTransactions(
                types = listOf(NormalizedTransactionType.BUY),
                assets = listOf("BTC"),
            )
            } returns listOf(
                buyTx1,
                buyTx2
            )

            val request = TaxReportRequest(
                requestId = "request-1",
                taxableEvents = listOf(
                    TaxableEventParameters(
                        sellId = "sell-1",
                        taxTreatment = TaxTreatment.MAX_PROFIT
                    )
                )
            )

            // Act
            val result = processor.processTaxReport(request)

            // Assert
            with(result.results[0]) {
                assertEquals(ExchangeAmount(75000.0, "USD"), proceeds)
                assertEquals(ExchangeAmount(25000.0, "USD"), costBasis)
                assertEquals(ExchangeAmount(50000.0, "USD"), gain)
                assertEquals(2, usedBuyTransactions.size)
                assertEquals(ExchangeAmount(0.5, "BTC"), uncoveredSellAmount)
                assertEquals(ExchangeAmount(25000.0, "USD"), uncoveredSellValue)
            }
        }
    }

    // New helper methods to create the right types of transactions
    private fun createBuyTransaction(
        id: String,
        assetAmount: Double,
        fiatAmount: Double,
        date: Date
    ): NormalizedTransaction {
        return NormalizedTransaction(
            id = id,
            type = NormalizedTransactionType.BUY,
            source = TransactionSource.COINBASE_STANDARD,
            transactionAmountFiat = ExchangeAmount(fiatAmount, "USD"), // Amount spent
            fee = ExchangeAmount(0.0, "USD"),
            assetAmount = ExchangeAmount(assetAmount, "BTC"),
            assetValueFiat = ExchangeAmount(fiatAmount, "USD"), // For buys, this is same as transactionAmountFiat
            timestamp = date,
            timestampText = date.toString(),
            notes = "",
            filedWithIRS = false,
        )
    }

    private fun createSellTransaction(
        id: String,
        assetAmount: Double,
        fiatAmount: Double, // Actual USD received
        price: Double,      // Price per BTC
        date: Date
    ): NormalizedTransaction {
        return NormalizedTransaction(
            id = id,
            type = NormalizedTransactionType.SELL,
            source = TransactionSource.COINBASE_STANDARD,
            transactionAmountFiat = ExchangeAmount(fiatAmount, "USD"), // Amount received
            fee = ExchangeAmount(0.0, "USD"),
            assetAmount = ExchangeAmount(assetAmount, "BTC"),
            assetValueFiat = ExchangeAmount(price, "USD"), // Price per unit, not total value
            timestamp = date,
            timestampText = date.toString(),
            notes = "",
            filedWithIRS = false,
        )
    }
}
