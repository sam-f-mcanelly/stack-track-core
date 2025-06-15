package com.bitcointracker.core.tax

import jakarta.inject.Inject

class TransactionTrackerFactory @Inject constructor() {
    fun create() = TransactionTracker(mutableMapOf())
}