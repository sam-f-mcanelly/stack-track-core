package com.bitcointracker.service.manager

import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.api.AssetHoldingsReport
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MetadataManagerTest {

    @MockK
    private lateinit var transactionRepository: TransactionRepository
    @MockK
    private lateinit var coinbaseClient: CoinbaseClient
    @MockK
    private lateinit var transactionCache: TransactionMetadataCache

    @InjectMockKs
    private lateinit var metadataManager: MetadataManager

    @Test
    fun `getCurrentPrice returns price from client when available`() {
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
    fun `getCurrentPrice returns default value when client returns null`() {
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
    fun `getAccumulation returns mapped amounts from analyzer`() = runBlocking {
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
    fun `getPortfolioValue calculates total value of all assets`() = runBlocking {
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
    fun `getPortfolioValue handles null price by using zero`() = runBlocking {
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
    fun `getAssetHoldings returns report with correct values`() = runBlocking {
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
    fun `getAssetHoldings handles null price`() = runBlocking {
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
}
