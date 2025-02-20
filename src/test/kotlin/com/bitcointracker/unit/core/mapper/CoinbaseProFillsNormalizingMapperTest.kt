package com.bitcointracker.unit.core.mapper

import com.bitcointracker.core.mapper.CoinbaseProFillsNormalizingMapper
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseFillsSide
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseFillsTransaction
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.TransactionSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoinbaseProFillsNormalizingMapperTest {

    private lateinit var mapper: CoinbaseProFillsNormalizingMapper
    private val testDate = Date()

    @BeforeEach
    fun setup() {
        mapper = CoinbaseProFillsNormalizingMapper()
    }

    private fun createTestTransaction(
        tradeId: String = "123",
        side: CoinbaseFillsSide = CoinbaseFillsSide.BUY,
        size: ExchangeAmount = ExchangeAmount(1.0, "BTC"),
        price: ExchangeAmount = ExchangeAmount(50000.0, "USD"),
        fee: ExchangeAmount = ExchangeAmount(25.0, "USD"),
        total: ExchangeAmount = ExchangeAmount(50025.0, "USD")
    ): CoinbaseFillsTransaction {
        return CoinbaseFillsTransaction(
            portfolio = "default",
            tradeId = tradeId,
            product = "BTC-USD",
            side = side,
            createdAt = testDate,
            size = size,
            price = price,
            fee = fee,
            total = total
        )
    }

    @Nested
    inner class BuyTransactionTests {
        @Test
        fun `when mapping buy transaction, correctly sets type and source`() {
            // Arrange
            val transaction = createTestTransaction(side = CoinbaseFillsSide.BUY)

            // Act
            val normalized = mapper.normalizeTransaction(transaction)

            // Assert
            assertEquals(NormalizedTransactionType.BUY, normalized.type)
            assertEquals(TransactionSource.COINBASE_PRO_FILL, normalized.source)
        }

        @Test
        fun `when mapping buy transaction, preserves all amount values`() {
            // Arrange
            val transaction = createTestTransaction(
                size = ExchangeAmount(2.5, "BTC"),
                price = ExchangeAmount(45000.0, "USD"),
                fee = ExchangeAmount(50.0, "USD"),
                total = ExchangeAmount(112550.0, "USD")
            )

            // Act
            val normalized = mapper.normalizeTransaction(transaction)

            // Assert
            assertEquals(ExchangeAmount(2.5, "BTC"), normalized.assetAmount)
            assertEquals(ExchangeAmount(45000.0, "USD"), normalized.assetValueFiat)
            assertEquals(ExchangeAmount(50.0, "USD"), normalized.fee)
            assertEquals(ExchangeAmount(112550.0, "USD"), normalized.transactionAmountFiat)
        }
    }

    @Nested
    inner class SellTransactionTests {
        @Test
        fun `when mapping sell transaction, correctly sets type and source`() {
            // Arrange
            val transaction = createTestTransaction(side = CoinbaseFillsSide.SELL)

            // Act
            val normalized = mapper.normalizeTransaction(transaction)

            // Assert
            assertEquals(NormalizedTransactionType.SELL, normalized.type)
            assertEquals(TransactionSource.COINBASE_PRO_FILL, normalized.source)
        }

        @Test
        fun `when mapping sell transaction, preserves all amount values`() {
            // Arrange
            val transaction = createTestTransaction(
                side = CoinbaseFillsSide.SELL,
                size = ExchangeAmount(1.5, "BTC"),
                price = ExchangeAmount(48000.0, "USD"),
                fee = ExchangeAmount(35.0, "USD"),
                total = ExchangeAmount(71965.0, "USD")
            )

            // Act
            val normalized = mapper.normalizeTransaction(transaction)

            // Assert
            assertEquals(ExchangeAmount(1.5, "BTC"), normalized.assetAmount)
            assertEquals(ExchangeAmount(48000.0, "USD"), normalized.assetValueFiat)
            assertEquals(ExchangeAmount(35.0, "USD"), normalized.fee)
            assertEquals(ExchangeAmount(71965.0, "USD"), normalized.transactionAmountFiat)
        }
    }

    @Nested
    inner class MetadataTests {
        @Test
        fun `when mapping transaction, correctly sets metadata fields`() {
            // Arrange
            val tradeId = "test-trade-123"
            val transaction = createTestTransaction(tradeId = tradeId)

            // Act
            val normalized = mapper.normalizeTransaction(transaction)

            // Assert
            assertEquals(tradeId, normalized.id)
            assertEquals(testDate, normalized.timestamp)
            assertEquals(testDate.toString(), normalized.timestampText)
        }

        @Test
        fun `when mapping unfiled transaction, sets filedWithIRS to false`() {
            // Arrange
            val tradeId = "unfiled-transaction-id"
            val transaction = createTestTransaction(tradeId = tradeId)

            // Act
            val normalized = mapper.normalizeTransaction(transaction)

            // Assert
            assertFalse(normalized.filedWithIRS)
        }
    }
}