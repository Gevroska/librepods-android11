package me.kavishdevar.librepods.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission

/** Keeps Android 12 callback classes out of the Android 11 registration path. */
class CallStateMonitor(private val context: Context, private val onState: (Int) -> Unit) {
    private var unregister: (() -> Unit)? = null

    fun start() {
        if (unregister != null || context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(TelephonyManager::class.java) ?: return
        try {
            unregister = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Api31.register(context, manager, onState)
            } else {
                registerLegacy(manager)
            }
        } catch (error: SecurityException) {
            // Permission can be revoked between the check and registration.
            Log.w("CallStateMonitor", "Call-state permission is unavailable", error)
        }
    }

    fun stop() {
        val cleanup = unregister
        unregister = null
        try {
            cleanup?.invoke()
        } catch (error: SecurityException) {
            Log.w("CallStateMonitor", "Call-state permission was revoked before cleanup", error)
        }
    }

    @Suppress("DEPRECATION")
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun registerLegacy(manager: TelephonyManager): () -> Unit {
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state)
        }
        manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        return { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object Api31 {
        @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
        fun register(context: Context, manager: TelephonyManager, onState: (Int) -> Unit): () -> Unit {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onState(state)
            }
            manager.registerTelephonyCallback(context.mainExecutor, callback)
            return { manager.unregisterTelephonyCallback(callback) }
        }
    }
}
