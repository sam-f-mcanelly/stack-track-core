package com.bitcointracker.service.routes

import com.bitcointracker.core.tax.TaxReportGenerator
import com.bitcointracker.core.tax.TaxReportPdfGenerator
import com.bitcointracker.core.tax.TaxReportSubmitter
import com.bitcointracker.model.api.tax.TaxReportRequest
import com.bitcointracker.model.api.tax.TaxReportResult
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import org.slf4j.LoggerFactory
import jakarta.inject.Inject

/**
 * Handles HTTP routes related to tax computation and report submission.
 *
 * This class processes incoming tax report requests, generates tax reports,
 * and manages their submission through the appropriate services.
 *
 * @property taxReportGenerator class responsible for generating tax reports
 * @property taxReportSubmitter class responsible for submitting generated tax reports
 * @property taxReportPdfGenerator class responsible for generating downloadable PDFs of tax reports
 * @property objectMapper jackson object mapper
 *
 * @throws IllegalArgumentException if the tax report request is malformed
 * @throws TaxComputationException if there's an error during tax computation
 */
class TaxComputationRouteHandler @Inject constructor(
    private val taxReportGenerator: TaxReportGenerator,
    private val taxReportSubmitter: TaxReportSubmitter,
    private val taxReportPdfGenerator: TaxReportPdfGenerator,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TaxComputationRouteHandler::class.java)
    }

    /**
     * Processes an incoming tax report submission request.
     *
     * Handles the HTTP request for tax report submission,
     * validates the input, generates the report, and returns the appropriate response.
     *
     * @param call The ApplicationCall containing the request details
     * @return Unit, but responds to the call with either the generated tax report or an error
     */
    suspend fun submitTaxReportRequest(call: ApplicationCall) {
        try {
            val taxReportRequest = call.receive<TaxReportRequest>()
            logger.info("received tax report request: $taxReportRequest")
            val result = taxReportGenerator.processTaxReport(taxReportRequest)
            call.respond(result)
        } catch (e: Exception) {
            logger.error("Error while processing tax report request", e)
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
        }
    }

    /**
     * Generates a PDF of the Tax Report Result.
     *
     * @param call The ApplicationCall containing the tax report result
     */
    suspend fun generateTaxReportPdf(call: ApplicationCall) {
        val taxReport = call.receive<TaxReportResult>()

        val pdfBytes = taxReportPdfGenerator.generateTaxReportPdf(taxReport)

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                "tax-report-${taxReport.requestId}.pdf"
            ).toString()
        )
        call.respondBytes(pdfBytes, ContentType.Application.Pdf)
    }
}
