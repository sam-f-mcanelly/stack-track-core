package com.bitcointracker.dagger.module

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
class ConcurrencyModule {

    @Provides
    fun provideDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
