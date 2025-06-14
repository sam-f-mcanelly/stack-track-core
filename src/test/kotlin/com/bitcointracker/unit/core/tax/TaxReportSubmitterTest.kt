package com.bitcointracker.unit.core.tax

import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.core.tax.TaxReportSubmitter
import com.bitcointracker.model.api.tax.TaxReportResult
import com.bitcointracker.model.api.tax.TaxType
import com.bitcointracker.model.api.tax.TaxableEventResult
import com.bitcointracker.model.api.tax.UsedBuyTransaction
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class TaxReportSubmitterTest {
    private val logger = LoggerFactory.getLogger(TaxReportSubmitterTest::class.java)

    @MockK
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var submitter: TaxReportSubmitter
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        logger.info("Setting up test")
        submitter = TaxReportSubmitter(transactionRepository, testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        logger.info("Starting test teardown")
        try {
            submitter.shutdown()
            logger.info("Shutdown completed successfully")
        } catch (e: Exception) {
            logger.warn("Shutdown warning: ${e.message}")
        } finally {
            logger.info("Test teardown completed")
        }
    }

    @Nested
    inner class SubmissionProcessing {
        @Test
        fun `should process submission and mark transactions as filed`() = runTest(testDispatcher) {
            logger.info("Starting submission processing test")

            // Arrange
            val sellTx = createTransaction("sell-1", NormalizedTransactionType.SELL)
            val buyTx1 = createTransaction("buy-1", NormalizedTransactionType.BUY)
            val buyTx2 = createTransaction("buy-2", NormalizedTransactionType.BUY)

            logger.info("Setting up mock responses")
            coEvery { transactionRepository.getTransactionById(any()) } answers {
                logger.info("Getting transaction: ${firstArg<String>()}")
                when (firstArg<String>()) {
                    "sell-1" -> sellTx
                    "buy-1" -> buyTx1
                    "buy-2" -> buyTx2
                    else -> null
                }
            }

            coEvery { transactionRepository.updateTransaction(any()) } answers {
                logger.info("Updating transaction: ${firstArg<NormalizedTransaction>().id}")
                Unit
            }

            val result = TaxReportResult(
                requestId = "request-1",
                results = listOf(
                    TaxableEventResult(
                        sellTransactionId = "sell-1",
                        proceeds = ExchangeAmount(100000.0, "USD"),
                        costBasis = ExchangeAmount(90000.0, "USD"),
                        gain = ExchangeAmount(10000.0, "USD"),
                        sellTransaction = sellTx,
                        usedBuyTransactions = listOf(
                            UsedBuyTransaction(
                                "buy-1",
                                ExchangeAmount(1.0, "BTC"),
                                ExchangeAmount(45000.0, "USD"),
                                TaxType.LONG_TERM, buyTx1
                            ),
                            UsedBuyTransaction(
                                "buy-2",
                                ExchangeAmount(1.0, "BTC"),
                                ExchangeAmount(45000.0, "USD"),
                                TaxType.LONG_TERM,
                                buyTx2
                            ),
                        )
                    )
                )
            )

            // Act
            logger.info("Submitting tax report")
            val submissionId = submitter.submitAsync(result)
            logger.info("Submission ID: $submissionId")
            val statusFlow = submitter.getSubmissionStatus(submissionId)
            assertNotNull(statusFlow)

            logger.info("Advancing coroutines")
            val finalStatus = statusFlow.filter { status ->
                logger.info("Current status: $status")
                status == TaxReportSubmitter.SubmissionStatus.Completed
            }.first()
            logger.info("Final status received: $finalStatus")

            // Assert
            logger.info("Verifying repository calls")
            coVerify(exactly = 3) { transactionRepository.getTransactionById(any()) }
            coVerify(exactly = 3) { transactionRepository.updateTransaction(any()) }

            coVerify {
                transactionRepository.updateTransaction(match { it.id == "sell-1" && it.filedWithIRS })
                transactionRepository.updateTransaction(match { it.id == "buy-1" && it.filedWithIRS })
                transactionRepository.updateTransaction(match { it.id == "buy-2" && it.filedWithIRS })
            }
            logger.info("Test completed successfully")
        }

        @Test
        fun `should handle missing transactions gracefully`() = runTest(testDispatcher) {
            logger.info("Starting missing transactions test")

            val sellTx = createTransaction("sell-1", NormalizedTransactionType.SELL)
            val buyTx = createTransaction("buy-1", NormalizedTransactionType.BUY)
            coEvery { transactionRepository.getTransactionById(any()) } answers {
                logger.info("Getting transaction (returning null): ${firstArg<String>()}")
                null
            }

            val result = TaxReportResult(
                requestId = "request-1",
                results = listOf(
                    TaxableEventResult(
                        sellTransactionId = "sell-1",
                        proceeds = ExchangeAmount(100000.0, "USD"),
                        costBasis = ExchangeAmount(90000.0, "USD"),
                        gain = ExchangeAmount(10000.0, "USD"),
                        sellTransaction = sellTx,
                        usedBuyTransactions = listOf(
                            UsedBuyTransaction(
                                "buy-1",
                                ExchangeAmount(1.0, "BTC"),
                                ExchangeAmount(45000.0, "USD"),
                                TaxType.LONG_TERM, buyTx
                            ),
                        )
                    )
                )
            )

            logger.info("Submitting tax report")
            val submissionId = submitter.submitAsync(result)
            val statusFlow = submitter.getSubmissionStatus(submissionId)
            assertNotNull(statusFlow)

            val finalStatus = statusFlow.filter { status ->
                logger.info("Current status: $status")
                status is TaxReportSubmitter.SubmissionStatus.Failed
            }.first()
            logger.info("Final status received: $finalStatus")

            coVerify(exactly = 1) { transactionRepository.getTransactionById(any()) }
            coVerify(exactly = 0) { transactionRepository.updateTransaction(any()) }
            logger.info("Test completed successfully")
        }
    }

    @Nested
    inner class ConcurrencyHandling {
        @Test
        fun `should process submissions sequentially`() = runTest(testDispatcher) {
            logger.info("Starting sequential processing test")

            val processingOrder = mutableListOf<String>()

            coEvery { transactionRepository.getTransactionById(any()) } answers {
                val id = firstArg<String>()
                logger.info("Getting transaction: $id")
                processingOrder.add(id)
                createTransaction(id, if (id.startsWith("sell")) NormalizedTransactionType.SELL else NormalizedTransactionType.BUY)
            }

            coEvery { transactionRepository.updateTransaction(any()) } answers {
                logger.info("Updating transaction: ${firstArg<NormalizedTransaction>().id}")
                Unit
            }

            // Create two submissions
            val results = listOf(
                createTestResult("sell-1", listOf("buy-1")),
                createTestResult("sell-2", listOf("buy-2"))
            )

            logger.info("Submitting multiple tax reports")
            val submissionIds = results.map { submitter.submitAsync(it) }
            val statusFlows = submissionIds.map {
                submitter.getSubmissionStatus(it) ?: error("Status flow not found")
            }

            delay(5000)
            logger.info("Checking final statuses")
            statusFlows.forEach { flow ->
                assertEquals(TaxReportSubmitter.SubmissionStatus.Completed, flow.first())
            }


            logger.info("Verifying processing order")
            assertTrue(processingOrder.indexOf("sell-1") < processingOrder.indexOf("sell-2"))
            assertTrue(processingOrder.indexOf("buy-1") < processingOrder.indexOf("buy-2"))
            logger.info("Test completed successfully")
        }
    }

    // Helper functions remain the same
    private fun createTestResult(sellId: String, buyIds: List<String>) = TaxReportResult(
        requestId = "request-${sellId}",
        results = listOf(
            TaxableEventResult(
                sellTransactionId = sellId,
                proceeds = ExchangeAmount(100000.0, "USD"),
                costBasis = ExchangeAmount(90000.0, "USD"),
                gain = ExchangeAmount(10000.0, "USD"),
                sellTransaction = createTransaction("sell-1", NormalizedTransactionType.SELL),
                usedBuyTransactions = buyIds.map {
                    UsedBuyTransaction(
                        it,
                        ExchangeAmount(1.0, "BTC"),
                        ExchangeAmount(90000.0 / buyIds.size, "USD"),
                        TaxType.LONG_TERM,
                        createTransaction("buy-$it", NormalizedTransactionType.BUY)
                    )
                }
            )
        )
    )

    private fun createTransaction(
        id: String,
        type: NormalizedTransactionType,
        filedWithIRS: Boolean = false
    ): NormalizedTransaction = NormalizedTransaction(
        id = id,
        type = type,
        source = TransactionSource.COINBASE_STANDARD,
        transactionAmountFiat = ExchangeAmount(100000.0, "USD"),
        fee = ExchangeAmount(0.0, "USD"),
        assetAmount = ExchangeAmount(1.0, "BTC"),
        assetValueFiat = ExchangeAmount(100000.0, "USD"),
        timestamp = Instant.now(),
        timestampText = Instant.now().toString(),
        filedWithIRS = filedWithIRS
    )
}