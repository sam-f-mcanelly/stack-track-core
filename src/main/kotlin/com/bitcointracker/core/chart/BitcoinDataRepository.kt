package com.bitcointracker.core.chart

import com.bitcointracker.model.internal.historical.BitcoinData
import javax.inject.Inject
import javax.inject.Singleton
import org.slf4j.LoggerFactory

/**
 * Repository that holds Bitcoin data loaded at application startup
 */
@Singleton
class BitcoinDataRepository @Inject constructor(
    private val bitcoinHistoricalDataLoader: BitcoinHistoricalDataLoader
) {
    companion object {
        private val logger = LoggerFactory.getLogger(BitcoinDataRepository::class.java)
    }

    private val _data: MutableList<BitcoinData> = mutableListOf()

    /**
     * Returns a list of all Bitcoin data, sorted by date
     */
    val data: List<BitcoinData>
        get() = _data.toList()

    /**
     * Initializes the repository by loading all Bitcoin data
     * Called automatically by the container at startup
     */
    init {
        logger.info("Initializing Bitcoin data repository")
        try {
            val loadedData = bitcoinHistoricalDataLoader.loadAllData()
            _data.clear()
            _data.addAll(loadedData)
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
     * @return The BitcoinData for the date, or null if not found
     */
    fun findByDate(date: java.util.Date): BitcoinData? {
        return _data.find { it.date.time == date.time }
    }

    /**
     * Find Bitcoin data within a date range
     *
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @return List of BitcoinData within the range, sorted by date
     */
    fun findByDateRange(startDate: java.util.Date, endDate: java.util.Date): List<BitcoinData> {
        return _data.filter {
            it.date.time >= startDate.time && it.date.time <= endDate.time
        }.sortedBy { it.date.time }
    }
}