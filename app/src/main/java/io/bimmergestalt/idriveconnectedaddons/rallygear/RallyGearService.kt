package io.bimmergestalt.idriveconnectaddons.rallygear

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class RallyGearService : Service() {

    private val cdsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                // Lettura parametri inviati dal Car Data Service di AAIdrive
                val rawGear = intent.getIntExtra("driving.gear", 0)
                val rpm = intent.getIntExtra("engine.rpm", 0)
                
                MainModel.instance.updateCarData(rawGear, rpm)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("io.bimmergestalt.idriveconnectaddons.CDS_UPDATE")
        registerReceiver(cdsReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(cdsReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}