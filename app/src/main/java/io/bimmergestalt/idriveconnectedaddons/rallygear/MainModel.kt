package io.bimmergestalt.idriveconnectaddons.rallygear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

/**
 * Espone marcia, giri motore e una riga di stato leggendo i dati CDS di AAIdrive.
 * Le sorgenti si attivano solo mentre la schermata e' visibile (observer attivi).
 */
class MainModel(app: Application) : AndroidViewModel(app) {

    private val gearSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_GEAR)
    private val rpmSource = CdsLiveData(app.contentResolver, CdsLiveData.PROP_RPM)

    /** Testo da mostrare nel display a 7 segmenti: "N", "R", "P", "1", "2", ... oppure "-" */
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

        /**
         * Conversione del valore grezzo di driving.gear, secondo la documentazione CDS:
         *   1 = N, 2 = R, 3 = P, 4 e oltre = D1, D2, D3...
         * Se sulla tua auto le marce risultano sfasate, correggi solo questa funzione
         * (il valore grezzo ricevuto e' mostrato in basso a destra).
         */
        fun gearLabel(raw: Int?): String = when {
            raw == null -> NO_GEAR
            raw == 1 -> "N"
            raw == 2 -> "R"
            raw == 3 -> "P"
            raw >= 4 -> (raw - 3).toString()
            else -> NO_GEAR
        }
    }
}
