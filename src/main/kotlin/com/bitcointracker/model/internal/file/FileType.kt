package com.bitcointracker.model.internal.file

enum class FileType {
    // Exchange
    STRIKE_ANNUAL,
    STRIKE_MONTHLY,
    STRIKE_MONTHLY_V2,
    COINBASE_PRO_FILLS,
    COINBASE_ANNUAL,

    // Data
    BTC_HISTORICAL_DATA,
}