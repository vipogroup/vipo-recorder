package com.vipo.recorder.svc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vipo.recorder.util.Prefs

class RecordableReceiver : BroadcastReceiver() {

  companion object {
    const val ACTION = "com.vipo.recorder.SET_RECORDABLE_PACKAGES"
    const val EXTRA_PACKAGES = "packages"
  }

  override fun onReceive(ctx: Context, intent: Intent?) {
    if (intent?.action != ACTION) return
    val list = intent.getStringArrayListExtra(EXTRA_PACKAGES) ?: return
    Prefs.setRecordablePackages(ctx, list.toSet())
  }
}
