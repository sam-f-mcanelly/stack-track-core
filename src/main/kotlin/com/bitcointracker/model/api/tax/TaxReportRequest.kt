package com.bitcointracker.model.api.tax

data class TaxReportRequest(
    val requestId: String,
    val taxableEvents: List<TaxableEventParameters>
)

data class TaxableEventParameters(
    val sellId: String,
    val taxTreatment: TaxTreatment,
    val buyTransactionIds: List<String>? = null,
)
