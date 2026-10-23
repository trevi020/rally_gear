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
 *
 * La prima query puo' bloccarsi fino a circa 1,5 secondi in attesa del dato dall'auto,
 * quindi tutte le letture avvengono su un thread dedicato, mai sul thread principale.
 * Il thread e l'observer esistono solo mentre la LiveData ha osservatori attivi.
 */
class CdsLiveData(
    private val contentResolver: ContentResolver,
    propertyName: String
) : LiveData<CdsState>() {

    private val uri: Uri = Uri.parse("content://$AUTHORITY/cds/$propertyName")
    private var worker: HandlerThread? = null
    private var observer: ContentObserver? = null

    override fun onActive() {
        val thread = HandlerThread("RallyGearCds").also { it.start() }
        val handler = Handler(thread.looper)
        val contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }
        worker = thread
        observer = contentObserver
        try {
            contentResolver.registerContentObserver(uri, true, contentObserver)
        } catch (e: Exception) {
            // provider non accessibile: la lettura iniziale qui sotto riportera' lo stato corretto
        }
        // Lettura iniziale (sul thread dedicato)
        handler.post { refresh() }
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
            result[key] = json.get(key)
        }
        return result
    }

    companion object {
        const val AUTHORITY = "io.bimmergestalt.cardata.provider"

        // Nomi delle proprieta' CDS (case-sensitive, vedi la documentazione CDS di BimmerGestalt)
        const val PROP_GEAR = "driving.gear"        // {"gear": N}
        const val PROP_RPM = "engine.RPMSpeed"      // {"RPMSpeed": N}
    }
}
