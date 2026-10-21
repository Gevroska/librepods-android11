package me.kavishdevar.librepods.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Keeps the final update in a burst, without repeatedly publishing identical content. */
internal class LatestUpdateDispatcher<T : Any>(
    private val intervalMillis: Long = 500,
    private val publish: (T) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: T? = null
    private var published: T? = null
    private var publishedAt: Long? = null
    private var closed = false
    private val flush = Runnable {
        val value = pending ?: return@Runnable
        pending = null
        if (value != published && !closed) {
            publish(value)
            published = value
            publishedAt = SystemClock.uptimeMillis()
        }
    }

    fun submit(value: T) = onMain {
        if (closed) return@onMain
        pending = value
        handler.removeCallbacks(flush)
        if (value == published) {
            pending = null
        } else {
            val delay = publishedAt?.let {
                (intervalMillis - (SystemClock.uptimeMillis() - it)).coerceAtLeast(0)
            } ?: 0
            handler.postDelayed(flush, delay)
        }
    }

    fun reset() = onMain {
        handler.removeCallbacks(flush)
        pending = null
        published = null
        publishedAt = null
    }

    fun close() = onMain {
        reset()
        closed = true
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
}
