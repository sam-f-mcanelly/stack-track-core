package com.bitcointracker.unit.core.cache

import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionMetadataCacheTest {

    private lateinit var cache: TransactionMetadataCache
    private val testDate = Instant.now()

    @BeforeEach
    fun setup() {
        cache = TransactionMetadataCache()
    }

    private fun createTestTransaction(
        id: String,
        type: NormalizedTransactionType,
        assetAmount: ExchangeAmount
    ): NormalizedTransaction {
        return NormalizedTransaction(
            id = id,
            source = TransactionSource.COINBASE_PRO_FILL,
            type = type,
            transactionAmountFiat = ExchangeAmount(100.0, "USD"),
            fee = ExchangeAmount(1.0, "USD"),
            assetAmount = assetAmount,
            assetValueFiat = ExchangeAmount(100.0, "USD"),
            timestamp = testDate,
            timestampText = testDate.toString()
        )
    }

    @Nested
    inner class UpdateTests {
        @Test
        fun `when updating with buy transactions, correctly accumulates amounts`() {
            // Arrange
            val transactions = listOf(
                createTestTransaction("1", NormalizedTransactionType.BUY, ExchangeAmount(1.0, "BTC")),
                createTestTransaction("2", NormalizedTransactionType.BUY, ExchangeAmount(2.0, "BTC"))
            )

            // Act
            cache.update(transactions)

            // Assert
            assertEquals(ExchangeAmount(3.0, "BTC"), cache.getAssetAmount("BTC"))
            assertEquals(2, cache.transactionCount)
        }

        @Test
        fun `when updating with sell transactions, correctly decrements amounts`() {
            // Arrange
            val transactions = listOf(
                createTestTransaction("1", NormalizedTransactionType.BUY, ExchangeAmount(3.0, "BTC")),
                createTestTransaction("2", NormalizedTransactionType.SELL, ExchangeAmount(1.0, "BTC"))
            )

            // Act
            cache.update(transactions)

            // Assert
            assertEquals(ExchangeAmount(2.0, "BTC"), cache.getAssetAmount("BTC"))
        }

        @Test
        fun `when updating with multiple assets, tracks each separately`() {
            // Arrange
            val transactions = listOf(
                createTestTransaction("1", NormalizedTransactionType.BUY, ExchangeAmount(1.0, "BTC")),
                createTestTransaction("2", NormalizedTransactionType.BUY, ExchangeAmount(100.0, "ETH"))
            )

            // Act
            cache.update(transactions)

            // Assert
            assertEquals(ExchangeAmount(1.0, "BTC"), cache.getAssetAmount("BTC"))
            assertEquals(ExchangeAmount(100.0, "ETH"), cache.getAssetAmount("ETH"))
        }

        @Test
        fun `when updating with invalid asset units, handles error gracefully`() {
            // Arrange
            val transactions = listOf(
                createTestTransaction("1", NormalizedTransactionType.BUY, ExchangeAmount(1.0, "BTC")),
                createTestTransaction("2", NormalizedTransactionType.BUY, ExchangeAmount(1.0, "")), // Invalid unit
                createTestTransaction("3", NormalizedTransactionType.BUY, ExchangeAmount(2.0, "BTC"))
            )

            // Act
            cache.update(transactions)

            // Assert
            assertEquals(ExchangeAmount(3.0, "BTC"), cache.getAssetAmount("BTC"))
            assertEquals(3, cache.transactionCount)
        }
    }

    @Nested
    inner class GetAssetAmountTests {
        @Test
        fun `when getting amount for non-existent asset, returns zero amount`() {
            // Act
            val amount = cache.getAssetAmount("NONEXISTENT")

            // Assert
            assertEquals(ExchangeAmount(0.0, "NONEXISTENT"), amount)
        }

        @Test
        fun `when getting amount for existing asset, returns correct amount`() {
            // Arrange
            val transactions = listOf(
                createTestTransaction("1", NormalizedTransactionType.BUY, ExchangeAmount(1.5, "BTC"))
            )
            cache.update(transactions)

            // Act
            val amount = cache.getAssetAmount("BTC")

            // Assert
            assertEquals(ExchangeAmount(1.5, "BTC"), amount)
        }
    }

    @Nested
    inner class GetAllAssetAmountsTests {
        @Test
        fun `when getting all amounts with empty cache, returns empty list`() {
            // Act
            val amounts = cache.getAllAssetAmounts()

            // Assert
            assertTrue(amounts.isEmpty())
        }

        @Test
        fun `when getting all amounts with multiple assets, returns all amounts`() {
            // Arrange
            val transactions = listOf(
                createTestTransaction("1", NormalizedTransactionType.BUY, ExchangeAmount(1.0, "BTC")),
                createTestTransaction("2", NormalizedTransactionType.BUY, ExchangeAmount(100.0, "ETH"))
            )
            cache.update(transactions)

            // Act
            val amounts = cache.getAllAssetAmounts()

            // Assert
            assertEquals(2, amounts.size)
            assertTrue(amounts.contains(ExchangeAmount(1.0, "BTC")))
            assertTrue(amounts.contains(ExchangeAmount(100.0, "ETH")))
        }
    }
}