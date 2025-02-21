package com.bitcointracker.dagger.component

import com.bitcointracker.dagger.module.ConcurrencyModule
import com.bitcointracker.dagger.module.DatabaseModule
import com.bitcointracker.dagger.module.ExternalClientModule
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
])
interface AppComponent {
    fun getMetadataRouteHandler(): MetadataRouteHandler
    fun getRawDataRouteHandler(): RawDataRouteHandler
    fun getTaxComputationRouteHandler(): TaxComputationRouteHandler
}