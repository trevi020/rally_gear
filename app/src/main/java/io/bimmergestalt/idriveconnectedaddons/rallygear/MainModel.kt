package io.bimmergestalt.idriveconnectaddons.rallygear

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale
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

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val gearSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_GEAR)
    private val rpmSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_RPM)
    private val speedSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_SPEED)
    private val tempSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_TEMP, TEMP_POLL_INTERVAL_MS)

    /** Misura quante volte al secondo arriva il dato dei giri (mostrato nei valori grezzi) */
    private val rpmMeter = RateMeter()

    /** Ultimo valore dei giri contato dal misuratore (per ignorare le letture uguali) */
    private var lastRpmValue: Int? = null

    /** Fa ricalcolare gli Hz periodicamente, cosi' scendono a 0 se i dati si fermano */
    private val ticker = TickerLiveData(RAW_REFRESH_MS)

    /** true = velocita' in mph, false = km/h (scelta salvata) */
    private val useMph = MutableLiveData<Boolean>(prefs.getBoolean(PREF_MPH, false))

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

    /**
     * Velocita' come testo, in km/h o in mph secondo la scelta dell'utente, oppure "--".
     * L'auto fornisce sempre km/h (documentazione CDS): per le mph si moltiplica per KMH_TO_MPH.
     */
    val speed: LiveData<String> = MediatorLiveData<String>().apply {
        val update = {
            val kmh = ((speedSource.value as? CdsState.Value)?.values?.get("speedActual") as? Number)?.toDouble()
            value = if (kmh == null) {
                NO_VALUE
            } else {
                val shown = if (useMph.value == true) kmh * KMH_TO_MPH else kmh
                shown.roundToInt().toString()
            }
        }
        addSource(speedSource) { update() }
        addSource(useMph) { update() }
    }

    /** Etichetta dell'unita' di misura della velocita': "km/h" o "mph" */
    val speedUnit: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(useMph) { mph -> value = if (mph == true) "mph" else "km/h" }
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

    /**
     * Valori esattamente come arrivano dall'auto, senza interpretazione, piu' la frequenza di
     * aggiornamento dei giri in Hz, cioe' quante volte al secondo il valore cambia (debug, in basso a destra)
     */
    val raw: LiveData<String> = MediatorLiveData<String>().apply {
        val update = { value = describeRaw(gearSource.value, rpmSource.value) }
        addSource(gearSource) { update() }
        addSource(rpmSource) { state ->
            // Si contano solo i giri CAMBIATI rispetto alla lettura precedente: le notifiche di
            // AAIdrive o le riletture dello stesso valore non sono aggiornamenti dell'auto.
            val current = (state as? CdsState.Value)?.let { rawRpm(it) }
            if (current != null && current != lastRpmValue) {
                lastRpmValue = current
                rpmMeter.record()
            }
            update()
        }
        addSource(ticker) { update() }
    }

    /** Passa da km/h a mph e viceversa; la scelta viene salvata */
    fun toggleSpeedUnit() {
        val mph = useMph.value != true
        useMph.value = mph
        prefs.edit().putBoolean(PREF_MPH, mph).apply()
    }

    private fun describeRaw(gear: CdsState?, rpm: CdsState?): String {
        val g = (gear as? CdsState.Value)?.values?.get("gear")?.toString() ?: "-"
        val r = (rpm as? CdsState.Value)?.values?.get("RPMSpeed")?.toString() ?: "-"
        val hz = String.format(Locale.US, "%.1f", rpmMeter.hertz())
        return "gear=$g  rpm=$r  ${hz}Hz"
    }

    private fun rawGear(state: CdsState.Value): Int? = (state.values["gear"] as? Number)?.toInt()

    private fun rawRpm(state: CdsState.Value): Int? = (state.values["RPMSpeed"] as? Number)?.toInt()

    private fun numberText(value: Any?): String =
        (value as? Number)?.toDouble()?.roundToInt()?.toString() ?: NO_VALUE

    private fun temperatureText(state: CdsState?, key: String): String {
        val values = (state as? CdsState.Value)?.values
        return numberText(values?.get(key)) + "°C"
    }

    private fun string(id: Int): String = getApplication<Application>().getString(id)

    private fun describe(gear: CdsState?, rpm: CdsState?): String {
        val states = listOfNotNull(gear, rpm)
        return when {
            states.isEmpty() ->
                string(R.string.status_waiting_aaidrive)
            states.any { it is CdsState.PermissionDenied } ->
                string(R.string.status_permission_denied)
            states.all { it is CdsState.Unavailable } ->
                string(R.string.status_unreachable)
            states.any { it is CdsState.Value } ->
                string(R.string.status_ok)
            else ->
                string(R.string.status_waiting_car)
        }
    }

    companion object {
        private const val PREFS = "rally_gear"
        private const val PREF_MPH = "speed_mph"

        private const val NO_GEAR = "-"
        private const val NO_VALUE = "--"

        /** Fattore di conversione da km/h a mph */
        private const val KMH_TO_MPH = 0.621371

        /** Ogni quanti millisecondi ricalcolare gli Hz mostrati nei valori grezzi */
        private const val RAW_REFRESH_MS = 500L

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
