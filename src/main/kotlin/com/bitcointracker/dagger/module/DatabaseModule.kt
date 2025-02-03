package com.bitcointracker.dagger.module

import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.core.database.H2TransactionRepository
import dagger.Module
import dagger.Provides
import org.jetbrains.exposed.sql.Database
import javax.inject.Singleton

@Module
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(): Database = Database.connect(
            url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )

    @Provides
    @Singleton
    fun provideTransactionRepository(
        database: Database,
        transactionMetadataCache: TransactionMetadataCache
    ): TransactionRepository = H2TransactionRepository(database, transactionMetadataCache)
}