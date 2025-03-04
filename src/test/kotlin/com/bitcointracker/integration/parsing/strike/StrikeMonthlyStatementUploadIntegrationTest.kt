package com.bitcointracker.integration.parsing.strike

import com.bitcointracker.module
import com.bitcointracker.dagger.component.DaggerAppComponent
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import com.bitcointracker.util.jackson.ExchangeAmountDeserializer
import com.bitcointracker.util.jackson.ExchangeAmountSerializer
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.junit.jupiter.api.Test
import java.io.File
import java.text.SimpleDateFormat
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrikeMonthlyStatementUploadIntegrationTest {
    companion object {
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
    fun testStrikeStatementUpload() = testApplication {
        // Set up the application with your DI component
        val appComponent = DaggerAppComponent.create()
        application {
            module(appComponent)
        }

        // Create a multipart form request with the Strike statement file
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

        // Now fetch the transactions to validate them
        val transactionsResponse = client.get("/api/data/transactions?pageSize=20")
        assertEquals(HttpStatusCode.OK, transactionsResponse.status)

        // Parse the response body to get the transactions
        val transactionsString: String = transactionsResponse.body()

        // Try to parse the response as a JSON object with a 'data' field
        val responseMap: Map<String, Any> = objectMapper.readValue(transactionsString)

        // Extract the transactions from the 'data' field and convert to List<NormalizedTransaction>
        @Suppress("UNCHECKED_CAST")
        val transactionsData = responseMap["data"] as? List<Map<String, Any>>
            ?: throw IllegalStateException("Could not find or parse 'data' field in response")

        // Convert each transaction map to a NormalizedTransaction object
        val transactions = transactionsData.map { transactionMap ->
            objectMapper.convertValue(transactionMap, NormalizedTransaction::class.java)
        }

        // Validate the number of transactions (20 transactions in the sample data)
        assertEquals(20, transactions.size, "Expected 20 transactions")

        // Define expected transactions based on the provided data
        val expectedTransactions = createExpectedTransactions()

        // Validate key transactions
        validateTransactions(expectedTransactions, transactions)
    }

    private fun createExpectedTransactions(): List<NormalizedTransaction> {
        val dateFormat = SimpleDateFormat("MMM dd yyyy HH:mm:ss")
        val transactions = mutableListOf<NormalizedTransaction>()

        // Sample of expected transactions based on the provided data
        // First deposit transaction
        transactions.add(
            NormalizedTransaction(
                id = "b7e45921-c8d3-429a-ae12-f8b291d54e81",
                source = TransactionSource.STRIKE_MONTHLY,
                type = NormalizedTransactionType.DEPOSIT,
                transactionAmountFiat = ExchangeAmount(9.87, "USD"),
                fee = ExchangeAmount(0.0, "USD"),
                assetAmount = ExchangeAmount(0.0, "USD"),
                assetValueFiat = ExchangeAmount(0.0, "USD"),
                timestamp = dateFormat.parse("Jan 02 2025 01:23:45"),
                timestampText = "Jan 02 2025 01:23:45",
                address = "",
                notes = ""
            )
        )

        // First trade transaction
        transactions.add(
            NormalizedTransaction(
                id = "3a9854c6-78b2-42ef-9d23-741cfa358def",
                source = TransactionSource.STRIKE_MONTHLY,
                type = NormalizedTransactionType.BUY,
                transactionAmountFiat = ExchangeAmount(9.87, "USD"),
                fee = ExchangeAmount(0.0, "USD"),
                assetAmount = ExchangeAmount(0.00010529, "BTC"),
                assetValueFiat = ExchangeAmount(9.87, "USD"),
                timestamp = dateFormat.parse("Jan 02 2025 01:24:12"),
                timestampText = "Jan 02 2025 01:24:12",
                address = "",
                notes = ""
            )
        )

        // Adding a third transaction for more validation coverage
        transactions.add(
            NormalizedTransaction(
                id = "e7c129bf-4725-48ac-b843-5a9b78246d12",
                source = TransactionSource.STRIKE_MONTHLY,
                type = NormalizedTransactionType.DEPOSIT,
                transactionAmountFiat = ExchangeAmount(10.24, "USD"),
                fee = ExchangeAmount(0.0, "USD"),
                assetAmount = ExchangeAmount(0.0, "USD"),
                assetValueFiat = ExchangeAmount(0.0, "USD"),
                timestamp = dateFormat.parse("Jan 03 2025 01:22:33"),
                timestampText = "Jan 03 2025 01:22:33",
                address = "",
                notes = ""
            )
        )

        return transactions
    }

    private fun validateTransactions(
        expectedTransactions: List<NormalizedTransaction>,
        actualTransactions: List<NormalizedTransaction>
    ) {
        // Validate specific transactions by ID
        for (expected in expectedTransactions) {
            val actual = actualTransactions.find { it.id == expected.id }
            assertTrue(actual != null, "Transaction with ID ${expected.id} not found")

            actual.let {
                assertEquals(expected.source, it.source, "Transaction source mismatch for ID ${expected.id}")
                assertEquals(expected.type, it.type, "Transaction type mismatch for ID ${expected.id}")
                assertEquals(expected.transactionAmountFiat.amount, it.transactionAmountFiat.amount, 0.001,
                    "Transaction amount mismatch for ID ${expected.id}")
                assertEquals(expected.transactionAmountFiat.unit, it.transactionAmountFiat.unit,
                    "Transaction currency mismatch for ID ${expected.id}")

                if (expected.type == NormalizedTransactionType.BUY) {
                    assertEquals(expected.assetAmount.amount, it.assetAmount.amount, 0.00000001,
                        "Asset amount mismatch for ID ${expected.id}")
                    assertEquals(expected.assetAmount.unit, it.assetAmount.unit,
                        "Asset currency mismatch for ID ${expected.id}")
                }

                // Using string comparison for timestamps to avoid time zone issues
                assertEquals(
                    SimpleDateFormat("yyyy-MM-dd HH:mm").format(expected.timestamp),
                    SimpleDateFormat("yyyy-MM-dd HH:mm").format(it.timestamp),
                    "Timestamp mismatch for ID ${expected.id}"
                )
            }
        }

        // Validate that we have the correct number of deposits and trades
        val depositCount = actualTransactions.count { it.type == NormalizedTransactionType.DEPOSIT }
        val buyCount = actualTransactions.count { it.type == NormalizedTransactionType.BUY }

        assertEquals(10, depositCount, "Expected 10 deposit transactions")
        assertEquals(10, buyCount, "Expected 10 buy transactions")

        // Validate the total amount of BTC purchased
        val totalBtcPurchased = actualTransactions
            .filter { it.type == NormalizedTransactionType.BUY }
            .sumOf { it.assetAmount.amount }

        // The expected total based on the provided data
        val expectedBtcTotal = 0.00145097
        assertEquals(expectedBtcTotal, totalBtcPurchased, 0.00000001, "Total BTC purchased mismatch")
    }
}
