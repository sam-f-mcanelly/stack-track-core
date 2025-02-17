package com.bitcointracker.model.internal.tax

/**
 * Contains the results of processing a tax report request.
 *
 * @property requestId The ID of the original request
 * @property results List of results for each processed taxable event
 */
data class TaxReportResult(
    val requestId: String,
    val results: List<TaxableEventResult>
)