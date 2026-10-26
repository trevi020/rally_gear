package io.bimmergestalt.idriveconnectaddons.rallygear

import android.content.Context

/**
 * Soglie RPM dei LED, modificabili dall'utente (popup che si apre tenendo premuta la fascia dei LED)
 * e salvate sul telefono. Sono sempre 5 (verde, verde, giallo, giallo, rosso): in orizzontale
 * i 10 LED si accendono a coppie con le stesse 5 soglie.
 */
object ShiftLightSettings {
    private const val PREFS = "rally_gear"
    private const val KEY_THRESHOLDS = "thresholds"

    /** Soglie predefinite: verde, verde, giallo, giallo, rosso */
    val DEFAULTS = intArrayOf(3500, 4200, 5000, 5700, 6500)

    const val MIN_RPM = 1000
    const val MAX_RPM = 9000

    /** Passo di modifica nel popup, e distanza minima tra due soglie consecutive */
    const val STEP = 100

    fun load(context: Context): IntArray {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THRESHOLDS, null)
            ?: return DEFAULTS.copyOf()
        val parsed = text.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
        return if (isValid(parsed)) parsed else DEFAULTS.copyOf()
    }

    fun save(context: Context, values: IntArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THRESHOLDS, values.joinToString(","))
            .apply()
    }

    /** 5 valori nell'intervallo consentito, crescenti di almeno STEP */
    private fun isValid(values: IntArray): Boolean {
        if (values.size != DEFAULTS.size) return false
        if (values.any { it < MIN_RPM || it > MAX_RPM }) return false
        for (i in 1 until values.size) {
            if (values[i] - values[i - 1] < STEP) return false
        }
        return true
    }
}
