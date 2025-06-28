package com.bitcointracker.core.chart

import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.internal.historical.BitcoinData
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Repository that holds Bitcoin data loaded at application startup
 */

@Singleton
class BitcoinDataRepository @Inject constructor(
    bitcoinHistoricalDataLoader: BitcoinHistoricalDataLoader,
    private val coinbaseClient: CoinbaseClient,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(BitcoinDataRepository::class.java)
    }

    private val _data: List<BitcoinData>

    /**
     * Returns a list of all Bitcoin data, sorted by date
     */
    val data: List<BitcoinData>
        get() = _data

    /**
     * Initializes the repository by loading all Bitcoin data
     * Called automatically by the container at startup
     */
    init {
        logger.info("Initializing Bitcoin data repository")
        try {
            val historicalData = bitcoinHistoricalDataLoader.loadAllData()
                .sortedBy { it.date }
            logger.info("Successfully loaded ${historicalData.size} historical Bitcoin data points")

            // Fill in missing data from Coinbase API
            _data = fillMissingData(historicalData)

            logger.info("Bitcoin data repository initialized with ${_data.size} total data points")
            if (_data.isNotEmpty()) {
                logger.info("Data ranges from ${_data.first().date} to ${_data.last().date}")
            }
        } catch (e: Exception) {
            logger.error("Failed to load Bitcoin data during initialization", e)
            throw e
        }
    }

    /**
     * Find Bitcoin data for a specific date
     *
     * @param date The date to search for
     * @return BitcoinData for the given date, or null if not found
     */
    fun findByDate(date: Instant): BitcoinData? {
        val targetDate = date.atZone(ZoneOffset.UTC).toLocalDate()
        return _data.find { bitcoinData ->
            val bitcoinDate = bitcoinData.date.atZone(ZoneOffset.UTC).toLocalDate()
            bitcoinDate == targetDate
        }
    }

    /**
     * Find Bitcoin data within a date range
     *
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @return List of BitcoinData within the range, sorted by date
     */
    fun findByDateRange(startDate: Instant, endDate: Instant): List<BitcoinData> {
        return _data.filter {
            it.date in startDate..endDate
        }
    }

    /**
     * Fills missing Bitcoin data from the last available date to current date using Coinbase API
     */
    private fun fillMissingData(existingData: List<BitcoinData>): List<BitcoinData> {
        if (existingData.isEmpty()) {
            logger.warn("No existing Bitcoin data found, skipping API calls")
            return existingData
        }
        val lastDataDate = existingData.last().date

        // Get the next day after the last data point
        val startDate = lastDataDate.plusSeconds(24 * 60 * 60) // Add 24 hours
        val currentDateStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()

        // If we're already up to date, return existing data
        if (startDate.isAfter(currentDateStart)) {
            logger.info("Bitcoin data is already up to date")
            return existingData
        }

        logger.info("Filling missing Bitcoin data from ${formatInstantToDateString(startDate)} to ${formatInstantToDateString(currentDateStart)}")

        val newDataPoints = mutableListOf<BitcoinData>()
        var dateToFetch = startDate

        while (dateToFetch.isBefore(currentDateStart) || dateToFetch == currentDateStart) {
            val dateString = formatInstantToDateString(dateToFetch)
            logger.info("Fetching Bitcoin price for date: $dateString")

            try {
                val price = coinbaseClient.getHistoricalPrice("BTC", "USD", dateString)
                if (price != null) {
                    val bitcoinData = createBitcoinDataFromPrice(dateToFetch, price)
                    newDataPoints.add(bitcoinData)
                    logger.info("Successfully fetched price for $dateString: $$price")
                } else {
                    logger.warn("Failed to fetch price for $dateString, skipping")
                }
            } catch (e: Exception) {
                logger.error("Error fetching price for $dateString", e)
            }

            // Move to next day
            dateToFetch = dateToFetch.plusSeconds(24 * 60 * 60)

            // Add a small delay to be respectful to the API
            Thread.sleep(50) // 50ms delay between requests
        }

        logger.info("Successfully fetched ${newDataPoints.size} new data points from Coinbase")
        return existingData + newDataPoints
    }

    /**
     * Formats an Instant to YYYY-MM-DD format (UTC timezone) for use with Coinbase API
     */
    private fun formatInstantToDateString(instant: Instant): String {
        return instant.atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    /**
     * Creates a BitcoinData entry with only the close price populated from Coinbase data.
     * Open, high, and low are set to the same value as close since we only have spot price.
     */
    private fun createBitcoinDataFromPrice(date: Instant, price: Double): BitcoinData {
        val amount = ExchangeAmount(price, "USD") // Assuming USD currency
        return BitcoinData(
            date = date,
            open = amount,
            high = amount,
            low = amount,
            close = amount
        )
    }
}