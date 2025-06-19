package com.bitcointracker.dagger.module

import com.bitcointracker.core.parser.processor.CoinbaseAnnualFileProcessor
import com.bitcointracker.core.parser.processor.CoinbaseProFillsFileProcessor
import com.bitcointracker.core.parser.processor.FidelityAccountStatementFileProcessor
import com.bitcointracker.core.parser.processor.FileProcessor
import com.bitcointracker.core.parser.processor.StrikeAnnualFileProcessor
import com.bitcointracker.core.parser.processor.StrikeMonthlyFileProcessor
import com.bitcointracker.core.parser.processor.StrikeV2MonthlyFileProcessor
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/**
 * Dagger module for binding FileProcessors to their corresponding FileTypes.
 * Uses multibinding to create a map of file types to processors.
 *
 * @see com.bitcointracker.model.internal.file.FileType
 */
@Module
abstract class FileProcessorModule {

    @Binds
    @IntoMap
    @StringKey("STRIKE_ANNUAL")
    abstract fun bindStrikeAnnualFileProcessor(processor: StrikeAnnualFileProcessor): FileProcessor

    @Binds
    @IntoMap
    @StringKey("STRIKE_MONTHLY")
    abstract fun bindStrikeMonthlyFileProcessor(processor: StrikeMonthlyFileProcessor): FileProcessor

    @Binds
    @IntoMap
    @StringKey("STRIKE_MONTHLY_V2")
    abstract fun bindStrikeMonthlyV2FileProcessor(processor: StrikeV2MonthlyFileProcessor): FileProcessor

    @Binds
    @IntoMap
    @StringKey("COINBASE_PRO_FILLS")
    abstract fun bindCoinbaseProFillsFileProcessor(processor: CoinbaseProFillsFileProcessor): FileProcessor

    @Binds
    @IntoMap
    @StringKey("COINBASE_ANNUAL")
    abstract fun bindCoinbaseAnnualFileProcessor(processor: CoinbaseAnnualFileProcessor): FileProcessor

    @Binds
    @IntoMap
    @StringKey("FIDELITY")
    abstract fun fidelityAccountStatementFileProcessor(processor: FidelityAccountStatementFileProcessor): FileProcessor
}