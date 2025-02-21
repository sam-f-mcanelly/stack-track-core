package com.bitcointracker.dagger.module

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.util.jackson.ExchangeAmountDeserializer
import com.bitcointracker.util.jackson.ExchangeAmountSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient

@Module
class ExternalClientModule {
    @Provides
    fun provideOkHttpClient() = OkHttpClient()

    @Provides
    fun provideObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(
                KotlinModule.Builder()
                    .configure(KotlinFeature.NullToEmptyCollection, true)
                    .configure(KotlinFeature.NullToEmptyMap, true)
                    .configure(KotlinFeature.NullIsSameAsDefault, false)
                    .configure(KotlinFeature.SingletonSupport, false)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build()
            )

            val module = SimpleModule().apply {
                addSerializer(ExchangeAmount::class.java, ExchangeAmountSerializer())
                addDeserializer(ExchangeAmount::class.java, ExchangeAmountDeserializer())
            }
            registerModule(module)
        }
    }
}