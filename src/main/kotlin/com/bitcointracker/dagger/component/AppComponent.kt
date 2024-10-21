package com.bitcointracker.dagger.component

import com.bitcointracker.dagger.module.ExternalClientModule
import com.bitcointracker.service.BackendService
import com.google.gson.Gson
import dagger.Component

@Component(modules = [
    ExternalClientModule::class,
])
interface AppComponent {
    fun getBackendService(): BackendService
    fun getGson(): Gson
}