package com.bitcointracker.unit.core.tax

import com.bitcointracker.core.tax.TaxCalculator
import com.bitcointracker.core.tax.TransactionTracker
import com.bitcointracker.model.api.tax.TaxType
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Date

class TaxCalculatorTest {

    private lateinit var taxCalculator: TaxCalculator
    private lateinit var transactionTracker: TransactionTracker
    private lateinit var usedBuyAmounts: MutableMap<String, ExchangeAmount>

    // Test constants
    private val btcUnit = "BTC"
    private val usdUnit = "USD"
    private val sellTxId = "sell1"
    private val buyTx1Id = "buy1"
    private val buyTx2Id = "buy2"

    @BeforeEach
    fun setUp() {
        taxCalculator = TaxCalculator()
        usedBuyAmounts = mutableMapOf()
        transactionTracker = TransactionTracker(usedBuyAmounts)
    }

    @Test
    fun `calculateTaxableEvent should properly calculate gains with single buy transaction`() {
        // Arrange
        val now = Date()
        val oneYearAgo = getDateBefore(now, 366) // Long-term capital gain

        // Create transactions
        val sellPrice = ExchangeAmount(50000.0, usdUnit)
        val buyPrice = ExchangeAmount(30000.0, usdUnit)
        val oneBitcoin = ExchangeAmount(1.0, btcUnit)

        val sellTx = createTransaction(
            id = sellTxId,
            assetAmount = oneBitcoin,
            transactionAmountFiat = sellPrice,
            timestamp = now,
            type = NormalizedTransactionType.SELL
        )

        val buyTx = createTransaction(
            id = buyTx1Id,
            assetAmount = oneBitcoin,
            transactionAmountFiat = buyPrice,
            timestamp = oneYearAgo,
            type = NormalizedTransactionType.BUY
        )

        // Act
        val result = taxCalculator.calculateTaxableEvent(
            sellTransaction = sellTx,
            buyTransactions = listOf(buyTx),
            transactionTracker = transactionTracker
        )

        // Assert
        assertEquals(sellTxId, result.sellTransactionId)
        assertEquals(sellPrice, result.proceeds)
        assertEquals(buyPrice, result.costBasis)
        assertEquals(ExchangeAmount(20000.0, usdUnit), result.gain) // $50k - $30k = $20k profit
        assertEquals(1, result.usedBuyTransactions.size)
        assertEquals(TaxType.LONG_TERM, result.usedBuyTransactions[0].taxType)
        assertEquals(buyTx1Id, result.usedBuyTransactions[0].transactionId)
        assertEquals(oneBitcoin, result.usedBuyTransactions[0].amountUsed)
        assertEquals(ExchangeAmount(0.0, btcUnit), result.uncoveredSellAmount)
    }

    @Test
    fun `calculateTaxableEvent should handle multiple buy transactions`() {
        // Arrange
        val now = Date()
        val twoMonthsAgo = getDateBefore(now, 60) // Short-term capital gain
        val threeMonthsAgo = getDateBefore(now, 90) // Short-term capital gain

        // Create sell transaction for 1 BTC at $50,000
        val sellPrice = ExchangeAmount(50000.0, usdUnit)
        val oneBitcoin = ExchangeAmount(1.0, btcUnit)
        val halfBitcoin = ExchangeAmount(0.5, btcUnit)

        val sellTx = createTransaction(
            id = sellTxId,
            assetAmount = oneBitcoin,
            transactionAmountFiat = sellPrice,
            timestamp = now,
            type = NormalizedTransactionType.SELL
        )

        // Create two buy transactions of 0.5 BTC each at different prices
        val buyPrice1 = ExchangeAmount(20000.0, usdUnit) // $20,000 for 0.5 BTC
        val buyPrice2 = ExchangeAmount(15000.0, usdUnit) // $15,000 for 0.5 BTC

        val buyTx1 = createTransaction(
            id = buyTx1Id,
            assetAmount = halfBitcoin,
            transactionAmountFiat = buyPrice1,
            timestamp = twoMonthsAgo,
            type = NormalizedTransactionType.BUY
        )

        val buyTx2 = createTransaction(
            id = buyTx2Id,
            assetAmount = halfBitcoin,
            transactionAmountFiat = buyPrice2,
            timestamp = threeMonthsAgo,
            type = NormalizedTransactionType.BUY
        )

        // Act
        val result = taxCalculator.calculateTaxableEvent(
            sellTransaction = sellTx,
            buyTransactions = listOf(buyTx1, buyTx2),
            transactionTracker = transactionTracker
        )

        // Assert
        assertEquals(sellTxId, result.sellTransactionId)
        assertEquals(sellPrice, result.proceeds)

        // Total cost basis should be $20,000 + $15,000 = $35,000
        assertEquals(ExchangeAmount(35000.0, usdUnit), result.costBasis)

        // Gain should be $50,000 - $35,000 = $15,000
        assertEquals(ExchangeAmount(15000.0, usdUnit), result.gain)

        // Should have used both buy transactions
        assertEquals(2, result.usedBuyTransactions.size)

        // Both should be short-term
        assertEquals(TaxType.SHORT_TERM, result.usedBuyTransactions[0].taxType)
        assertEquals(TaxType.SHORT_TERM, result.usedBuyTransactions[1].taxType)

        // No uncovered amount
        assertEquals(ExchangeAmount(0.0, btcUnit), result.uncoveredSellAmount)
    }

    @Test
    fun `calculateTaxableEvent should handle uncovered amounts`() {
        // Arrange
        val now = Date()
        val oneMonthAgo = getDateBefore(now, 30)

        // Sell 1 BTC at $50,000
        val sellPrice = ExchangeAmount(50000.0, usdUnit)
        val oneBitcoin = ExchangeAmount(1.0, btcUnit)
        val halfBitcoin = ExchangeAmount(0.5, btcUnit)

        val sellTx = createTransaction(
            id = sellTxId,
            assetAmount = oneBitcoin,
            transactionAmountFiat = sellPrice,
            timestamp = now,
            type = NormalizedTransactionType.SELL
        )

        // Only have 0.5 BTC available to cover the sell
        val buyPrice = ExchangeAmount(20000.0, usdUnit)
        val buyTx = createTransaction(
            id = buyTx1Id,
            assetAmount = halfBitcoin,
            transactionAmountFiat = buyPrice,
            timestamp = oneMonthAgo,
            type = NormalizedTransactionType.BUY
        )

        // Act
        val result = taxCalculator.calculateTaxableEvent(
            sellTransaction = sellTx,
            buyTransactions = listOf(buyTx),
            transactionTracker = transactionTracker
        )

        // Assert
        assertEquals(sellTxId, result.sellTransactionId)
        assertEquals(sellPrice, result.proceeds)
        assertEquals(buyPrice, result.costBasis)

        // Gain should be $50,000 - $20,000 = $30,000
        assertEquals(ExchangeAmount(30000.0, usdUnit), result.gain)

        // Should have used the one buy transaction
        assertEquals(1, result.usedBuyTransactions.size)

        // Should have uncovered amount of 0.5 BTC
        assertEquals(halfBitcoin, result.uncoveredSellAmount)

        // Uncovered value should be 50% of total sell value
        assertEquals(ExchangeAmount(25000.0, usdUnit), result.uncoveredSellValue)
    }

    @Test
    fun `calculateTaxableEvent should respect transaction tracker`() {
        // Arrange
        val now = Date()
        val oneMonthAgo = getDateBefore(now, 30)

        // Create sell and buy transactions
        val sellPrice = ExchangeAmount(50000.0, usdUnit)
        val buyPrice = ExchangeAmount(30000.0, usdUnit)
        val oneBitcoin = ExchangeAmount(1.0, btcUnit)
        val halfBitcoin = ExchangeAmount(0.5, btcUnit)

        val sellTx = createTransaction(
            id = sellTxId,
            assetAmount = oneBitcoin,
            transactionAmountFiat = sellPrice,
            timestamp = now,
            type = NormalizedTransactionType.SELL
        )

        val buyTx = createTransaction(
            id = buyTx1Id,
            assetAmount = oneBitcoin,
            transactionAmountFiat = buyPrice,
            timestamp = oneMonthAgo,
            type = NormalizedTransactionType.BUY
        )

        // Mock transaction tracker to simulate that half of the buy is already used
        val mockedTracker = mockk<TransactionTracker>()
        every { mockedTracker.getAvailableAmount(buyTx) } returns halfBitcoin

        val amountUsedSlot = slot<ExchangeAmount>()
        every { mockedTracker.updateUsedAmount(buyTx1Id, capture(amountUsedSlot)) } returns Unit

        // Act
        val result = taxCalculator.calculateTaxableEvent(
            sellTransaction = sellTx,
            buyTransactions = listOf(buyTx),
            transactionTracker = mockedTracker
        )

        // Assert
        // Should have used half the buy transaction
        assertEquals(halfBitcoin, amountUsedSlot.captured)

        // Should have uncovered amount of 0.5 BTC
        assertEquals(halfBitcoin, result.uncoveredSellAmount)

        // Cost basis should be half of the buy price
        assertEquals(ExchangeAmount(15000.0, usdUnit), result.costBasis)

        // Verify the tracker's updateUsedAmount was called
        verify(exactly = 1) { mockedTracker.updateUsedAmount(buyTx1Id, any()) }
    }

    @Test
    fun `calculateTaxableEvent should determine tax type based on holding period`() {
        // Arrange
        val now = Date()
        val sixMonthsAgo = getDateBefore(now, 182) // Short-term
        val fifteenMonthsAgo = getDateBefore(now, 456) // Long-term

        // Create transactions
        val sellPrice = ExchangeAmount(60000.0, usdUnit)
        val buyPrice1 = ExchangeAmount(20000.0, usdUnit)
        val buyPrice2 = ExchangeAmount(10000.0, usdUnit)
        val oneBitcoin = ExchangeAmount(1.0, btcUnit)
        val halfBitcoin = ExchangeAmount(0.5, btcUnit)

        val sellTx = createTransaction(
            id = sellTxId,
            assetAmount = oneBitcoin,
            transactionAmountFiat = sellPrice,
            timestamp = now,
            type = NormalizedTransactionType.SELL
        )

        val shortTermBuyTx = createTransaction(
            id = buyTx1Id,
            assetAmount = halfBitcoin,
            transactionAmountFiat = buyPrice1,
            timestamp = sixMonthsAgo,
            type = NormalizedTransactionType.BUY
        )

        val longTermBuyTx = createTransaction(
            id = buyTx2Id,
            assetAmount = halfBitcoin,
            transactionAmountFiat = buyPrice2,
            timestamp = fifteenMonthsAgo,
            type = NormalizedTransactionType.BUY
        )

        // Act
        val result = taxCalculator.calculateTaxableEvent(
            sellTransaction = sellTx,
            buyTransactions = listOf(shortTermBuyTx, longTermBuyTx),
            transactionTracker = transactionTracker
        )

        // Assert
        assertEquals(2, result.usedBuyTransactions.size)
        assertEquals(TaxType.SHORT_TERM, result.usedBuyTransactions[0].taxType)
        assertEquals(TaxType.LONG_TERM, result.usedBuyTransactions[1].taxType)
    }

    /**
     * Helper method to create a transaction for testing
     */
    private fun createTransaction(
        id: String,
        assetAmount: ExchangeAmount,
        transactionAmountFiat: ExchangeAmount,
        timestamp: Date,
        type: NormalizedTransactionType
    ): NormalizedTransaction {
        val transactionSource = mockk<TransactionSource>()
        return NormalizedTransaction(
            id = id,
            source = transactionSource,
            type = type,
            transactionAmountFiat = transactionAmountFiat,
            fee = ExchangeAmount(10.0, usdUnit),
            assetAmount = assetAmount,
            assetValueFiat = transactionAmountFiat,
            timestamp = timestamp,
            timestampText = timestamp.toString(),
            address = "0xabc123",
            notes = "Test transaction",
            filedWithIRS = false
        )
    }

    /**
     * Helper method to get a date N days before the specified date
     */
    private fun getDateBefore(date: Date, days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return calendar.time
    }
}
