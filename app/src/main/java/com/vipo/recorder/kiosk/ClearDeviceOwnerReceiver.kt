package com.vipo.recorder.kiosk

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ClearDeviceOwnerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            KioskPolicy.clearAllSuspension(context)
            dpm.clearDeviceOwnerApp(context.packageName)
            Log.i("ClearDO", "Device Owner cleared for ${context.packageName}")
        }
    }
}
