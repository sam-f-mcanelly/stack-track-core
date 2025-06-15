package com.bitcointracker.core.chart

import com.bitcointracker.model.internal.historical.BitcoinData
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset

/**
 * Repository that holds Bitcoin data loaded at application startup
 */

@Singleton
class BitcoinDataRepository @Inject constructor(
    bitcoinHistoricalDataLoader: BitcoinHistoricalDataLoader
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
            _data = bitcoinHistoricalDataLoader.loadAllData()
                .sortedBy { it.date }
            logger.info("Successfully loaded ${_data.size} Bitcoin data points")
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
}