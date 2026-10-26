package io.bimmergestalt.idriveconnectaddons.rallygear

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import java.util.ArrayDeque

/**
 * Misura la frequenza (Hz) con cui arrivano gli aggiornamenti di un dato.
 * Tiene gli istanti degli ultimi aggiornamenti entro una finestra mobile (2 secondi) e calcola
 * (aggiornamenti - 1) / intervallo tra il primo e l'ultimo. Senza aggiornamenti nella finestra
 * il risultato scende a 0. Va usato sempre dal thread principale.
 */
class RateMeter(private val windowMs: Long = 2000L) {
    private val times = ArrayDeque<Long>()

    fun record(now: Long = SystemClock.elapsedRealtime()) {
        times.addLast(now)
        trim(now)
    }

    fun hertz(now: Long = SystemClock.elapsedRealtime()): Double {
        trim(now)
        if (times.size < 2) return 0.0
        val span = times.peekLast() - times.peekFirst()
        return if (span <= 0L) 0.0 else (times.size - 1) * 1000.0 / span
    }

    private fun trim(now: Long) {
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
            times.pollFirst()
        }
    }
}

/**
 * LiveData che emette l'ora ogni intervalMs, solo finche' ha osservatori attivi.
 * Serve a far aggiornare periodicamente valori calcolati nel tempo (come gli Hz, che devono
 * scendere a 0 anche quando non arriva piu' nessun dato).
 */
class TickerLiveData(private val intervalMs: Long) : MutableLiveData<Long>() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            value = SystemClock.elapsedRealtime()
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onActive() {
        handler.post(tick)
    }

    override fun onInactive() {
        handler.removeCallbacks(tick)
    }
}
