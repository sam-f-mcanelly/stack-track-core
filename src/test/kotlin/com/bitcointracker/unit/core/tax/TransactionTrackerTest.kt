package com.bitcointracker.unit.core.tax

import com.bitcointracker.core.tax.TransactionTracker
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class TransactionTrackerTest {

    private lateinit var transactionTracker: TransactionTracker
    private lateinit var usedBuyAmounts: MutableMap<String, ExchangeAmount>

    // Test constants
    private val btcUnit = "BTC"
    private val usdUnit = "USD"
    private val transaction1Id = "tx1"
    private val transaction2Id = "tx2"
    private val oneHundredDollars = ExchangeAmount(100.0, usdUnit)
    private val tenDollars = ExchangeAmount(10.0, usdUnit)
    private val oneBitcoin = ExchangeAmount(1.0, btcUnit)
    private val halfBitcoin = ExchangeAmount(0.5, btcUnit)
    private val quarterBitcoin = ExchangeAmount(0.25, btcUnit)
    private val zeroBitcoin = ExchangeAmount(0.0, btcUnit)
    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        usedBuyAmounts = mutableMapOf()
        transactionTracker = TransactionTracker(usedBuyAmounts)
    }

    @Test
    fun `getAvailableAmount should return full amount for unused transaction`() {
        // Arrange
        val transaction = createTransaction(transaction1Id, oneBitcoin)

        // Act
        val availableAmount = transactionTracker.getAvailableAmount(transaction)

        // Assert
        assertEquals(oneBitcoin, availableAmount)
    }

    @Test
    fun `getAvailableAmount should return reduced amount after partial usage`() {
        // Arrange
        val transaction = createTransaction(transaction1Id, oneBitcoin)
        transactionTracker.updateUsedAmount(transaction1Id, halfBitcoin)

        // Act
        val availableAmount = transactionTracker.getAvailableAmount(transaction)

        // Assert
        assertEquals(halfBitcoin, availableAmount)
    }

    @Test
    fun `getAvailableAmount should return zero when fully used`() {
        // Arrange
        val transaction = createTransaction(transaction1Id, oneBitcoin)
        transactionTracker.updateUsedAmount(transaction1Id, oneBitcoin)

        // Act
        val availableAmount = transactionTracker.getAvailableAmount(transaction)

        // Assert
        assertEquals(zeroBitcoin, availableAmount)
    }

    @Test
    fun `updateUsedAmount should accumulate multiple uses`() {
        // Arrange
        val transaction = createTransaction(transaction1Id, oneBitcoin)

        // Act
        transactionTracker.updateUsedAmount(transaction1Id, quarterBitcoin)
        transactionTracker.updateUsedAmount(transaction1Id, quarterBitcoin)
        val availableAmount = transactionTracker.getAvailableAmount(transaction)

        // Assert
        assertEquals(halfBitcoin, availableAmount)
    }

    @Test
    fun `getAvailableAmount should never return negative amounts`() {
        // Arrange
        val transaction = createTransaction(transaction1Id, halfBitcoin)
        transactionTracker.updateUsedAmount(transaction1Id, oneBitcoin) // Using more than available

        // Act
        val availableAmount = transactionTracker.getAvailableAmount(transaction)

        // Assert
        assertEquals(zeroBitcoin, availableAmount)
    }

    @Test
    fun `tracker should handle multiple transactions independently`() {
        // Arrange
        val transaction1 = createTransaction(transaction1Id, oneBitcoin)
        val transaction2 = createTransaction(transaction2Id, oneBitcoin)

        // Act
        transactionTracker.updateUsedAmount(transaction1Id, halfBitcoin)

        // Assert
        assertEquals(halfBitcoin, transactionTracker.getAvailableAmount(transaction1))
        assertEquals(oneBitcoin, transactionTracker.getAvailableAmount(transaction2))
    }

    @Test
    fun `tracker should handle transactions with different units`() {
        // Arrange
        val btcTransaction = createTransaction(transaction1Id, oneBitcoin)
        val ethTransaction = createTransaction(
            transaction2Id,
            ExchangeAmount(10.0, "ETH")
        )

        // Act
        transactionTracker.updateUsedAmount(transaction1Id, halfBitcoin)
        transactionTracker.updateUsedAmount(
            transaction2Id,
            ExchangeAmount(2.0, "ETH")
        )

        // Assert
        assertEquals(halfBitcoin, transactionTracker.getAvailableAmount(btcTransaction))
        assertEquals(ExchangeAmount(8.0, "ETH"), transactionTracker.getAvailableAmount(ethTransaction))
    }

    /**
     * Helper method to create a transaction for testing
     */
    private fun createTransaction(id: String, assetAmount: ExchangeAmount): NormalizedTransaction {
        val transactionSource = mockk<TransactionSource>()
        return NormalizedTransaction(
            id = id,
            source = transactionSource,
            type = NormalizedTransactionType.BUY,
            transactionAmountFiat = oneHundredDollars,
            fee = tenDollars,
            assetAmount = assetAmount,
            assetValueFiat = oneHundredDollars,
            timestamp = now,
            timestampText = now.toString(),
            address = "0xabc123",
            notes = "Test transaction",
            filedWithIRS = false
        )
    }
}
