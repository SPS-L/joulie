package org.spsl.evtracker.domain.usecase

/**
 * Clock seam used by use cases that need to compute time-relative ranges (e.g. rolling
 * "Last 12 months"). JVM tests inject a fixed lambda so rolling-window tests are
 * deterministic; production binding (DispatcherModule) returns System.currentTimeMillis().
 */
fun interface NowProvider {
    fun nowMillis(): Long
}
