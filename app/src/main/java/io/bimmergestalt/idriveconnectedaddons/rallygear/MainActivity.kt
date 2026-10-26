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

    /** Zona dello schermo toccata, che decide cosa fa una pressione prolungata */
    private enum class Zone { LEDS, SPEED, OTHER }

    private lateinit var model: MainModel
    private lateinit var tvGearDigit: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvRaw: TextView
    private lateinit var ledBar: View
    private lateinit var leds: List<View>

    // Solo nel layout orizzontale, altrimenti null
    private var tvSpeed: TextView? = null
    private var tvSpeedUnit: TextView? = null

    // Soglie RPM dei LED (modificabili dal popup) e ultimo valore di giri ricevuto
    private var thresholds = ShiftLightSettings.DEFAULTS.copyOf()
    private var lastRpm = 0
    private var thresholdsDialog: ThresholdsDialog? = null

    // Pressioni prolungate: ogni zona ha la sua azione e la sua durata
    private val handler = Handler(Looper.getMainLooper())
    private val rotateRunnable = Runnable { rotateToNext() }
    private val thresholdsRunnable = Runnable { openThresholdsDialog() }
    private val speedUnitRunnable = Runnable { toggleSpeedUnit() }
    private var scheduled: Runnable? = null
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
        ledBar = findViewById(R.id.ledBarContainer)

        // Verticale: 5 LED (led1..led5). Orizzontale: 10 LED (led1..led10).
        // (toList() serve perché IntArray non ha mapNotNull: esiste solo per List, Array<T>, ecc.)
        leds = LED_IDS.toList().mapNotNull { findViewById<View?>(it) }

        // Presenti solo nel layout orizzontale, altrimenti null
        val speedView = findViewById<TextView?>(R.id.tvSpeed)
        val speedUnitView = findViewById<TextView?>(R.id.tvSpeedUnit)
        val engTempView = findViewById<TextView?>(R.id.tvEngTemp)
        val oilTempView = findViewById<TextView?>(R.id.tvOilTemp)
        tvSpeed = speedView
        tvSpeedUnit = speedUnitView

        // Caricamento Font 7-Segmenti (fallback sul font di sistema se il TTF manca)
        val digitalFont = try {
            Typeface.createFromAsset(assets, "fonts/dseg7.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
        tvGearDigit.typeface = digitalFont
        speedView?.typeface = digitalFont

        // Valori iniziali, finché AAIdrive non invia dati
        thresholds = ShiftLightSettings.load(this)
        tvGearDigit.text = "-"
        updateShiftLights(0)

        // Dati CDS di AAIdrive (si attivano solo mentre la schermata è visibile)
        model = ViewModelProvider(this)[MainModel::class.java]
        model.gear.observe(this) { gear -> tvGearDigit.text = gear }
        model.rpm.observe(this) { rpm ->
            lastRpm = rpm
            updateShiftLights(rpm)
        }
        model.status.observe(this) { status -> tvStatus.text = status }
        model.raw.observe(this) { raw -> tvRaw.text = raw }

        // Velocità e temperature: osservate (e quindi richieste all'auto) solo in orizzontale
        if (speedView != null) {
            model.speed.observe(this) { speed -> speedView.text = speed }
        }
        if (speedUnitView != null) {
            model.speedUnit.observe(this) { unit -> speedUnitView.text = unit }
        }
        if (engTempView != null) {
            model.engTemp.observe(this) { text -> engTempView.text = text }
        }
        if (oilTempView != null) {
            model.oilTemp.observe(this) { text -> oilTempView.text = text }
        }

        setupLongPress()
    }

    override fun onPause() {
        cancelLongPress()
        thresholdsDialog?.dismiss()
        thresholdsDialog = null
        super.onPause()
    }

    override fun onDestroy() {
        cancelLongPress()
        super.onDestroy()
    }

    /**
     * Pressioni prolungate (senza muovere il dito), a seconda di dove si preme:
     *  - fascia dei LED, 2 secondi: popup per cambiare le soglie dei LED;
     *  - velocità, 2 secondi: passa da km/h a mph, senza finestre;
     *  - ovunque altrove, 3,4 secondi: ruota lo schermo (0° -> 90° -> 180° -> 270° -> 0°).
     */
    private fun setupLongPress() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    cancelLongPress()
                    when (zoneAt(event.rawX, event.rawY)) {
                        Zone.LEDS -> schedule(thresholdsRunnable, THRESHOLDS_LONG_PRESS_MS)
                        Zone.SPEED -> schedule(speedUnitRunnable, SPEED_UNIT_LONG_PRESS_MS)
                        Zone.OTHER -> schedule(rotateRunnable, ROTATE_LONG_PRESS_MS)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) {
                        cancelLongPress()
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelLongPress()
                }
            }
            true
        }
    }

    private fun schedule(action: Runnable, delayMs: Long) {
        scheduled = action
        handler.postDelayed(action, delayMs)
    }

    private fun cancelLongPress() {
        scheduled?.let { handler.removeCallbacks(it) }
        scheduled = null
    }

    /** Fascia dei LED (dal bordo superiore fino sotto i LED) e velocità sono escluse dalla rotazione */
    private fun zoneAt(rawX: Float, rawY: Float): Zone {
        val margin = dp(16)
        val location = IntArray(2)
        ledBar.getLocationOnScreen(location)
        if (rawY <= location[1] + ledBar.height + margin) return Zone.LEDS
        if (hits(tvSpeed, rawX, rawY, margin) || hits(tvSpeedUnit, rawX, rawY, margin)) return Zone.SPEED
        return Zone.OTHER
    }

    private fun hits(view: View?, rawX: Float, rawY: Float, margin: Int): Boolean {
        if (view == null || !view.isShown) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] - margin &&
            rawX <= location[0] + view.width + margin &&
            rawY >= location[1] - margin &&
            rawY <= location[1] + view.height + margin
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

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

    private fun openThresholdsDialog() {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        thresholdsDialog?.dismiss()
        val dialog = ThresholdsDialog(this, thresholds) { updated ->
            thresholds = updated
            updateShiftLights(lastRpm)
        }
        thresholdsDialog = dialog
        dialog.show()
    }

    private fun toggleSpeedUnit() {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        model.toggleSpeedUnit()
    }

    /**
     * Accende i LED in base ai giri. Le soglie sono sempre 5: con 5 LED (verticale) ogni LED ha la
     * sua soglia, con 10 LED (orizzontale) si accendono a coppie (1-2, 3-4, 5-6, 7-8, 9-10).
     */
    private fun updateShiftLights(rpm: Int) {
        val ledsPerStep = (leds.size / thresholds.size).coerceAtLeast(1)
        leds.forEachIndexed { index, led ->
            val step = (index / ledsPerStep).coerceAtMost(thresholds.size - 1)
            val on = rpm >= thresholds[step]
            led.setBackgroundResource(if (on) COLORS[step] else R.drawable.led_off)
        }
    }

    companion object {
        private const val PREFS = "rally_gear"
        private const val PREF_ROTATION = "rotation"

        /** Pressione per cambiare orientamento (fuori dalle zone speciali) */
        private const val ROTATE_LONG_PRESS_MS = 3400L

        /** Pressione sulla fascia dei LED per aprire il popup delle soglie */
        private const val THRESHOLDS_LONG_PRESS_MS = 2000L

        /** Pressione sulla velocità per passare da km/h a mph */
        private const val SPEED_UNIT_LONG_PRESS_MS = 2000L

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

        // Colori dei 5 gradini (le soglie sono in ShiftLightSettings): verde, verde, giallo, giallo, rosso
        private val COLORS = intArrayOf(
            R.drawable.led_green,
            R.drawable.led_green,
            R.drawable.led_yellow,
            R.drawable.led_yellow,
            R.drawable.led_red
        )
    }
}
