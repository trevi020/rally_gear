package io.bimmergestalt.idriveconnectaddons.rallygear

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {

    private lateinit var model: MainModel
    private lateinit var tvGearDigit: TextView
    private lateinit var tvStatus: TextView
    private lateinit var leds: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Modalità FullScreen Immersiva
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_main)

        tvGearDigit = findViewById(R.id.tvGearDigit)
        tvStatus = findViewById(R.id.tvStatus)
        leds = listOf<View>(
            findViewById(R.id.led1),
            findViewById(R.id.led2),
            findViewById(R.id.led3),
            findViewById(R.id.led4),
            findViewById(R.id.led5)
        )

        // Caricamento Font 7-Segmenti
        try {
            val digitalFont = Typeface.createFromAsset(assets, "fonts/dseg7.ttf")
            tvGearDigit.typeface = digitalFont
        } catch (e: Exception) {
            // Fallback sul font di sistema se il TTF manca
            tvGearDigit.typeface = Typeface.MONOSPACE
        }

        // Valori iniziali, finché AAIdrive non invia dati
        tvGearDigit.text = "-"
        updateShiftLights(0)

        // Dati CDS di AAIdrive (si attivano solo mentre la schermata è visibile)
        model = ViewModelProvider(this)[MainModel::class.java]
        model.gear.observe(this) { gear -> tvGearDigit.text = gear }
        model.rpm.observe(this) { rpm -> updateShiftLights(rpm) }
        model.status.observe(this) { status -> tvStatus.text = status }
    }

    private fun updateShiftLights(rpm: Int) {
        leds.forEachIndexed { index, led ->
            val drawable = if (rpm >= RPM_THRESHOLDS[index]) LED_ON[index] else R.drawable.led_off
            led.setBackgroundResource(drawable)
        }
    }

    companion object {
        // Soglie RPM dei 5 LED (modificale qui): verde, verde, giallo, giallo, rosso
        private val RPM_THRESHOLDS = intArrayOf(3500, 4200, 5000, 5700, 6500)
        private val LED_ON = intArrayOf(
            R.drawable.led_green,
            R.drawable.led_green,
            R.drawable.led_yellow,
            R.drawable.led_yellow,
            R.drawable.led_red
        )
    }
}
