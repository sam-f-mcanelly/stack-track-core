package com.bitcointracker.core.chart

import com.bitcointracker.core.parser.historical.BitcoinHistoricalDataCsvParser
import com.bitcointracker.model.internal.historical.BitcoinData
import com.bitcointracker.util.local.ClasspathResourceProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitcoinHistoricalDataLoader @Inject constructor(
    private val resourceProvider: ClasspathResourceProvider,
    private val csvParser: BitcoinHistoricalDataCsvParser
) {
    companion object {
        const val CSV_EXTENSION = ".csv"
        const val BTC_DATA_DEFAULT_DIR = "btc_historical_data"

        private val logger = org.slf4j.LoggerFactory.getLogger(BitcoinHistoricalDataLoader::class.java)
    }

    /**
     * Loads all Bitcoin data from CSV files in the specified resources directory
     *
     * @param directoryPath Path relative to resources directory (e.g., "btc_historical_data")
     * @return List of BitcoinData sorted by date with duplicates removed
     */
    fun loadAllData(directoryPath: String = BTC_DATA_DEFAULT_DIR): List<BitcoinData> {
        logger.info("Loading Bitcoin data from $directoryPath")

        val files = resourceProvider.findResourceFiles(directoryPath, CSV_EXTENSION)
        logger.info("Found ${files.size} CSV files to process")

        val dataPoints = files.flatMap { csvParser.parse(it) }
        logger.info("Parsed ${dataPoints.size} data points total")

        // Handle duplicate dates by taking the first occurrence
        val uniqueDataPoints = dataPoints
            .groupBy { it.date }
            .map { (_, points) ->
                if (points.size > 1) {
                    logger.debug("Found ${points.size} entries for date ${points.first().date}, using first entry")
                }
                points.first()
            }
            .sortedBy { it.date }

        logger.info("Returning ${uniqueDataPoints.size} unique data points")
        return uniqueDataPoints
    }
}