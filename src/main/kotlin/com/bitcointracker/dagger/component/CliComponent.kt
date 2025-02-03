package com.bitcointracker.dagger.component

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.core.parser.report.ReportGenerator
import com.bitcointracker.dagger.module.DatabaseModule
import com.bitcointracker.dagger.module.ExternalClientModule
import com.bitcointracker.external.client.CoinbaseClient
import dagger.Component
import javax.inject.Singleton

// TODO: find a use for this
@Singleton
@Component(modules = [
    DatabaseModule::class,
    ExternalClientModule::class,
])
interface CliComponent {
    fun getTransactionAnalyzer(): NormalizedTransactionAnalyzer
    fun getReportGenerator(): ReportGenerator
    fun getFileLoader(): UniversalFileLoader
    fun getCoinbaseClient(): CoinbaseClient
}