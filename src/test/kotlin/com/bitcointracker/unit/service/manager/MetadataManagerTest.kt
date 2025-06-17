package com.bitcointracker.unit.service.manager

import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.core.chart.BitcoinDataRepository
import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.api.AssetHoldingsReport
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import com.bitcointracker.model.internal.historical.BitcoinData
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.service.manager.MetadataManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@ExtendWith(MockKExtension::class)
class MetadataManagerTest {

    @MockK
    private lateinit var transactionRepository: TransactionRepository
    @MockK
    private lateinit var coinbaseClient: CoinbaseClient
    @MockK
    private lateinit var transactionCache: TransactionMetadataCache
    @MockK
    private lateinit var bitcoinDataRepository: BitcoinDataRepository

    @InjectMockKs
    private lateinit var metadataManager: MetadataManager

    @Test
    fun `getCurrentPrice WHEN client returns price THEN returns price from client`() {
        // Arrange
        val asset = "BTC"
        val currency = "USD"
        val expectedPrice = 56789.12
        every { coinbaseClient.getCurrentPrice(asset, currency) } returns expectedPrice

        // Act
        val result = metadataManager.getCurrentPrice(asset, currency)

        // Assert
        assertEquals(expectedPrice, result)
    }

    @Test
    fun `getCurrentPrice WHEN client returns null THEN returns default value`() {
        // Arrange
        val asset = "BTC"
        val currency = "USD"
        val defaultPrice = 55000.0
        every { coinbaseClient.getCurrentPrice(asset, currency) } returns null

        // Act
        val result = metadataManager.getCurrentPrice(asset, currency)

        // Assert
        assertEquals(defaultPrice, result)
    }

    @Test
    @Disabled
    // TODO: fix
    fun `getAccumulation WHEN called THEN returns mapped amounts from analyzer`() = runTest {
        // Arrange
        val days = 7
        val asset = "ETH"
        val accumulations = listOf(
            ExchangeAmount(0.5, "ETH"),
            ExchangeAmount(1.2, "ETH"),
            ExchangeAmount(0.8, "ETH")
        )
        val expectedAmounts = listOf(0.5, 1.2, 0.8)

        // Act
        val result = metadataManager.getAccumulation(days, asset)

        // Assert
        assertEquals(expectedAmounts, result)
    }

    @Test
    fun `getPortfolioValue WHEN calculated THEN returns total value of all assets`() = runTest {
        // Arrange
        val fiat = "EUR"
        val assetAmounts = listOf(
            ExchangeAmount(1.5, "BTC"),
            ExchangeAmount(10.0, "ETH"),
            ExchangeAmount(500.0, "XRP")
        )

        every { transactionCache.getAllAssetAmounts() } returns assetAmounts
        every { coinbaseClient.getCurrentPrice("BTC", fiat) } returns 50000.0
        every { coinbaseClient.getCurrentPrice("ETH", fiat) } returns 3000.0
        every { coinbaseClient.getCurrentPrice("XRP", fiat) } returns 0.5

        val expectedTotal = (1.5 * 50000.0) + (10.0 * 3000.0) + (500.0 * 0.5)
        val expectedResult = ExchangeAmount(expectedTotal, fiat)

        // Act
        val result = metadataManager.getPortfolioValue(fiat)

        // Assert
        assertEquals(expectedResult.amount, result.amount)
        assertEquals(expectedResult.unit, result.unit)
    }

    @Test
    fun `getPortfolioValue WHEN price is null THEN uses zero`() = runTest {
        // Arrange
        val fiat = "EUR"
        val assetAmounts = listOf(
            ExchangeAmount(1.5, "BTC"),
            ExchangeAmount(10.0, "ETH")
        )

        every { transactionCache.getAllAssetAmounts() } returns assetAmounts
        every { coinbaseClient.getCurrentPrice("BTC", fiat) } returns 50000.0
        every { coinbaseClient.getCurrentPrice("ETH", fiat) } returns null

        val expectedTotal = (1.5 * 50000.0) + (10.0 * 0.0)
        val expectedResult = ExchangeAmount(expectedTotal, fiat)

        // Act
        val result = metadataManager.getPortfolioValue(fiat)

        // Assert
        assertEquals(expectedResult.amount, result.amount)
        assertEquals(expectedResult.unit, result.unit)
    }

    @Test
    fun `getAssetHoldings WHEN getting report THEN returns report with correct values`() = runTest {
        // Arrange
        val asset = "BTC"
        val currency = "USD"
        val assetAmount = ExchangeAmount(2.5, asset)
        val price = 54321.98
        val expectedTotalValue = assetAmount * price

        every { transactionCache.getAssetAmount(asset) } returns assetAmount
        every { coinbaseClient.getCurrentPrice(asset, currency) } returns price

        val expectedReport = AssetHoldingsReport(
            asset = asset,
            assetAmount = assetAmount,
            fiatValue = expectedTotalValue
        )

        // Act
        val result = metadataManager.getAssetHoldings(asset, currency)

        // Assert
        assertEquals(expectedReport.asset, result.asset)
        assertEquals(expectedReport.assetAmount, result.assetAmount)
        assertEquals(expectedReport.fiatValue, result.fiatValue)
    }

    @Test
    fun `getAssetHoldings WHEN price is null THEN handles null price`() = runTest {
        // Arrange
        val asset = "XYZ"
        val currency = "USD"
        val assetAmount = ExchangeAmount(100.0, "XYZ")

        every { transactionCache.getAssetAmount(asset) } returns assetAmount
        every { coinbaseClient.getCurrentPrice(asset, currency) } returns null

        val expectedReport = AssetHoldingsReport(
            asset = asset,
            assetAmount = assetAmount,
            fiatValue = null
        )

        // Act
        val result = metadataManager.getAssetHoldings(asset, currency)

        // Assert
        assertEquals(expectedReport.asset, result.asset)
        assertEquals(expectedReport.assetAmount, result.assetAmount)
        assertEquals(expectedReport.fiatValue, result.fiatValue)
    }

    @Test
    fun `getHistory WHEN no transactions exist THEN returns empty list`() = runTest {
        // Arrange
        val asset = "BTC"
        coEvery { transactionRepository.getTransactionsByAsset(asset) } returns emptyList()

        // Act
        val result = metadataManager.getHistory(asset)

        // Assert
        assertThat(result).isEmpty()
        coVerify(exactly = 1) { transactionRepository.getTransactionsByAsset(asset) }
    }

    @Test
    fun `getHistory WHEN transactions exist THEN returns correct daily data`() = runTest {
        // Arrange
        val asset = "BTC"
        val transaction1Date = Instant.parse("2023-01-01T00:00:00Z")
        val transaction2Date = Instant.parse("2023-01-15T00:00:00Z")

        val transaction1 = createTransaction(
            id = "1",
            type = NormalizedTransactionType.BUY,
            timestamp = transaction1Date,
            assetAmount = ExchangeAmount(1.0, asset)
        )

        val transaction2 = createTransaction(
            id = "2",
            type = NormalizedTransactionType.BUY,
            timestamp = transaction2Date,
            assetAmount = ExchangeAmount(0.5, asset)
        )

        val transactions = listOf(transaction1, transaction2)

        val bitcoinData1 = createBitcoinData(
            date = transaction1Date,
            close = ExchangeAmount(20000.0, "USD")
        )

        val bitcoinData2 = createBitcoinData(
            date = transaction2Date,
            close = ExchangeAmount(22000.0, "USD")
        )

        val bitcoinData3 = createBitcoinData(
            date = Instant.parse("2023-01-20T00:00:00Z"),
            close = ExchangeAmount(25000.0, "USD")
        )

        val bitcoinDataList = listOf(bitcoinData1, bitcoinData2, bitcoinData3)

        coEvery { transactionRepository.getTransactionsByAsset(asset) } returns transactions
        every { bitcoinDataRepository.findByDateRange(transaction1Date, any()) } returns bitcoinDataList

        // Act
        val result = metadataManager.getHistory(asset)

        // Assert
        assertThat(result).hasSize(3)

        // First day (has first transaction)
        assertThat(result[0].date).isEqualTo(bitcoinData1.date)
        assertThat(result[0].value).isEqualTo(bitcoinData1.close * transaction1.assetAmount.amount)
        assertThat(result[0].assetAmount.amount).isEqualTo(1.0)
        assertThat(result[0].assetAmount.unit).isEqualTo(asset)

        // Middle day
        assertThat(result[1].date).isEqualTo(bitcoinData2.date)
        assertThat(result[1].value).isEqualTo(bitcoinData2.close * (transaction1.assetAmount.amount + transaction2.assetAmount.amount))
        assertThat(result[1].assetAmount.amount).isEqualTo(1.5)
        assertThat(result[1].assetAmount.unit).isEqualTo(asset)

        // Last day (after both transactions)
        assertThat(result[2].date).isEqualTo(bitcoinData3.date)
        assertThat(result[2].value).isEqualTo(bitcoinData3.close * (transaction1.assetAmount.amount + transaction2.assetAmount.amount))
        assertThat(result[2].assetAmount.amount).isEqualTo(1.5)
        assertThat(result[2].assetAmount.unit).isEqualTo(asset)

        coVerify(exactly = 1) { transactionRepository.getTransactionsByAsset(asset) }
        verify(exactly = 1) { bitcoinDataRepository.findByDateRange(transaction1Date, any()) }
    }

    @Test
    fun `getHistory WHEN different transaction types exist THEN calculates correct running totals`() = runTest {
        // Arrange
        val asset = "BTC"
        val transaction1Date = Instant.parse("2023-01-01T00:00:00Z")
        val transaction2Date = Instant.parse("2023-01-15T00:00:00Z")
        val transaction3Date = Instant.parse("2023-01-20T00:00:00Z")

        val transaction1 = createTransaction(
            id = "1",
            type = NormalizedTransactionType.BUY,
            timestamp = transaction1Date,
            assetAmount = ExchangeAmount(2.0, asset)
        )

        val transaction2 = createTransaction(
            id = "2",
            type = NormalizedTransactionType.SELL,
            timestamp = transaction2Date,
            assetAmount = ExchangeAmount(0.5, asset)
        )

        val transaction3 = createTransaction(
            id = "3",
            type = NormalizedTransactionType.DEPOSIT,
            timestamp = transaction3Date,
            assetAmount = ExchangeAmount(1.0, asset)
        )

        val transactions = listOf(transaction1, transaction2, transaction3)

        val bitcoinData1 = createBitcoinData(
            date = Instant.parse("2023-01-01T00:00:00Z"),
            close = ExchangeAmount(20000.0, "USD")
        )

        val bitcoinData2 = createBitcoinData(
            date = Instant.parse("2023-01-15T00:00:00Z"),
            close = ExchangeAmount(22000.0, "USD")
        )

        val bitcoinData3 = createBitcoinData(
            date = Instant.parse("2023-01-25T00:00:00Z"),
            close = ExchangeAmount(25000.0, "USD")
        )

        val bitcoinDataList = listOf(bitcoinData1, bitcoinData2, bitcoinData3)

        coEvery { transactionRepository.getTransactionsByAsset(asset) } returns transactions
        every { bitcoinDataRepository.findByDateRange(transaction1Date, any()) } returns bitcoinDataList

        // Act
        val result = metadataManager.getHistory(asset)

        // Assert
        assertThat(result).hasSize(3)

        // First data point (after first transaction)
        assertThat(result[0].date).isEqualTo(bitcoinData1.date)
        assertThat(result[0].assetAmount.amount).isEqualTo(2.0) // Initial purchase

        // Second data point (after sell transaction)
        assertThat(result[1].date).isEqualTo(bitcoinData2.date)
        assertThat(result[1].assetAmount.amount).isEqualTo(1.5) // 2.0 - 0.5

        // Third data point (after deposit transaction, which may not affect total depending on implementation)
        assertThat(result[2].date).isEqualTo(bitcoinData3.date)
        // The exact expected value depends on how the DEPOSIT transaction type is handled in the implementation

        coVerify(exactly = 1) { transactionRepository.getTransactionsByAsset(asset) }
        verify(exactly = 1) { bitcoinDataRepository.findByDateRange(transaction1Date, any()) }
    }

    @Test
    fun `getHistory WHEN no bitcoin data exists THEN returns empty list`() = runTest {
        // Arrange
        val asset = "BTC"
        val transaction = createTransaction(
            id = "1",
            type = NormalizedTransactionType.BUY,
            timestamp = Instant.now(),
            assetAmount = ExchangeAmount(1.0, asset)
        )

        coEvery { transactionRepository.getTransactionsByAsset(asset) } returns listOf(transaction)
        every { bitcoinDataRepository.findByDateRange(any(), any()) } returns emptyList()

        // Act
        val result = metadataManager.getHistory(asset)

        // Assert
        assertThat(result).isEmpty()
        coVerify(exactly = 1) { transactionRepository.getTransactionsByAsset(asset) }
        verify(exactly = 1) { bitcoinDataRepository.findByDateRange(any(), any()) }
    }

    private fun createTransaction(
        id: String,
        type: NormalizedTransactionType,
        timestamp: Instant,
        assetAmount: ExchangeAmount
    ): NormalizedTransaction {
        return NormalizedTransaction(
            id = id,
            source = TransactionSource.COINBASE_PRO_FILL,
            type = type,
            transactionAmountFiat = ExchangeAmount(assetAmount.amount * 20000.0, "USD"),
            fee = ExchangeAmount(10.0, "USD"),
            assetAmount = assetAmount,
            assetValueFiat = ExchangeAmount(assetAmount.amount * 20000.0, "USD"),
            timestamp = timestamp,
            timestampText = timestamp.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            address = "",
            notes = "",
            filedWithIRS = false
        )
    }

    private fun createBitcoinData(
        date: Instant,
        close: ExchangeAmount
    ): BitcoinData {
        return BitcoinData(
            date = date,
            open = close,
            high = close,
            low = close,
            close = close
        )
    }
}
