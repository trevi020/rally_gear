package io.bimmergestalt.idriveconnectaddons.rallygear

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Popup nero per cambiare al volo le 5 soglie RPM dei LED.
 *
 * Ogni soglia ha i pulsanti − e + (passo 100 giri/min, tenendoli premuti si ripete). Le modifiche
 * sono subito attive e salvate: i LED dietro il popup si aggiornano subito. Le soglie restano
 * sempre crescenti (distanza minima ShiftLightSettings.STEP).
 */
class ThresholdsDialog(
    private val activity: Activity,
    initial: IntArray,
    private val onChanged: (IntArray) -> Unit
) {
    private val values = initial.copyOf()
    private val valueViews = ArrayList<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val density = activity.resources.displayMetrics.density
    private var dialog: Dialog? = null

    private val labels = arrayOf(
        activity.getString(R.string.label_green_1),
        activity.getString(R.string.label_green_2),
        activity.getString(R.string.label_yellow_1),
        activity.getString(R.string.label_yellow_2),
        activity.getString(R.string.label_red)
    )
    private val labelColors = intArrayOf(Color.GREEN, Color.GREEN, Color.YELLOW, Color.YELLOW, Color.RED)

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show() {
        if (isShowing) return
        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(buildContent())

        val window = d.window
        if (window != null) {
            window.setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.BLACK)
                setStroke(dp(1), 0xFF555555.toInt())
                cornerRadius = dp(12).toFloat()
            })
            val width = minOf(dp(380), (activity.resources.displayMetrics.widthPixels * 0.92f).toInt())
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Evita che la comparsa del popup faccia riapparire le barre di sistema (modalità immersiva)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
        }
        d.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
        d.show()
        if (window != null) {
            window.decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        dialog = d
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun buildContent(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        column.addView(label(activity.getString(R.string.dialog_title), 20f, Color.WHITE, true))
        column.addView(
            label(
                activity.getString(R.string.dialog_hint),
                12f, 0xFFAAAAAA.toInt(), false
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(8) }
        )

        valueViews.clear()
        for (i in values.indices) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                label(labels[i], 16f, labelColors[i], true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(stepButton("−", i, -1))
            val valueView = label(values[i].toString(), 22f, Color.WHITE, true).apply {
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                minWidth = dp(76)
            }
            valueViews.add(valueView)
            row.addView(valueView)
            row.addView(stepButton("+", i, 1))
            column.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            )
        }

        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val reset = makeButton(activity.getString(R.string.button_reset), 14f).apply { setOnClickListener { resetDefaults() } }
        val close = makeButton(activity.getString(R.string.button_close), 14f).apply { setOnClickListener { dismiss() } }
        actions.addView(reset, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        actions.addView(close, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        column.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )

        return ScrollView(activity).apply { addView(column) }
    }

    /** Pulsante − / + con ripetizione tenendo premuto */
    private fun stepButton(symbol: String, index: Int, direction: Int): TextView {
        val button = makeButton(symbol, 26f)
        button.layoutParams = LinearLayout.LayoutParams(dp(56), dp(48))
        val repeat = object : Runnable {
            override fun run() {
                change(index, direction)
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    change(index, direction)
                    handler.postDelayed(repeat, FIRST_REPEAT_MS)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
        return button
    }

    private fun change(index: Int, direction: Int) {
        val low = if (index == 0) ShiftLightSettings.MIN_RPM else values[index - 1] + ShiftLightSettings.STEP
        val high = if (index == values.size - 1) ShiftLightSettings.MAX_RPM else values[index + 1] - ShiftLightSettings.STEP
        if (low > high) return
        val next = (values[index] + direction * ShiftLightSettings.STEP).coerceIn(low, high)
        if (next != values[index]) {
            values[index] = next
            valueViews[index].text = next.toString()
            publish()
        }
    }

    private fun resetDefaults() {
        for (i in values.indices) {
            values[i] = ShiftLightSettings.DEFAULTS[i]
            valueViews[i].text = values[i].toString()
        }
        publish()
    }

    private fun publish() {
        ShiftLightSettings.save(activity, values)
        onChanged(values.copyOf())
    }

    private fun label(content: String, sizeSp: Float, color: Int, bold: Boolean): TextView {
        return TextView(activity).apply {
            text = content
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun makeButton(content: String, sizeSp: Float): TextView {
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), boxShape(0xFF333333.toInt()))
            addState(intArrayOf(), boxShape(Color.BLACK))
        }
        return TextView(activity).apply {
            text = content
            textSize = sizeSp
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            background = states
            isClickable = true
        }
    }

    private fun boxShape(fill: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), 0xFF777777.toInt())
            cornerRadius = dp(8).toFloat()
        }
    }

    companion object {
        private const val FIRST_REPEAT_MS = 350L
        private const val REPEAT_MS = 70L
    }
}
