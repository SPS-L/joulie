package org.spsl.evtracker.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.usecase.NowProvider

/**
 * `AppWidgetProvider` instances are created by the platform via reflection
 * and aren't `@AndroidEntryPoint`-able. This Hilt entry point lets the
 * provider grab its dependencies via [dagger.hilt.android.EntryPointAccessors].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LastChargeWidgetEntryPoint {
    fun settingsReader(): SettingsReader
    fun carReader(): CarReader
    fun chargeEventQueries(): ChargeEventQueries
    fun nowProvider(): NowProvider
}
