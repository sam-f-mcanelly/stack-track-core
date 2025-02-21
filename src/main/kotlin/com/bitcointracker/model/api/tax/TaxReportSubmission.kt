package com.bitcointracker.model.api.tax

import com.bitcointracker.model.internal.tax.TaxReportResult

data class TaxReportSubmission(
    val requestId: String,
    val submitted: Boolean,
    val taxReportResult: TaxReportResult,
)
