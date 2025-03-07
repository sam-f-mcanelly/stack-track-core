package com.bitcointracker.unit.core.parser.exchange.mapper

import com.bitcointracker.core.parser.exchange.mapper.CoinbaseStandardTransactionNormalizingMapper
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseStandardTransaction
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseTransactionType
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import java.util.Date
import kotlin.test.assertEquals

class CoinbaseStandardTransactionNormalizingMapperTest {

    private lateinit var mapper: CoinbaseStandardTransactionNormalizingMapper
    private val testDate = Date()
    private val testId = "test-transaction-id"
    private val testNotes = "test notes"

    @BeforeEach
    fun setup() {
        mapper = CoinbaseStandardTransactionNormalizingMapper()
    }

    private fun createTestTransaction(
        type: CoinbaseTransactionType,
        quantity: Double = 1.0,
        assetValue: Double = 100.0,
        transactionAmount: Double = 100.0,
        fee: Double = 1.0
    ): CoinbaseStandardTransaction = CoinbaseStandardTransaction(
        id = testId,
        timestamp = testDate,
        type = type,
        quantityTransacted = ExchangeAmount(quantity, "BTC"),
        assetValue = ExchangeAmount(assetValue, "USD"),
        transactionAmount = ExchangeAmount(transactionAmount, "USD"),
        fee = ExchangeAmount(fee, "USD"),
        notes = testNotes
    )

    @Nested
    inner class TransactionTypeMapping {
        @Test
        fun `should map DEPOSIT to DEPOSIT`() {
            val transaction = createTestTransaction(CoinbaseTransactionType.DEPOSIT)
            val normalized = mapper.normalizeTransaction(transaction)
            assertEquals(NormalizedTransactionType.DEPOSIT, normalized.type)
        }

        @Test
        fun `should map PRO_WITHDRAWAL to WITHDRAWAL`() {
            val transaction = createTestTransaction(CoinbaseTransactionType.PRO_WITHDRAWAL)
            val normalized = mapper.normalizeTransaction(transaction)
            assertEquals(NormalizedTransactionType.WITHDRAWAL, normalized.type)
        }

        @Test
        fun `should map RECEIVE to DEPOSIT`() {
            val transaction = createTestTransaction(CoinbaseTransactionType.RECEIVE)
            val normalized = mapper.normalizeTransaction(transaction)
            assertEquals(NormalizedTransactionType.DEPOSIT, normalized.type)
        }

        @Test
        fun `should map SEND to WITHDRAWAL`() {
            val transaction = createTestTransaction(CoinbaseTransactionType.SEND)
            val normalized = mapper.normalizeTransaction(transaction)
            assertEquals(NormalizedTransactionType.WITHDRAWAL, normalized.type)
        }

        @Test
        fun `should map SELL to SELL with all fields`() {
            val transaction = createTestTransaction(
                type = CoinbaseTransactionType.SELL,
                quantity = 2.5,
                assetValue = 125000.0,
                transactionAmount = 124750.0,
                fee = 250.0
            )
            val normalized = mapper.normalizeTransaction(transaction)

            with(normalized) {
                assertEquals(NormalizedTransactionType.SELL, type)
                assertEquals(TransactionSource.COINBASE_STANDARD, source)
                assertEquals(testId, id)
                assertEquals(testDate, timestamp)
                assertEquals(testDate.toString(), timestampText)
                assertEquals(testNotes, notes)
                assertEquals("", address)
                assertEquals(false, filedWithIRS)

                // Verify amounts
                assertEquals(2.5, assetAmount.amount)
                assertEquals("BTC", assetAmount.unit)
                assertEquals(125000.0, assetValueFiat.amount)
                assertEquals("USD", assetValueFiat.unit)
                assertEquals(124750.0, transactionAmountFiat.amount)
                assertEquals("USD", transactionAmountFiat.unit)
                assertEquals(250.0, fee.amount)
                assertEquals("USD", fee.unit)
            }
        }

        @Test
        fun `should map BUY to BUY with all fields`() {
            val transaction = createTestTransaction(
                type = CoinbaseTransactionType.BUY,
                quantity = 1.5,
                assetValue = 75000.0,
                transactionAmount = 75150.0,
                fee = 150.0
            )
            val normalized = mapper.normalizeTransaction(transaction)

            with(normalized) {
                assertEquals(NormalizedTransactionType.BUY, type)
                assertEquals(TransactionSource.COINBASE_STANDARD, source)
                assertEquals(testId, id)
                assertEquals(testDate, timestamp)
                assertEquals(testDate.toString(), timestampText)
                assertEquals(testNotes, notes)
                assertEquals("", address)
                assertEquals(false, filedWithIRS)

                // Verify amounts
                assertEquals(1.5, assetAmount.amount)
                assertEquals("BTC", assetAmount.unit)
                assertEquals(75000.0, assetValueFiat.amount)
                assertEquals("USD", assetValueFiat.unit)
                assertEquals(75150.0, transactionAmountFiat.amount)
                assertEquals("USD", transactionAmountFiat.unit)
                assertEquals(150.0, fee.amount)
                assertEquals("USD", fee.unit)
            }
        }
    }

    @Nested
    inner class TransactionProperties {
        @Test
        fun `should correctly map basic transaction properties`() {
            val transaction = createTestTransaction(CoinbaseTransactionType.BUY)
            val normalized = mapper.normalizeTransaction(transaction)

            assertEquals(testId, normalized.id)
            assertEquals(TransactionSource.COINBASE_STANDARD, normalized.source)
            assertEquals(testDate, normalized.timestamp)
            assertEquals(testDate.toString(), normalized.timestampText)
            assertEquals(testNotes, normalized.notes)
        }

        @Test
        fun `should use absolute values for amounts`() {
            val transaction = createTestTransaction(
                type = CoinbaseTransactionType.BUY,
                quantity = -1.0,
                transactionAmount = -100.0,
                fee = -1.0
            )
            val normalized = mapper.normalizeTransaction(transaction)

            assertEquals(1.0, normalized.assetAmount.amount)
            assertEquals(100.0, normalized.transactionAmountFiat.amount)
            assertEquals(1.0, normalized.fee.amount)
        }

        @Test
        fun `should preserve currency units`() {
            val transaction = createTestTransaction(CoinbaseTransactionType.BUY)
            val normalized = mapper.normalizeTransaction(transaction)

            assertEquals("BTC", normalized.assetAmount.unit)
            assertEquals("USD", normalized.transactionAmountFiat.unit)
            assertEquals("USD", normalized.fee.unit)
        }
    }

    @Test
    fun `should handle empty notes`() {
        val transaction = createTestTransaction(CoinbaseTransactionType.BUY).copy(notes = "")
        val normalized = mapper.normalizeTransaction(transaction)
        assertEquals("", normalized.notes)
    }

    @Test
    fun `should handle zero amounts`() {
        val transaction = createTestTransaction(
            type = CoinbaseTransactionType.BUY,
            quantity = 0.0,
            assetValue = 0.0,
            transactionAmount = 0.0,
            fee = 0.0
        )
        val normalized = mapper.normalizeTransaction(transaction)

        assertEquals(0.0, normalized.assetAmount.amount)
        assertEquals(0.0, normalized.assetValueFiat.amount)
        assertEquals(0.0, normalized.transactionAmountFiat.amount)
        assertEquals(0.0, normalized.fee.amount)
    }
}