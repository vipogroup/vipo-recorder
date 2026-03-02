package com.vipo.recorder.svc

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs

/**
 * Simple floating overlay:
 * - Always-on-top small panel
 * - STOP requires holding for 5 seconds
 *
 * Note: Requires user to grant "Display over other apps" permission.
 */
class OverlayController(private val ctx: Context) {

  private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private var root: View? = null
  private val main = Handler(Looper.getMainLooper())

  fun canDrawOverlays(): Boolean {
    return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)
  }

  fun show(onStopConfirmed: () -> Unit) {
    if (!canDrawOverlays()) return
    if (root != null) return

    val container = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(18, 14, 18, 14)
      alpha = 0.95f
      // simple dark panel
      setBackgroundColor(0xCC000000.toInt())
    }

    val title = TextView(ctx).apply {
      text = "VIPORecorder"
      setTextColor(0xFFFFFFFF.toInt())
      textSize = 14f
    }
    val hint = TextView(ctx).apply {
      text = "Hold STOP 5s to end"
      setTextColor(0xFFFFFFFF.toInt())
      textSize = 12f
      alpha = 0.9f
    }

    val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
      max = 100
      progress = 0
    }

    val stopBtn = Button(ctx).apply { text = "STOP" }

    container.addView(title)
    container.addView(hint)
    container.addView(pb, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = 8 })
    container.addView(stopBtn, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = 8 })

    // Drag to move
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      else
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.END
      x = 30
      y = 160
    }

    var downX = 0f
    var downY = 0f
    var startX = 0
    var startY = 0
    var dragging = false

    container.setOnTouchListener { _, ev ->
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = ev.rawX
          downY = ev.rawY
          startX = params.x
          startY = params.y
          dragging = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (downX - ev.rawX).toInt()
          val dy = (ev.rawY - downY).toInt()
          if (!dragging && (abs(dx) > 6 || abs(dy) > 6)) dragging = true
          params.x = startX + dx
          params.y = startY + dy
          wm.updateViewLayout(container, params)
          true
        }
        else -> false
      }
    }

    // Hold-to-stop 5 seconds
    stopBtn.setOnTouchListener(object : View.OnTouchListener {
      private val holdMs = 5000L
      private var running = false
      private var startTs = 0L

      private val tick = object : Runnable {
        override fun run() {
          if (!running) return
          val elapsed = System.currentTimeMillis() - startTs
          val pct = ((elapsed.toDouble() / holdMs.toDouble()) * 100).toInt().coerceIn(0, 100)
          pb.progress = pct
          if (elapsed >= holdMs) {
            running = false
            pb.progress = 100
            onStopConfirmed()
          } else {
            main.postDelayed(this, 50)
          }
        }
      }

      override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            running = true
            startTs = System.currentTimeMillis()
            pb.progress = 0
            main.post(tick)
            v.isPressed = true
            return true
          }
          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            running = false
            main.removeCallbacks(tick)
            pb.progress = 0
            v.isPressed = false
            return true
          }
        }
        return false
      }
    })

    wm.addView(container, params)
    root = container
  }

  fun hide() {
    val v = root ?: return
    try { wm.removeView(v) } catch (_: Throwable) {}
    root = null
  }
}
