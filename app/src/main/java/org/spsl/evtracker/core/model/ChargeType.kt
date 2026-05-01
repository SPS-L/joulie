package org.spsl.evtracker.core.model

/**
 * Charging-mode enum stored on every [org.spsl.evtracker.data.local.entity.ChargeEventEntity].
 *
 * The current UI only exposes AC vs DC (one toggle button each); the DC
 * button maps to [DC_FAST]. [DC_ULTRA] is forward-compat: declared today,
 * unreached by any user-facing code, surfaced in a future task that
 * differentiates rapid vs ultra-rapid DC chargers.
 *
 * The string column on disk and on the wire still uses the enum's `name`
 * (`"AC"` / `"DC_FAST"` / `"DC_ULTRA"`), and the legacy `"DC"` value
 * (Room v3, backup_version = 3) is decoded as [DC_FAST] by [parseLegacy].
 */
enum class ChargeType {
    AC,
    DC_FAST,
    DC_ULTRA,
    ;

    /** True for any DC variant. The AC vs DC UI distinction collapses [DC_FAST] and [DC_ULTRA]. */
    val isDc: Boolean get() = this != AC

    /** Short label used in the History badge / chart legends. */
    fun displayLabel(): String = if (this == AC) "AC" else "DC"

    companion object {
        /**
         * Decode a stored / serialized chargeType string.
         * - `"AC"` → [AC]
         * - `"DC"` → [DC_FAST] (legacy v3 row alias; preserved for backups + migrated rows)
         * - `"DC_FAST"` → [DC_FAST]
         * - `"DC_ULTRA"` → [DC_ULTRA]
         * - anything else → [AC] (defensive default; v3 rows never contained these)
         */
        fun parseLegacy(s: String): ChargeType = when (s) {
            "AC" -> AC
            "DC" -> DC_FAST
            "DC_FAST" -> DC_FAST
            "DC_ULTRA" -> DC_ULTRA
            else -> AC
        }
    }
}
