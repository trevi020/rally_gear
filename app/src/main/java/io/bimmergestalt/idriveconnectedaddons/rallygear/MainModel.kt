package io.bimmergestalt.idriveconnectaddons.rallygear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlin.math.roundToInt

/**
 * Espone marcia, giri motore, velocita', temperature e una riga di stato
 * leggendo i dati CDS di AAIdrive.
 * Ogni sorgente si attiva solo se qualcuno la osserva e la schermata e' visibile:
 * velocita' e temperature vengono osservate solo dal layout orizzontale.
 *
 * Marcia, giri e velocita' si aggiornano in tempo reale (alla massima frequenza di AAIdrive);
 * le temperature vengono lette solo ogni TEMP_POLL_INTERVAL_MS, per non appesantire AAIdrive.
 */
class MainModel(app: Application) : AndroidViewModel(app) {

    private val gearSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_GEAR)
    private val rpmSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_RPM)
    private val speedSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_SPEED)
    private val tempSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_TEMP, TEMP_POLL_INTERVAL_MS)

    /** Testo da mostrare nel display a 7 segmenti: "N", "R", "P", "0", "1", "2", ... oppure "-" */
    val gear: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(gearSource) { state ->
            value = if (state is CdsState.Value) gearLabel(rawGear(state)) else NO_GEAR
        }
    }

    /** Giri motore (0 se il dato non e' disponibile) */
    val rpm: LiveData<Int> = MediatorLiveData<Int>().apply {
        addSource(rpmSource) { state ->
            value = if (state is CdsState.Value) rawRpm(state) ?: 0 else 0
        }
    }

    /** Velocita' in km/h (unita' indicata dalla documentazione CDS), oppure "--" */
    val speed: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(speedSource) { state ->
            value = if (state is CdsState.Value) numberText(state.values["speedActual"]) else NO_VALUE
        }
    }

    /** Riga "ENG T: 92°C" con la temperatura del motore */
    val engTemp: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(tempSource) { state -> value = "ENG T: " + temperatureText(state, "engine") }
    }

    /** Riga "OIL T: 105°C" con la temperatura dell'olio */
    val oilTemp: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(tempSource) { state -> value = "OIL T: " + temperatureText(state, "oil") }
    }

    /** Riga di stato in basso, utile per capire se la connessione con AAIdrive funziona */
    val status: LiveData<String> = MediatorLiveData<String>().apply {
        val update = { value = describe(gearSource.value, rpmSource.value) }
        addSource(gearSource) { update() }
        addSource(rpmSource) { update() }
    }

    /** Valori esattamente come arrivano dall'auto, senza interpretazione (debug, in basso a destra) */
    val raw: LiveData<String> = MediatorLiveData<String>().apply {
        val update = { value = describeRaw(gearSource.value, rpmSource.value) }
        addSource(gearSource) { update() }
        addSource(rpmSource) { update() }
    }

    private fun describeRaw(gear: CdsState?, rpm: CdsState?): String {
        val g = (gear as? CdsState.Value)?.values?.get("gear")?.toString() ?: "-"
        val r = (rpm as? CdsState.Value)?.values?.get("RPMSpeed")?.toString() ?: "-"
        return "gear=$g  rpm=$r"
    }

    private fun rawGear(state: CdsState.Value): Int? = (state.values["gear"] as? Number)?.toInt()

    private fun rawRpm(state: CdsState.Value): Int? = (state.values["RPMSpeed"] as? Number)?.toInt()

    private fun numberText(value: Any?): String =
        (value as? Number)?.toDouble()?.roundToInt()?.toString() ?: NO_VALUE

    private fun temperatureText(state: CdsState?, key: String): String {
        val values = (state as? CdsState.Value)?.values
        return numberText(values?.get(key)) + "°C"
    }

    private fun describe(gear: CdsState?, rpm: CdsState?): String {
        val states = listOfNotNull(gear, rpm)
        return when {
            states.isEmpty() ->
                "In attesa di AAIdrive…"
            states.any { it is CdsState.PermissionDenied } ->
                "Permesso CDS negato: reinstalla Rally Gear dopo aver installato AAIdrive"
            states.all { it is CdsState.Unavailable } ->
                "AAIdrive non raggiungibile (installato? versione recente? auto collegata?)"
            states.any { it is CdsState.Value } ->
                "CDS ok: dati ricevuti da AAIdrive"
            else ->
                "In attesa dei dati dall'auto…"
        }
    }

    companion object {
        private const val NO_GEAR = "-"
        private const val NO_VALUE = "--"

        /**
         * Ogni quanti millisecondi rileggere le temperature (lente a cambiare).
         * AAIdrive tiene attiva una proprieta' per circa 10 secondi dopo l'ultima query: con 10 s
         * la sottoscrizione resta praticamente sempre attiva; se vuoi che AAIdrive la rilasci tra
         * una lettura e l'altra, alza questo valore (ad esempio 20_000L).
         */
        const val TEMP_POLL_INTERVAL_MS = 10_000L

        /**
         * Conversione del valore grezzo di driving.gear, calibrata sulle prove in auto:
         *   1 = N, 2 = R, 3 = P, da 4 in su = valore - 4 (4 -> "0", 5 -> "1", 6 -> "2", ...)
         * Il valore grezzo ricevuto e' mostrato in basso a destra dello schermo.
         */
        fun gearLabel(raw: Int?): String = when {
            raw == null -> NO_GEAR
            raw == 1 -> "N"
            raw == 2 -> "R"
            raw == 3 -> "P"
            raw >= 4 -> (raw - 4).toString()
            else -> NO_GEAR
        }
    }
}
