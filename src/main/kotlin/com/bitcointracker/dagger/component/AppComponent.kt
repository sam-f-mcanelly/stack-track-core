package com.bitcointracker.dagger.component

import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.dagger.module.ConcurrencyModule
import com.bitcointracker.dagger.module.DatabaseModule
import com.bitcointracker.dagger.module.ExternalClientModule
import com.bitcointracker.dagger.module.FileProcessorModule
import com.bitcointracker.service.routes.MetadataRouteHandler
import com.bitcointracker.service.routes.RawDataRouteHandler
import com.bitcointracker.service.routes.TaxComputationRouteHandler
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [
    ConcurrencyModule::class,
    DatabaseModule::class,
    ExternalClientModule::class,
    FileProcessorModule::class,
])
interface AppComponent {
    fun getMetadataRouteHandler(): MetadataRouteHandler
    fun getRawDataRouteHandler(): RawDataRouteHandler
    fun getTaxComputationRouteHandler(): TaxComputationRouteHandler

    // Database should only be directly accessed in niteg tests
    fun getDatabase(): TransactionRepository
}