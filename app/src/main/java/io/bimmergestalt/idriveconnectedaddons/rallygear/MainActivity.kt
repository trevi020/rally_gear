package io.bimmergestalt.idriveconnectaddons.rallygear

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var model: MainModel
    private lateinit var tvGearDigit: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvRaw: TextView
    private lateinit var leds: List<View>

    // Rotazione manuale dello schermo (pressione prolungata)
    private val handler = Handler(Looper.getMainLooper())
    private val rotateRunnable = Runnable { rotateToNext() }
    private var rotationIndex = 0
    private var downX = 0f
    private var downY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Orientamento scelto a mano e salvato (verticale al primo avvio): niente rotazione col sensore.
        // Se serve cambiarlo il sistema ricrea l'activity e questo onCreate riparte con quello giusto.
        rotationIndex = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(PREF_ROTATION, 0)
            .coerceIn(0, ORIENTATIONS.size - 1)
        if (requestedOrientation != ORIENTATIONS[rotationIndex]) {
            requestedOrientation = ORIENTATIONS[rotationIndex]
        }

        // Modalità FullScreen Immersiva
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Verticale (0° e 180°): res/layout/activity_main.xml.
        // Orizzontale (90° e 270°): res/layout-land/activity_main.xml.
        // La rotazione ricrea l'activity; il MainModel (e quindi gli ultimi dati) sopravvive.
        setContentView(R.layout.activity_main)

        tvGearDigit = findViewById(R.id.tvGearDigit)
        tvStatus = findViewById(R.id.tvStatus)
        tvRaw = findViewById(R.id.tvRaw)

        // Verticale: 5 LED (led1..led5). Orizzontale: 10 LED (led1..led10).
        // (toList() serve perché IntArray non ha mapNotNull: esiste solo per List, Array<T>, ecc.)
        leds = LED_IDS.toList().mapNotNull { findViewById<View?>(it) }

        // Presenti solo nel layout orizzontale, altrimenti null
        val tvSpeed = findViewById<TextView?>(R.id.tvSpeed)
        val tvEngTemp = findViewById<TextView?>(R.id.tvEngTemp)
        val tvOilTemp = findViewById<TextView?>(R.id.tvOilTemp)

        // Caricamento Font 7-Segmenti (fallback sul font di sistema se il TTF manca)
        val digitalFont = try {
            Typeface.createFromAsset(assets, "fonts/dseg7.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
        tvGearDigit.typeface = digitalFont
        tvSpeed?.typeface = digitalFont

        // Valori iniziali, finché AAIdrive non invia dati
        tvGearDigit.text = "-"
        updateShiftLights(0)

        // Dati CDS di AAIdrive (si attivano solo mentre la schermata è visibile)
        model = ViewModelProvider(this)[MainModel::class.java]
        model.gear.observe(this) { gear -> tvGearDigit.text = gear }
        model.rpm.observe(this) { rpm -> updateShiftLights(rpm) }
        model.status.observe(this) { status -> tvStatus.text = status }
        model.raw.observe(this) { raw -> tvRaw.text = raw }

        // Velocità e temperature: osservate (e quindi richieste all'auto) solo in orizzontale
        if (tvSpeed != null) {
            model.speed.observe(this) { speed -> tvSpeed.text = speed }
        }
        if (tvEngTemp != null) {
            model.engTemp.observe(this) { text -> tvEngTemp.text = text }
        }
        if (tvOilTemp != null) {
            model.oilTemp.observe(this) { text -> tvOilTemp.text = text }
        }

        setupRotationLongPress()
    }

    override fun onPause() {
        handler.removeCallbacks(rotateRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(rotateRunnable)
        super.onDestroy()
    }

    /**
     * Tenendo premuto lo schermo per 4 secondi (senza muovere il dito) l'orientamento passa
     * al successivo: 0° -> 90° -> 180° -> 270° -> 0° ...
     */
    private fun setupRotationLongPress() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    handler.removeCallbacks(rotateRunnable)
                    handler.postDelayed(rotateRunnable, ROTATE_LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) {
                        handler.removeCallbacks(rotateRunnable)
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    handler.removeCallbacks(rotateRunnable)
                }
            }
            true
        }
    }

    private fun rotateToNext() {
        rotationIndex = (rotationIndex + 1) % ORIENTATIONS.size
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_ROTATION, rotationIndex)
            .apply()
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        Toast.makeText(this, "Rotazione schermo: ${rotationIndex * 90}°", Toast.LENGTH_SHORT).show()
        requestedOrientation = ORIENTATIONS[rotationIndex]
    }

    /**
     * Accende i LED in base ai giri. Le soglie sono sempre 5: con 5 LED (verticale) ogni LED ha la
     * sua soglia, con 10 LED (orizzontale) si accendono a coppie (1-2, 3-4, 5-6, 7-8, 9-10).
     */
    private fun updateShiftLights(rpm: Int) {
        val ledsPerStep = (leds.size / THRESHOLDS.size).coerceAtLeast(1)
        leds.forEachIndexed { index, led ->
            val step = (index / ledsPerStep).coerceAtMost(THRESHOLDS.size - 1)
            val on = rpm >= THRESHOLDS[step]
            led.setBackgroundResource(if (on) COLORS[step] else R.drawable.led_off)
        }
    }

    companion object {
        private const val PREFS = "rally_gear"
        private const val PREF_ROTATION = "rotation"

        /** Durata della pressione per cambiare orientamento */
        private const val ROTATE_LONG_PRESS_MS = 4000L

        /** 0°, 90°, 180°, 270° */
        private val ORIENTATIONS = intArrayOf(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        )

        private val LED_IDS = intArrayOf(
            R.id.led1, R.id.led2, R.id.led3, R.id.led4, R.id.led5,
            R.id.led6, R.id.led7, R.id.led8, R.id.led9, R.id.led10
        )

        // Soglie RPM (modificale qui), uguali in verticale e in orizzontale: verde, verde, giallo, giallo, rosso
        private val THRESHOLDS = intArrayOf(3500, 4200, 5000, 5700, 6500)
        private val COLORS = intArrayOf(
            R.drawable.led_green,
            R.drawable.led_green,
            R.drawable.led_yellow,
            R.drawable.led_yellow,
            R.drawable.led_red
        )
    }
}
