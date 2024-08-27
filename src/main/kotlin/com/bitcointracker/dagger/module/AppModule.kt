package com.bitcointracker.dagger.module

import com.bitcointracker.core.local.UniversalFileLoader
import com.bitcointracker.service.BackendService
import dagger.Module
import dagger.Provides

@Module
class AppModule {
    @Provides
    fun provideMyService(
        fileLoader: UniversalFileLoader,
    ): BackendService {
        return BackendService(
            fileLoader = fileLoader,
        )
    }
}