// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.coroutines

import javax.inject.Qualifier

/**
 * Qualifier for the CoroutineContext used to perform off-main aggregation work
 * inside use cases (e.g. ObserveChartsModelsUseCase). Production binds this to
 * Dispatchers.Default; JVM tests pass EmptyCoroutineContext so flowOn becomes
 * a no-op and the test scheduler stays in control.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AggregationDispatcher
