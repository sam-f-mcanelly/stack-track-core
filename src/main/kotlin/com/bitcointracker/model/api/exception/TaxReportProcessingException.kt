package com.bitcointracker.model.api.exception

import com.bitcointracker.model.api.tax.TaxableEventParameters

/**
 * Exception thrown when processing a tax report fails, containing the original event and cause.
 *
 * @property event The taxable event that failed to process
 * @property cause The underlying cause of the failure
 */
class TaxReportProcessingException(
    message: String,
    val event: TaxableEventParameters,
    cause: Throwable
) : Exception(message, cause)