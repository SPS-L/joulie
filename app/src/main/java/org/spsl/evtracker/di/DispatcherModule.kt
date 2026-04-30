package org.spsl.evtracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import org.spsl.evtracker.core.coroutines.AggregationDispatcher
import org.spsl.evtracker.domain.usecase.NowProvider
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @AggregationDispatcher
    fun provideAggregationContext(): CoroutineContext = Dispatchers.Default

    @Provides
    @Singleton
    fun provideNowProvider(): NowProvider = NowProvider { System.currentTimeMillis() }
}
