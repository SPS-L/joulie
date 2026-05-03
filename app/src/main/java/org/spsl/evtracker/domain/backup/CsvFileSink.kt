// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.backup

import android.net.Uri
import java.io.Writer

/**
 * Allocates a CSV file for the given car/timestamp, invokes [body] to write its contents,
 * then returns a shareable URI for the file. Production: external Downloads + FileProvider.
 * Tests: ExportCsvUseCaseTest calls the writeCsv() helper directly without going through
 * the sink at all.
 */
interface CsvFileSink {
    suspend fun write(carName: String, body: (Writer) -> Unit): Uri
}
