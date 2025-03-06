package com.bitcointracker.core.tax

import com.bitcointracker.model.api.tax.TaxReportResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class TaxReportPdfGenerator @Inject constructor() {

    /**
     * Generate a PDF of the Tax Report Result so the user
     * can print it and add it to their records.
     *
     * @param report the TaxReportResult
     */
    fun generateTaxReportPdf(report: TaxReportResult): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // Add title and header information
        document.add(Paragraph("Cryptocurrency Tax Report")
            .setFontSize(20f)
            .simulateBold()
            .setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph("Report ID: ${report.requestId}")
            .setFontSize(12f))

        document.add(Paragraph("Generated: ${java.time.LocalDateTime.now()}")
            .setFontSize(12f))

        document.add(Paragraph("\n"))

        // Create summary section
        document.add(Paragraph("Summary")
            .setFontSize(16f)
            .simulateBold())

        // Add summary data
        val totalProceeds = report.results.sumOf { it.proceeds.amount }
        val totalCostBasis = report.results.sumOf { it.costBasis.amount }
        val totalGain = report.results.sumOf { it.gain.amount }

        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f)))
            .useAllAvailableWidth()

        summaryTable.addCell(Cell().add(Paragraph("Total Proceeds:")).setBorder(null))
        summaryTable.addCell(Cell().add(Paragraph("$${String.format("%.2f", totalProceeds)} ${report.results.firstOrNull()?.proceeds?.unit ?: "USD"}")).setBorder(null))

        summaryTable.addCell(Cell().add(Paragraph("Total Cost Basis:")).setBorder(null))
        summaryTable.addCell(Cell().add(Paragraph("$${String.format("%.2f", totalCostBasis)} ${report.results.firstOrNull()?.costBasis?.unit ?: "USD"}")).setBorder(null))

        summaryTable.addCell(Cell().add(Paragraph("Total Gain/Loss:")).setBorder(null))
        summaryTable.addCell(Cell().add(Paragraph("$${String.format("%.2f", totalGain)} ${report.results.firstOrNull()?.gain?.unit ?: "USD"}")).setBorder(null))

        document.add(summaryTable)
        document.add(Paragraph("\n"))

        // Create detailed transactions table
        document.add(Paragraph("Detailed Transactions")
            .setFontSize(16f)
            .simulateBold())

        val transactionsTable = Table(UnitValue.createPercentArray(floatArrayOf(16f, 14f, 14f, 14f, 14f, 14f, 14f)))
            .useAllAvailableWidth()

        // Add table headers
        arrayOf("Transaction ID", "Date", "Type", "Asset Amount", "Proceeds", "Cost Basis", "Gain/Loss").forEach {
            transactionsTable.addHeaderCell(Cell().add(Paragraph(it).simulateBold()))
        }

        // Add transaction rows
        report.results.forEach { event ->
            transactionsTable.addCell(event.sellTransactionId)
            transactionsTable.addCell(event.sellTransaction.timestampText)
            transactionsTable.addCell(event.sellTransaction.type.toString())
            transactionsTable.addCell("${String.format("%.8f", event.sellTransaction.assetAmount.amount)} ${event.sellTransaction.assetAmount.unit}")
            transactionsTable.addCell("$${String.format("%.2f", event.proceeds.amount)}")
            transactionsTable.addCell("$${String.format("%.2f", event.costBasis.amount)}")
            transactionsTable.addCell("$${String.format("%.2f", event.gain.amount)}")
        }

        document.add(transactionsTable)
        document.add(Paragraph("\n"))

        // Add Form 8949 instructions
        document.add(Paragraph("IRS Form 8949 Instructions")
            .setFontSize(16f)
            .simulateBold())

        document.add(Paragraph("""
        To report these transactions on your taxes using IRS Form 8949:
        
        1. For each transaction listed in this report:
           a. Enter the name of the cryptocurrency (e.g., "Bitcoin") in column (a)
           b. Enter "Various" for the acquisition date in column (b)
           c. Enter the sale date from this report in column (c)
           d. Enter the proceeds amount from this report in column (d)
           e. Enter the cost basis amount from this report in column (e)
           f. Column (f) should be left blank
           g. Enter the gain or loss amount from this report in column (h)
        
        2. Use a separate Form 8949 for each holding period category:
           - Part I for short-term transactions (held one year or less)
           - Part II for long-term transactions (held more than one year)
        
        3. Check Box C in Part I or Box F in Part II since cryptocurrency transactions are not reported on Form 1099-B.
        
        4. Sum up the amounts in columns (d), (e), and (h) at the bottom of each form.
        
        5. Transfer the totals to Schedule D (Form 1040).
    """.trimIndent()))

        document.close()

        return outputStream.toByteArray()
    }
}
