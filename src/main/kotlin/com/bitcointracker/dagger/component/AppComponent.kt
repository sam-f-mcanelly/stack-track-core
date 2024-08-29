package com.bitcointracker.dagger.component

import com.bitcointracker.dagger.module.ExternalClientModule
import com.bitcointracker.service.BackendService
import dagger.Component

@Component(modules = [
    ExternalClientModule::class,
])
interface AppComponent {
    fun getBackendService(): BackendService
}