package com.bitcointracker.core.tax

import javax.inject.Inject

class TransactionTrackerFactory @Inject constructor() {
    fun create() = TransactionTracker(mutableMapOf())
}