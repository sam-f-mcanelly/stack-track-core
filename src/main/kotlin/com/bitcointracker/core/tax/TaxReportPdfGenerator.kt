package com.bitcointracker.core.tax

import com.bitcointracker.model.api.tax.TaxReportResult
import com.bitcointracker.model.api.tax.TaxType
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Div
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Text
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

        document.setFontSize(8f)

        // Add title and header information
        document.add(
            Paragraph("Stack Track Tax Report")
                .setFontSize(18f)
                .simulateBold()
                .setTextAlignment(TextAlignment.CENTER)
        )

        document.add(
            Paragraph("Report ID: ${report.requestId}")
                .setFontSize(10f)
        )

        document.add(
            Paragraph("Generated: ${java.time.LocalDateTime.now()}")
                .setFontSize(10f)
        )

        document.add(Paragraph("\n"))

        // Create summary section
        document.add(
            Paragraph("Summary")
                .setFontSize(14f)
                .simulateBold()
        )

        // Add summary data
        val totalProceeds = report.results.sumOf { it.proceeds.amount }
        val totalCostBasis = report.results.sumOf { it.costBasis.amount }
        val totalGain = report.results.sumOf { it.gain.amount }

        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f)))
            .useAllAvailableWidth()

        summaryTable.addCell(Cell().add(Paragraph("Total Proceeds:")).setBorder(null))
        summaryTable.addCell(
            Cell().add(
                Paragraph(
                    "$${
                        String.format(
                            "%.2f",
                            totalProceeds
                        )
                    } ${report.results.firstOrNull()?.proceeds?.unit ?: "USD"}"
                )
            ).setBorder(null)
        )

        summaryTable.addCell(Cell().add(Paragraph("Total Cost Basis:")).setBorder(null))
        summaryTable.addCell(
            Cell().add(
                Paragraph(
                    "$${
                        String.format(
                            "%.2f",
                            totalCostBasis
                        )
                    } ${report.results.firstOrNull()?.costBasis?.unit ?: "USD"}"
                )
            ).setBorder(null)
        )

        summaryTable.addCell(Cell().add(Paragraph("Total Gain/Loss:")).setBorder(null))
        summaryTable.addCell(
            Cell().add(
                Paragraph(
                    "$${
                        String.format(
                            "%.2f",
                            totalGain
                        )
                    } ${report.results.firstOrNull()?.gain?.unit ?: "USD"}"
                )
            ).setBorder(null)
        )

        document.add(summaryTable)
        document.add(Paragraph("\n"))

        // Create detailed transactions section with hierarchical layout
        document.add(
            Paragraph("Detailed Transactions")
                .setFontSize(12f)
                .simulateBold()
        )

        // Loop through each sell transaction
        report.results.forEachIndexed { index, event ->
            // Add a divider between transactions (except before the first one)
            if (index > 0) {
                document.add(Paragraph("\n"))
                document.add(Div().setHeight(1f).setBackgroundColor(ColorConstants.LIGHT_GRAY).setMarginBottom(2f))
                document.add(Paragraph("\n"))
            }

            // Create a card-like container for each transaction
            val transactionCard = Div()
                .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 1f))
                .setBackgroundColor(ColorConstants.WHITE)
                .setPadding(10f)
                .setMargin(5f)

            // Sell transaction header
            transactionCard.add(
                Paragraph("Sell Transaction: ${event.sellTransactionId}")
                    .setFontSize(10f)
                    .simulateBold()
            )

            // Sell transaction details
            val sellDetailsTable = Table(UnitValue.createPercentArray(floatArrayOf(8f, 8f, 8f, 8f, 8f, 8f)))
                .useAllAvailableWidth()
                .setBorder(Border.NO_BORDER)

            // Add sell transaction details header
            sellDetailsTable.addHeaderCell(createHeaderCell("Source"))
            sellDetailsTable.addHeaderCell(createHeaderCell("Transaction ID"))
            sellDetailsTable.addHeaderCell(createHeaderCell("Date"))
            sellDetailsTable.addHeaderCell(createHeaderCell("Amount"))
            sellDetailsTable.addHeaderCell(createHeaderCell("Price"))
            sellDetailsTable.addHeaderCell(createHeaderCell("Proceeds"))

            // Add sell transaction details
            sellDetailsTable.addCell(createCell(event.sellTransaction.source.name))
            sellDetailsTable.addCell(createCell(event.sellTransaction.id))
            sellDetailsTable.addCell(createCell(event.sellTransaction.timestampText))
            sellDetailsTable.addCell(createCell("${String.format("%,.8f", event.sellTransaction.assetAmount.amount)} ${event.sellTransaction.assetAmount.unit}"))
            val pricePerUnit = event.proceeds.amount / event.sellTransaction.assetAmount.amount
            sellDetailsTable.addCell(createCell("$${String.format("%,.2f", pricePerUnit)}"))
            sellDetailsTable.addCell(createCell("$${String.format("%,.2f", event.proceeds.amount)}"))

            transactionCard.add(sellDetailsTable)

            // Buy transactions used section
            transactionCard.add(
                Paragraph("\nUsed Buy Transactions")
                    .setFontSize(10f)
                    .simulateBold()
            )

            // Buy transactions table
            val buyTable = Table(UnitValue.createPercentArray(floatArrayOf(8f, 8f, 8f, 8f, 8f, 8f, 8f)))
                .useAllAvailableWidth()

            // Add buy transactions header
            buyTable.addHeaderCell(createHeaderCell("Source"))
            buyTable.addHeaderCell(createHeaderCell("Transaction ID"))
            buyTable.addHeaderCell(createHeaderCell("Date"))
            buyTable.addHeaderCell(createHeaderCell("Amount Used"))
            buyTable.addHeaderCell(createHeaderCell("Price"))
            buyTable.addHeaderCell(createHeaderCell("Cost Basis"))
            buyTable.addHeaderCell(createHeaderCell("Tax Type"))

            // Add buy transactions
            event.usedBuyTransactions.forEach { buyTx ->
                buyTable.addCell(createCell(buyTx.originalTransaction.source.name))
                buyTable.addCell(createCell(buyTx.transactionId))
                buyTable.addCell(createCell(buyTx.originalTransaction.timestampText))
                buyTable.addCell(createCell("${String.format("%,.8f", buyTx.amountUsed.amount)} ${buyTx.amountUsed.unit}"))
                val buyPrice = buyTx.costBasis.amount / buyTx.amountUsed.amount
                buyTable.addCell(createCell("$${String.format("%,.2f", buyPrice)}"))
                buyTable.addCell(createCell("$${String.format("%,.2f", buyTx.costBasis.amount)}"))

                // Add tax type with appropriate styling
                val taxTypeCell = Cell()
                    .add(
                        Paragraph(buyTx.taxType.name)
                            .setFontSize(8f)
                            .setTextAlignment(TextAlignment.CENTER)
                    )
                    .setBackgroundColor(
                        if (buyTx.taxType == TaxType.LONG_TERM)
                            ColorConstants.LIGHT_GRAY
                        else
                            DeviceRgb (255, 240, 240) // Light red for short term
                )
                buyTable.addCell(taxTypeCell)
            }

            transactionCard.add(buyTable)

            // Summary for this transaction
            val transactionSummaryTable = Table(UnitValue.createPercentArray(floatArrayOf(14f, 14f, 14f, 14f)))
                .useAllAvailableWidth()
                .setMarginTop(10f)

            transactionSummaryTable.addCell(
                createSummaryCell(
                    "Proceeds:",
                    "$${String.format("%,.2f", event.proceeds.amount)}"
                )
            )
            transactionSummaryTable.addCell(
                createSummaryCell(
                    "Cost Basis:",
                    "$${String.format("%,.2f", event.costBasis.amount)}"
                )
            )

            // Style the gain/loss cell based on whether it's a gain or loss
            val isGain = event.gain.amount >= 0
            val gainLossCell = createSummaryCell(
                "Gain/Loss:",
                "$${String.format("%,.2f", event.gain.amount)}"
            )

            if (isGain) {
                gainLossCell.setBackgroundColor(DeviceRgb(240, 255, 240)) // Light green
            } else {
                gainLossCell.setBackgroundColor(DeviceRgb(255, 240, 240)) // Light red
            }

            transactionSummaryTable.addCell(gainLossCell)

            // Calculate and add gain percentage
            val gainPercentage = if (event.costBasis.amount != 0.0) {
                (event.gain.amount / event.costBasis.amount) * 100
            } else {
                0.0
            }

            transactionSummaryTable.addCell(
                createSummaryCell(
                    "Return:",
                    String.format("%.2f%%", gainPercentage)
                )
            )

            transactionCard.add(transactionSummaryTable)

            // Add uncovered sell amount warning if applicable
            if (event.uncoveredSellAmount != null && event.uncoveredSellAmount.amount > 0) {
                val warningDiv = Div()
                    .setBackgroundColor(DeviceRgb(255, 250, 230)) // Light yellow
                .setPadding(5f)
                    .setMarginTop(10f)
                    .setBorder(SolidBorder(DeviceRgb(255, 200, 0), 1f))

                warningDiv.add(
                    Paragraph(
                        "⚠️ Warning: Uncovered amount of ${
                            String.format(
                                "%.8f",
                                event.uncoveredSellAmount.amount
                            )
                        } ${event.uncoveredSellAmount.unit}"
                    )
                        .setFontSize(9f)
                        .setFontColor(DeviceRgb(150, 100, 0)
                )
                )

                if (event.uncoveredSellValue != null) {
                    warningDiv.add(
                        Paragraph("Estimated value: $${String.format("%.2f", event.uncoveredSellValue.amount)}")
                            .setFontSize(9f)
                            .setFontColor(DeviceRgb(150, 100, 0)
                        )
                    )
                }

                transactionCard.add(warningDiv)
            }

            document.add(transactionCard)
        }

        document.close()

        return outputStream.toByteArray()
    }

    // Helper methods for creating consistent cells
    private fun createHeaderCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text).setFontSize(8f).simulateBold())
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
    }

    private fun createCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text).setFontSize(8f))
            .setTextAlignment(TextAlignment.CENTER)
    }

    private fun createSummaryCell(label: String, value: String): Cell {
        return Cell()
            .add(
                Paragraph()
                    .add(Text(label).setFontSize(8f))
                    .add(Text(" ").setFontSize(8f))
                    .add(Text(value).setFontSize(9f).simulateBold())
            )
            .setTextAlignment(TextAlignment.CENTER)
    }
}
