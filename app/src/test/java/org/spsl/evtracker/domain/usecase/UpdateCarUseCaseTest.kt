// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeWidgetRefresher

class UpdateCarUseCaseTest {

    private fun newCase(initial: List<CarEntity>): Triple<UpdateCarUseCase, FakeCarRepository, FakeBackupScheduler> {
        val repo = FakeCarRepository(initial = initial)
        val scheduler = FakeBackupScheduler()
        val useCase = UpdateCarUseCase(repo, repo, scheduler, FakeWidgetRefresher())
        return Triple(useCase, repo, scheduler)
    }

    @Test
    fun blankName_returnsNameBlank() = runTest {
        val (useCase, repo, _) = newCase(
            listOf(CarEntity(id = 1L, name = "Tesla", createdAt = 0L)),
        )
        val result = useCase(1L, CarFormState(name = "  "))
        assertTrue(result is UpdateCarUseCase.Result.NameBlank)
        // Row untouched.
        assertEquals("Tesla", repo.current().first().name)
    }

    @Test
    fun unknownCarId_returnsNotFound() = runTest {
        val (useCase, _, _) = newCase(emptyList())
        val result = useCase(99L, CarFormState(name = "Anything"))
        assertTrue(result is UpdateCarUseCase.Result.NotFound)
    }

    @Test
    fun persistsAllFields_including_wltp() = runTest {
        val (useCase, repo, scheduler) = newCase(
            listOf(
                CarEntity(
                    id = 7L,
                    name = "Old",
                    make = "OldMake",
                    model = "OldModel",
                    year = 2020,
                    batteryKwh = 40.0,
                    wltpKwhPer100km = null,
                    createdAt = 1_000L,
                ),
            ),
        )
        val result = useCase(
            7L,
            CarFormState(
                name = "New",
                make = "Tesla",
                model = "Model 3",
                year = "2024",
                batteryKwh = "75.0",
                wltpKwhPer100km = 14.0,
            ),
        )
        assertTrue(result is UpdateCarUseCase.Result.Success)
        val row = repo.current().first()
        assertEquals("New", row.name)
        assertEquals("Tesla", row.make)
        assertEquals("Model 3", row.model)
        assertEquals(2024, row.year)
        assertEquals(75.0, row.batteryKwh!!, 0.0001)
        assertEquals(14.0, row.wltpKwhPer100km!!, 0.0001)
        // createdAt preserved across the update.
        assertEquals(1_000L, row.createdAt)
        // Backup enqueued exactly once.
        assertEquals(1, scheduler.enqueueCount)
    }
}
