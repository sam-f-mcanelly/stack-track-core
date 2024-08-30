package com.bitcointracker.dagger.module

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
class ExternalClientModule {
    @Provides
    fun provideOkHttpClient() = OkHttpClient()

    @Provides
    fun provideGson() = Gson()
}