package com.bitcointracker.integration

import com.bitcointracker.module
import com.bitcointracker.dagger.component.DaggerAppComponent
import com.bitcointracker.model.api.DailyData
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.util.jackson.ExchangeAmountDeserializer
import com.bitcointracker.util.jackson.ExchangeAmountSerializer
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.platform.commons.logging.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the historical data API
 * Validates that the historical data accurately reflects transaction history
 */
class HistoricalDataIntegrationTest {
    companion object {
        private val logger = LoggerFactory.getLogger(HistoricalDataIntegrationTest::class.java)

        // Define a constant for the Strike monthly statement file path
        const val STRIKE_STATEMENT_PATH =
            "src/test/resources/account_statements/strike/monthly_statements/strike_hodl_monthly.csv"
    }

    private lateinit var statementFile: File
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        // Initialize and configure the Jackson ObjectMapper the same way as in the service
        objectMapper = ObjectMapper().apply {
            // Enable pretty printing
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            // Register Kotlin module with the same configuration as the service
            registerModule(
                KotlinModule.Builder()
                    .withReflectionCacheSize(512)
                    .configure(KotlinFeature.NullToEmptyCollection, true)
                    .configure(KotlinFeature.NullToEmptyMap, false)
                    .configure(KotlinFeature.NullIsSameAsDefault, true)
                    .configure(KotlinFeature.SingletonSupport, false)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build()
            )

            // Register custom serializers/deserializers for ExchangeAmount
            val module = SimpleModule().apply {
                addSerializer(ExchangeAmount::class.java, ExchangeAmountSerializer())
                addDeserializer(ExchangeAmount::class.java, ExchangeAmountDeserializer())
            }
            registerModule(module)
        }

        // Confirm the Strike monthly statement file exists
        statementFile = File(STRIKE_STATEMENT_PATH)
        assertTrue(statementFile.exists(), "Strike monthly statement file must exist at $STRIKE_STATEMENT_PATH")
    }

    @Test
    @Disabled
    fun testHistoricalDataAPI() = testApplication {
        // Set up the application with your DI component
        val appComponent = DaggerAppComponent.create()
        application {
            module(appComponent)
        }

        // First, upload the test data to ensure we have transactions to analyze
        val uploadResponse = client.post("/api/data/upload") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", statementFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"${statementFile.name}\"")
                        append(HttpHeaders.ContentType, ContentType.Text.CSV.toString())
                    })
                }
            ))
        }

        // Assert the upload response status is OK
        assertEquals(HttpStatusCode.OK, uploadResponse.status)

        // Now fetch the historical data for BTC
        val historyResponse = client.get("/api/metadata/history/BTC")
        assertEquals(HttpStatusCode.OK, historyResponse.status)

        // Parse the response body to get the historical data
        val historyString: String = historyResponse.body()

        logger.info { "raw response" }
        logger.info { historyString }

        // Direct array parsing - no need to look for a 'data' field
        val dailyDataPoints: List<DailyData> = objectMapper.readValue(
            historyString,
            objectMapper.typeFactory.constructCollectionType(List::class.java, DailyData::class.java)
        )

        // Validate the historical data
        validateHistoricalData(dailyDataPoints)
    }

    @Test
    @Disabled
    fun testHistoricalDataForNonExistentAsset() = testApplication {
        // Set up the application with your DI component
        val appComponent = DaggerAppComponent.create()
        application {
            module(appComponent)
        }

        // Fetch historical data for a non-existent asset
        val historyResponse = client.get("/api/metadata/history/NONEXISTENT")
        assertEquals(HttpStatusCode.OK, historyResponse.status)

        // Parse the response body to get the historical data
        val historyString: String = historyResponse.body()

        // Direct array parsing
        val dailyDataPoints: List<DailyData> = objectMapper.readValue(
            historyString,
            objectMapper.typeFactory.constructCollectionType(List::class.java, DailyData::class.java)
        )

        // Verify that the data is empty for a non-existent asset
        assertTrue(dailyDataPoints.isEmpty(), "Historical data for non-existent asset should be empty")
    }

    private fun validateHistoricalData(dailyDataPoints: List<DailyData>) {
        // Verify we have data points
        assertTrue(dailyDataPoints.isNotEmpty(), "Historical data should not be empty")

        // Validate chronological order
        for (i in 1 until dailyDataPoints.size) {
            assertTrue(
                !dailyDataPoints[i-1].date.isAfter(dailyDataPoints[i].date),
                "Data points should be in chronological order"
            )
        }

        // Validate final accumulated amount
        // The expected total BTC purchased is 0.00145097 based on the sample data
        val expectedBtcTotal = 0.00145097
        val lastDataPoint = dailyDataPoints.last()
        assertEquals(
            expectedBtcTotal,
            lastDataPoint.assetAmount.amount,
            0.00000001,
            "Final accumulated BTC amount should match expected total"
        )

        // BTC amount should never decrease (all transactions are BUY)
        for (i in 1 until dailyDataPoints.size) {
            assertTrue(
                dailyDataPoints[i-1].assetAmount.amount <= dailyDataPoints[i].assetAmount.amount,
                "BTC amount should not decrease between data points"
            )
        }

        // Validate specific data points based on transaction dates
        validateSpecificDates(dailyDataPoints)
    }

    private fun validateSpecificDates(dailyDataPoints: List<DailyData>) {
        // Create expected data points based on the known transaction data
        // The dates and amounts should match the transaction data from the CSV file
        val expectedDataPoints = createExpectedDataPoints()

        // Check each expected data point
        for (expected in expectedDataPoints) {
            // Find the closest matching data point by date
            val actual = findDataPointForDate(dailyDataPoints, expected.date)

            // Verify the data point exists
            assertTrue(actual != null, "Data point for ${formatDate(expected.date)} not found")

            actual?.let {
                // Verify the asset amount matches within tolerance
                assertEquals(
                    expected.amount,
                    it.assetAmount.amount,
                    0.00000001,
                    "Asset amount mismatch for date ${formatDate(expected.date)}"
                )

                // Verify the asset unit
                assertEquals(
                    expected.unit,
                    it.assetAmount.unit,
                    "Asset unit mismatch for date ${formatDate(expected.date)}"
                )

                // Verify the value is present
                assertTrue(
                    it.value.amount > 0,
                    "Asset value should be greater than zero for date ${formatDate(expected.date)}"
                )
            }
        }
    }

    // Helper function to find a data point by date
    private fun findDataPointForDate(dailyDataPoints: List<DailyData>, targetDate: Instant): DailyData? {
        // First try to find exact date match
        val exactMatch = dailyDataPoints.find {
            isSameDay(it.date, targetDate)
        }

        if (exactMatch != null) {
            return exactMatch
        }

        // If no exact match, find the closest later date
        return dailyDataPoints.find {
            it.date >= targetDate
        }
    }

    private fun isSameDay(date1: Instant, date2: Instant): Boolean {
        val localDate1 = date1.atZone(ZoneOffset.UTC).toLocalDate()
        val localDate2 = date2.atZone(ZoneOffset.UTC).toLocalDate()
        return localDate1 == localDate2
    }

    private fun formatDate(date: Instant): String {
        return SimpleDateFormat("yyyy-MM-dd").format(date)
    }

    private fun createExpectedDataPoints(): List<ExpectedDataPoint> {
        val expectedPoints = mutableListOf<ExpectedDataPoint>()

        // The first transaction (BUY) date is Jan 02 2025
        expectedPoints.add(ExpectedDataPoint(
            Instant.parse("2025-01-02"),
            0.00010529,
            "BTC"
        ))

        // After the second transaction on Jan 03 2025
        expectedPoints.add(ExpectedDataPoint(
            Instant.parse("2025-01-03"),
            0.00021547,  // Accumulated: first + second BTC purchase
            "BTC"
        ))

        // After the fifth transaction
        expectedPoints.add(ExpectedDataPoint(
            Instant.parse("2025-01-10"),
            0.00054891,  // Accumulated after 5 purchases
            "BTC"
        ))

        // Last transaction date
        expectedPoints.add(ExpectedDataPoint(
            Instant.parse("2025-01-31"),
            0.00145097,  // Total BTC after all 10 purchases
            "BTC"
        ))

        return expectedPoints
    }

    private data class ExpectedDataPoint(
        val date: Instant,
        val amount: Double,
        val unit: String
    )
}