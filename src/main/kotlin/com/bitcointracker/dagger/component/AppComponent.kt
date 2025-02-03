package com.bitcointracker.dagger.component

import com.bitcointracker.dagger.module.DatabaseModule
import com.bitcointracker.dagger.module.ExternalClientModule
import com.bitcointracker.service.BackendService
import com.google.gson.Gson
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [
    DatabaseModule::class,
    ExternalClientModule::class,
])
interface AppComponent {
    fun getBackendService(): BackendService
    fun getGson(): Gson
}