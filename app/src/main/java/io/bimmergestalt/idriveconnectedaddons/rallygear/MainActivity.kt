package io.bimmergestalt.idriveconnectaddons.rallygear

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvGearDigit: TextView
    private lateinit var led1: View
    private lateinit var led2: View
    private lateinit var led3: View
    private lateinit var led4: View
    private lateinit var led5: View

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
        led1 = findViewById(R.id.led1)
        led2 = findViewById(R.id.led2)
        led3 = findViewById(R.id.led3)
        led4 = findViewById(R.id.led4)
        led5 = findViewById(R.id.led5)

        // Caricamento Font 7-Segmenti
        try {
            val digitalFont = Typeface.createFromAsset(assets, "fonts/dseg7.ttf")
            tvGearDigit.typeface = digitalFont
        } catch (e: Exception) {
            // Fallback sul font di sistema se il TTF manca
            tvGearDigit.typeface = Typeface.MONOSPACE
        }

        // Osservazione dati LiveData dal MainModel
        MainModel.instance.gearLiveData.observe(this) { gear ->
            tvGearDigit.text = gear
        }

        MainModel.instance.rpmLiveData.observe(this) { rpm ->
            updateShiftLights(rpm)
        }
    }

    private fun updateShiftLights(rpm: Int) {
        // Reset LED
        led1.setBackgroundResource(R.drawable.led_off)
        led2.setBackgroundResource(R.drawable.led_off)
        led3.setBackgroundResource(R.drawable.led_off)
        led4.setBackgroundResource(R.drawable.led_off)
        led5.setBackgroundResource(R.drawable.led_off)

        // Progressione RPM (Esempio: cambio consigliato a 6500 RPM)
        if (rpm >= 3500) led1.setBackgroundResource(R.drawable.led_green)
        if (rpm >= 4200) led2.setBackgroundResource(R.drawable.led_green)
        if (rpm >= 5000) led3.setBackgroundResource(R.drawable.led_yellow)
        if (rpm >= 5700) led4.setBackgroundResource(R.drawable.led_yellow)
        if (rpm >= 6500) led5.setBackgroundResource(R.drawable.led_red)
    }
}