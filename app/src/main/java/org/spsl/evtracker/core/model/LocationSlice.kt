// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class LocationSlice(
    val label: String,
    val count: Int,
) {
    val isOther: Boolean get() = label == OTHER_KEY

    companion object {
        // `Char(0)` keeps the leading byte unambiguous in source (a literal
        // NUL renders as a space and would silently turn into a real space
        // under copy/paste). `@JvmField val` rather than `const val` because
        // `Char(0)` is not a compile-time constant expression. Android
        // `EditText` filters NUL out of IME input, so user-typed labels can
        // never start with this character — the sentinel is collision-proof
        // on its own merits.
        @JvmField val OTHER_KEY: String = "${Char(0)}__other__"
    }
}
