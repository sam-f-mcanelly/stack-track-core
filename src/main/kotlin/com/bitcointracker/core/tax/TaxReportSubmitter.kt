package com.bitcointracker.core.tax

import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.model.api.tax.TaxReportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Handles sequential processing of tax report submissions.
 * Ensures thread-safe processing of submissions to prevent duplicate use of buy transactions.
 */
@Singleton
class TaxReportSubmitter @Inject constructor(
    private val transactionRepository: TransactionRepository,
    dispatcher: CoroutineDispatcher,
) {
    private val logger = LoggerFactory.getLogger(TaxReportSubmitter::class.java)

    // TODO: inject?
    private val submissionChannel = Channel<SubmissionRequest>(Channel.UNLIMITED)
    private val processingScope = CoroutineScope(dispatcher + Job())
    private val submissionStatuses = ConcurrentHashMap<String, MutableStateFlow<SubmissionStatus>>()

    init {
        startSubmissionProcessor()
    }

    /**
     * Submits a tax report result for processing.
     * Returns a unique submission ID that can be used to check the status.
     */
    fun submitAsync(result: TaxReportResult): String {
        val submissionId = generateSubmissionId()
        val statusFlow = MutableStateFlow<SubmissionStatus>(SubmissionStatus.InProgress)
        submissionStatuses[submissionId] = statusFlow

        submissionChannel.trySend(SubmissionRequest(submissionId, result))

        return submissionId
    }

    /**
     * Returns a StateFlow that can be used to observe the submission status.
     */
    fun getSubmissionStatus(submissionId: String) =
        submissionStatuses[submissionId]?.asStateFlow()

    private fun startSubmissionProcessor() {
        processingScope.launch {
            logger.info("Starting submission processor")
            while (isActive) {
                logger.info("Waiting for next submission")
                val request = submissionChannel.receiveCatching().getOrNull() ?: break
                logger.info("Received request: $request")
                try {
                    processSubmission(request)
                    logger.info("Processing completed, updating status to Completed")
                    updateSubmissionStatus(request.submissionId, SubmissionStatus.Completed)
                    logger.info("Status updated to Completed")
                } catch (e: Exception) {
                    logger.error("Processing failed", e)
                    updateSubmissionStatus(
                        request.submissionId,
                        SubmissionStatus.Failed(e.message ?: "Unknown error")
                    )
                } finally {
                    submissionStatuses.remove(request.submissionId)
                }
            }
        }
    }

    private suspend fun processSubmission(request: SubmissionRequest) {
        request.result.results
            .flatMap { result ->
                result.usedBuyTransactions.map { it.transactionId } + result.sellTransactionId
            }
            .distinct()
            .forEach { transactionId ->
                markTransactionFiled(transactionId)
            }
    }

    private suspend fun markTransactionFiled(transactionId: String) {
        try {
            val transaction = transactionRepository.getTransactionById(transactionId)
            if (transaction != null) {
                transactionRepository.updateTransaction(
                    transaction.copy(filedWithIRS = true)
                )
            } else {
                throw IllegalStateException("Transaction $transactionId not found")
            }
        } catch (e: Exception) {
            throw SubmissionProcessingException(
                "Failed to mark transaction $transactionId as filed",
                e
            )
        }
    }

    private fun updateSubmissionStatus(submissionId: String, status: SubmissionStatus) {
        submissionStatuses[submissionId]?.value = status
    }

    private fun generateSubmissionId(): String =
        "submission-${System.currentTimeMillis()}-${(0..999999).random()}"

    /**
     * Gracefully shuts down the submission processor.
     * Waits for all active submissions to complete.
     */
    fun shutdown() {
        submissionChannel.close()
        processingScope.cancel() // Cancel all coroutines

        // Optional: Wait briefly for graceful shutdown
        runBlocking {
            // Log active coroutines before timeout
            processingScope.coroutineContext.job.children.forEach { job ->
                logger.info("Active job during shutdown: $job")
            }

            withTimeout(5000) {
                processingScope.coroutineContext.job.join()
            }
        }
    }

    private data class SubmissionRequest(
        val submissionId: String,
        val result: TaxReportResult
    )

    sealed class SubmissionStatus {
        object InProgress : SubmissionStatus()
        object Completed : SubmissionStatus()
        data class Failed(val error: String) : SubmissionStatus()
    }
}

class SubmissionProcessingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)