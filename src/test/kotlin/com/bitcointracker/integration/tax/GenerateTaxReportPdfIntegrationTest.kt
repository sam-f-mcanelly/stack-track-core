package com.bitcointracker.integration.tax

import com.bitcointracker.dagger.component.DaggerAppComponent
import com.bitcointracker.model.api.tax.TaxReportResult
import com.bitcointracker.model.api.tax.TaxType
import com.bitcointracker.model.api.tax.TaxableEventResult
import com.bitcointracker.model.api.tax.UsedBuyTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.module
import com.bitcointracker.util.jackson.ExchangeAmountDeserializer
import com.bitcointracker.util.jackson.ExchangeAmountSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat

class GenerateTaxReportPdfIntegrationTest {
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        // Initialize and configure the Jackson ObjectMapper
        objectMapper = ObjectMapper().apply {
            // Enable pretty printing
            enable(SerializationFeature.INDENT_OUTPUT)

            // Register Kotlin module
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
    }

    @Test
    fun testTaxReportPdfGeneration() = testApplication {
        // Set up the application with your DI component
        val appComponent = DaggerAppComponent.create()
        application {
            module(appComponent)
        }

        // Create a more comprehensive sample tax report
        val sampleTaxReport = createSampleTaxReport()

        // Send the tax report to the PDF generation endpoint
        val response = client.post("/api/tax/report/pdf") {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(sampleTaxReport))
        }

        // Basic response validation
        assertThat(response.status)
            .`as`("Response status")
            .isEqualTo(HttpStatusCode.OK)

        assertThat(response.headers[HttpHeaders.ContentType])
            .`as`("Content-Type header")
            .isEqualTo(ContentType.Application.Pdf.toString())

        assertThat(response.headers[HttpHeaders.ContentDisposition])
            .`as`("Content-Disposition header")
            .isNotNull()
            .contains("attachment")
            .contains(".pdf")

        // Get the PDF content and validate it
        val pdfInputStream = response.bodyAsChannel().toInputStream()
        val pdfBytes = pdfInputStream.readAllBytes()
        val pdfDocument: PDDocument = Loader.loadPDF(pdfBytes)
        val textStripper = PDFTextStripper()
        val pdfText = textStripper.getText(pdfDocument)

        // Close the document when done
        pdfDocument.close()

        // Title and header validation
        assertThat(pdfText)
            .`as`("PDF header validation")
            .contains("Stack Track Tax Report")
            .contains("Report ID: test-request-id")

        // Summary section validation - validate correct total calculations
        assertThat(pdfText)
            .`as`("PDF summary section validation")
            .contains("Summary")
            .contains("Total Proceeds:")
            .contains("$850.00")  // $500.00 + $350.00 = $850.00
            .contains("Total Cost Basis:")
            .contains("$500.00")  // $300.00 + $200.00 = $500.00
            .contains("Total Gain/Loss:")
            .contains("$350.00")  // $200.00 + $150.00 = $350.00

        // Validate transaction cards and hierarchical structure
        assertThat(pdfText)
            .`as`("Transaction structure validation")
            .contains("Sell Transaction: sell-transaction-1")
            .contains("Sell Transaction: sell-transaction-2")
            .contains("Used Buy Transactions")

        // Validate sell transaction details
        assertThat(pdfText)
            .`as`("Sell transaction details validation")
            .contains("Oct 15 2024")
            .contains("BTC")
            .contains("0.01000000")
            .contains("$500.00")

        // Validate buy transaction details
        assertThat(pdfText)
            .`as`("Buy transaction details validation")
            .contains("buy-transaction-1")
            .contains("May 10 2024")
            .contains("Amount Used")
            .contains("SHORT_TERM")
            .contains("LONG_TERM")  // From the second transaction
            .contains("Cost Basis: $300.00") // For first transaction
            .contains("Cost Basis: $200.00") // For second transaction

        // Validate transaction summaries with gain percentages
        assertThat(pdfText)
            .`as`("Transaction summary validation")
            .contains("Gain/Loss: $200.00") // For first transaction
            .contains("Return: 66.67%")     // $200/$300 * 100 = 66.67%
            .contains("Gain/Loss: $150.00") // For second transaction
            .contains("Return: 75.00%")     // $150/$200 * 100 = 75.00%

        // Validate uncovered amount warning
        assertThat(pdfText)
            .`as`("Uncovered amount validation")
            .contains("Warning: Uncovered amount")
            .contains("0.00100000 BTC")
            .contains("Estimated value: $50.00")
    }

    private fun createSampleTaxReport(): TaxReportResult {
        val dateFormat = SimpleDateFormat("MMM dd yyyy HH:mm:ss")

        // First transaction - fully covered sale
        val sellTransaction1 = NormalizedTransaction(
            id = "sell-transaction-1",
            source = TransactionSource.COINBASE_PRO_FILL,
            type = NormalizedTransactionType.SELL,
            transactionAmountFiat = ExchangeAmount(500.0, "USD"),
            fee = ExchangeAmount(1.50, "USD"),
            assetAmount = ExchangeAmount(0.01, "BTC"),
            assetValueFiat = ExchangeAmount(500.0, "USD"),
            timestamp = dateFormat.parse("Oct 15 2024 14:30:00"),
            timestampText = "Oct 15 2024 14:30:00",
            address = "",
            notes = ""
        )

        val buyTransaction1 = NormalizedTransaction(
            id = "buy-transaction-1",
            source = TransactionSource.COINBASE_PRO_FILL,
            type = NormalizedTransactionType.BUY,
            transactionAmountFiat = ExchangeAmount(300.0, "USD"),
            fee = ExchangeAmount(0.90, "USD"),
            assetAmount = ExchangeAmount(0.01, "BTC"),
            assetValueFiat = ExchangeAmount(300.0, "USD"),
            timestamp = dateFormat.parse("May 10 2024 09:15:00"),
            timestampText = "May 10 2024 09:15:00",
            address = "",
            notes = ""
        )

        val usedBuyTransaction1 = UsedBuyTransaction(
            transactionId = "buy-transaction-1",
            amountUsed = ExchangeAmount(0.01, "BTC"),
            costBasis = ExchangeAmount(300.0, "USD"),
            taxType = TaxType.SHORT_TERM,
            originalTransaction = buyTransaction1
        )

        val taxableEventResult1 = TaxableEventResult(
            sellTransactionId = "sell-transaction-1",
            proceeds = ExchangeAmount(500.0, "USD"),
            costBasis = ExchangeAmount(300.0, "USD"),
            gain = ExchangeAmount(200.0, "USD"),
            sellTransaction = sellTransaction1,
            usedBuyTransactions = listOf(usedBuyTransaction1)
        )

        // Second transaction - partially covered sale with uncovered amount
        val sellTransaction2 = NormalizedTransaction(
            id = "sell-transaction-2",
            source = TransactionSource.COINBASE_PRO_FILL,
            type = NormalizedTransactionType.SELL,
            transactionAmountFiat = ExchangeAmount(350.0, "USD"),
            fee = ExchangeAmount(1.25, "USD"),
            assetAmount = ExchangeAmount(0.007, "BTC"),
            assetValueFiat = ExchangeAmount(350.0, "USD"),
            timestamp = dateFormat.parse("Oct 20 2024 16:45:00"),
            timestampText = "Oct 20 2024 16:45:00",
            address = "",
            notes = ""
        )

        val buyTransaction2 = NormalizedTransaction(
            id = "buy-transaction-2",
            source = TransactionSource.COINBASE_STANDARD,
            type = NormalizedTransactionType.BUY,
            transactionAmountFiat = ExchangeAmount(200.0, "USD"),
            fee = ExchangeAmount(0.75, "USD"),
            assetAmount = ExchangeAmount(0.006, "BTC"),
            assetValueFiat = ExchangeAmount(200.0, "USD"),
            timestamp = dateFormat.parse("Aug 05 2023 10:20:00"),
            timestampText = "Aug 05 2023 10:20:00",
            address = "",
            notes = ""
        )

        val usedBuyTransaction2 = UsedBuyTransaction(
            transactionId = "buy-transaction-2",
            amountUsed = ExchangeAmount(0.006, "BTC"),
            costBasis = ExchangeAmount(200.0, "USD"),
            taxType = TaxType.LONG_TERM,
            originalTransaction = buyTransaction2
        )

        val taxableEventResult2 = TaxableEventResult(
            sellTransactionId = "sell-transaction-2",
            proceeds = ExchangeAmount(350.0, "USD"),
            costBasis = ExchangeAmount(200.0, "USD"),  // Updated to match usedBuyTransaction2
            gain = ExchangeAmount(150.0, "USD"),       // Updated to match proceeds - costBasis
            sellTransaction = sellTransaction2,
            usedBuyTransactions = listOf(usedBuyTransaction2),
            uncoveredSellAmount = ExchangeAmount(0.001, "BTC"),
            uncoveredSellValue = ExchangeAmount(50.0, "USD")
        )

        // Create the tax report result with multiple taxable events
        return TaxReportResult(
            requestId = "test-request-id",
            results = listOf(taxableEventResult1, taxableEventResult2)
        )
    }
}
