package io.bimmergestalt.idriveconnectaddons.rallygear

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Serve solo a rendere l'app visibile nella scheda "Addons" di AAIdrive.
 *
 * AAIdrive cerca i servizi con l'action "io.bimmergestalt.cardata.service" e mostra
 * le app che richiedono il permesso io.bimmergestalt.permission.CDS_normal.
 * I dati dell'auto non passano da qui: si leggono dal content provider di AAIdrive
 * (vedi CdsLiveData), quindi il servizio non fa nulla e non si collega a niente.
 */
class RallyGearService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
