package com.vipo.recorder.svc

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

/**
 * Sends "activity ping" to the ScreenRecordService whenever we detect user/system UI activity.
 * User must enable this service manually in Settings > Accessibility.
 */
class IdleAccessibilityService : AccessibilityService() {

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val e = event ?: return
    val pkg = e.packageName?.toString()

    // We send a lightweight ping to the recording service
    val i = Intent(this, ScreenRecordService::class.java).apply {
      action = ScreenRecordService.ACTION_ACTIVITY_PING
      putExtra(ScreenRecordService.EXTRA_LAST_PACKAGE, pkg)
    }
    startService(i)
  }

  override fun onInterrupt() {
    // No-op
  }
}
