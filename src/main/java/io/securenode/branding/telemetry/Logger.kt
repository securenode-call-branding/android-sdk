package io.securenode.branding.telemetry

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object Logger {
    private const val TAG = "SecureNode"
    private val httpLogging = AtomicBoolean(false)

    // Ring buffer (in-memory) for field debugging (no disk).
    private const val MAX_LINES = 250
    private val ring = ArrayDeque<String>(MAX_LINES)
    private val lock = Any()
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun configure(enableHttpLogging: Boolean) {
        httpLogging.set(enableHttpLogging)
    }

    fun d(msg: String) = log("D", msg, null)
    fun i(msg: String) = log("I", msg, null)
    fun w(msg: String, t: Throwable? = null) = log("W", msg, t)
    fun e(msg: String, t: Throwable? = null) = log("E", msg, t)

    fun http(msg: String) {
        if (httpLogging.get()) log("H", msg, null)
    }

    private fun log(level: String, msg: String, t: Throwable?) {
        val line = "${sdf.format(Date())} $level $msg"
        synchronized(lock) {
            if (ring.size >= MAX_LINES) ring.removeFirst()
            ring.addLast(line)
        }
        when (level) {
            "D" -> Log.d(TAG, msg, t)
            "I" -> Log.i(TAG, msg, t)
            "W" -> Log.w(TAG, msg, t)
            "E" -> Log.e(TAG, msg, t)
            "H" -> Log.d("$TAG-HTTP", msg, t)
            else -> Log.d(TAG, msg, t)
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { ring.toList() }
    fun snapshotText(): String = snapshot().joinToString("\n")
}
