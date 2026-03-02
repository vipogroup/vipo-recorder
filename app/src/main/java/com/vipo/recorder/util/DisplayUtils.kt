package com.vipo.recorder.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object DisplayUtils {
  data class Metrics(val w: Int, val h: Int, val densityDpi: Int)

  fun metrics(ctx: Context): Metrics {
    val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= 30) {
      val b = wm.currentWindowMetrics.bounds
      Metrics(b.width(), b.height(), ctx.resources.displayMetrics.densityDpi)
    } else {
      @Suppress("DEPRECATION")
      val display = wm.defaultDisplay
      val dm = DisplayMetrics()
      @Suppress("DEPRECATION")
      display.getRealMetrics(dm)
      Metrics(dm.widthPixels, dm.heightPixels, dm.densityDpi)
    }
  }
}
