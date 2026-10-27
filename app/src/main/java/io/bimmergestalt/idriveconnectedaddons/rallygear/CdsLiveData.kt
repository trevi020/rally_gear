package io.bimmergestalt.idriveconnectaddons.rallygear

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.lifecycle.LiveData
import org.json.JSONObject

/**
 * Stato di lettura di una proprieta' CDS esposta da AAIdrive.
 */
sealed class CdsState {
    /** AAIdrive non installato, troppo vecchio (senza content provider) o non visibile. */
    object Unavailable : CdsState()

    /** Il permesso io.bimmergestalt.permission.CDS_normal non risulta concesso. */
    object PermissionDenied : CdsState()

    /** Il provider risponde, ma l'auto non ha (ancora) inviato questo valore. */
    object NoData : CdsState()

    /** Valore ricevuto, ad esempio {"gear": 5} oppure {"RPMSpeed": 3200}. */
    data class Value(val values: Map<String, Any>) : CdsState()
}

/**
 * LiveData che legge una proprieta' CDS dal Content Provider di AAIdrive.
 *
 * Protocollo (stesso della libreria IDriveConnectKitAndroid di BimmerGestalt):
 *  - URI: content://io.bimmergestalt.cardata.provider/cds/<nomeProprieta>
 *  - il cursore ha una riga con colonne [_id, name, value]; "value" e' una stringa JSON
 *  - AAIdrive chiama notifyChange() sull'URI a ogni aggiornamento (al massimo ogni ~100 ms)
 *  - ogni query tiene "sottoscritta" la proprieta' presso l'auto; AAIdrive la rilascia dopo
 *    circa 10 secondi senza query
 *
 * Due modalita':
 *  - pollIntervalMs = 0 (predefinita): TEMPO REALE. Un ContentObserver rilegge il valore a ogni
 *    notifica di AAIdrive, quindi si aggiorna alla massima frequenza consentita.
 *  - pollIntervalMs > 0: POLLING. Nessun observer: il valore viene riletto solo ogni pollIntervalMs
 *    millisecondi. Adatto ai dati che cambiano piano (temperature): niente query e niente
 *    aggiornamenti dell'interfaccia a ogni notifica.
 *
 * La prima query puo' bloccarsi fino a circa 1,5 secondi in attesa del dato dall'auto,
 * quindi tutte le letture avvengono su un thread dedicato, mai sul thread principale.
 * Il thread (e l'observer) esistono solo mentre la LiveData ha osservatori attivi.
 */
class CdsLiveData(
    private val contentResolver: ContentResolver,
    propertyName: String,
    private val pollIntervalMs: Long = 0L
) : LiveData<CdsState>() {

    private val uri: Uri = Uri.parse("content://$AUTHORITY/cds/$propertyName")
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var observer: ContentObserver? = null

    override fun onActive() {
        val thread = HandlerThread("RallyGearCds").also { it.start() }
        val handler = Handler(thread.looper)
        worker = thread
        workerHandler = handler

        if (pollIntervalMs > 0) {
            // POLLING: lettura immediata, poi una ogni pollIntervalMs (contati dalla fine della lettura)
            handler.post(object : Runnable {
                override fun run() {
                    refresh()
                    handler.postDelayed(this, pollIntervalMs)
                }
            })
        } else {
            // TEMPO REALE: rilettura a ogni notifica di AAIdrive
            val contentObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    refresh()
                }
            }
            observer = contentObserver
            try {
                contentResolver.registerContentObserver(uri, true, contentObserver)
            } catch (e: Exception) {
                // provider non accessibile: la lettura iniziale qui sotto riportera' lo stato corretto
            }
            // Lettura iniziale (sul thread dedicato)
            handler.post { refresh() }
        }
    }

    override fun onInactive() {
        observer?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                // niente da fare
            }
        }
        observer = null
        workerHandler?.removeCallbacksAndMessages(null)
        workerHandler = null
        worker?.quitSafely()
        worker = null
    }

    private fun refresh() {
        postValue(readValue())
    }

    private fun readValue(): CdsState = try {
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor == null) CdsState.Unavailable else cursor.use { toState(it) }
    } catch (e: SecurityException) {
        CdsState.PermissionDenied
    } catch (e: Exception) {
        CdsState.Unavailable
    }

    private fun toState(cursor: Cursor): CdsState {
        if (!cursor.moveToFirst()) return CdsState.NoData
        val column = cursor.getColumnIndex("value").let { if (it >= 0) it else 2 }
        val json = cursor.getString(column)
        return if (json.isNullOrEmpty()) CdsState.NoData else CdsState.Value(parse(JSONObject(json)))
    }

    private fun parse(json: JSONObject): Map<String, Any> {
        val result = HashMap<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val child = json.optJSONObject(key)
            if (child != null) {
                // JSON annidato, ad esempio {"temperature":{"engine":19,"oil":18}}: appiattito in {"engine":19,"oil":18}
                result.putAll(parse(child))
            } else {
                result[key] = json.get(key)
            }
        }
        return result
    }

    companion object {
        const val AUTHORITY = "io.bimmergestalt.cardata.provider"

        // Nomi delle proprieta' CDS (case-sensitive, vedi la documentazione CDS di BimmerGestalt)
        const val PROP_SPEED = "driving.speedActual"    // {"speedActual": N}, in km/h
        const val PROP_TEMP = "engine.temperature"      // {"temperature": {"engine": N, "oil": N}}, in °C
        const val PROP_GEAR = "driving.gear"            // {"gear": N}
        const val PROP_RPM = "engine.RPMSpeed"          // {"RPMSpeed": N}
    }
}
