package com.bitcointracker.dagger.component

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.local.UniversalFileLoader
import com.bitcointracker.core.local.report.ReportGenerator
import com.bitcointracker.dagger.module.ExternalClientModule
import com.bitcointracker.external.client.CoinbaseClient
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [
    ExternalClientModule::class,
])
interface CliComponent {
    fun getTransactionAnalyzer(): NormalizedTransactionAnalyzer
    fun getReportGenerator(): ReportGenerator
    fun getFileLoader(): UniversalFileLoader
    fun getCoinbaseClient(): CoinbaseClient
}