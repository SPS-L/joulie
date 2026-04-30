package org.spsl.evtracker.core.model

data class LocationSlice(
    val label: String,
    val count: Int,
) {
    val isOther: Boolean get() = label == OTHER_KEY

    companion object {
        // Constructed via Char(0) so the source is unambiguous in markdown
        // (a literal NUL char would render as a blank space and could be
        //  silently turned into a real space under copy/paste, breaking the
        //  collision-proof contract). `@JvmField val` rather than `const val`
        //  because Char(0) is not a compile-time constant expression. Android
        //  EditText filters NUL out of IME input, so user-typed labels can
        //  never start with this character — the sentinel is collision-proof
        //  on its own merits, not via a paired trim() invariant.
        @JvmField val OTHER_KEY: String = "${Char(0)}__other__"
    }
}
