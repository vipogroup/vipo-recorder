package com.vipo.kidlauncher.svc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import com.vipo.kidlauncher.R

class KidOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var bottomBlocker: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false

    private val tapTimestamps = mutableListOf<Long>()
    private val TAP_COUNT = 5
    private val TAP_WINDOW_MS = 2000L

    private var phoneStateListener: PhoneStateListener? = null
    private var inCall = false
    private var callStartedAtMs = 0L

    // Primary: poll AudioManager mode — no permission needed
    // During active call: every 250ms to prevent child from disabling speaker
    // Idle: every 1000ms to save battery
    private val audioModePollRunnable = object : Runnable {
        override fun run() {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            var pollInterval = 1000L
            if (am != null) {
                val mode = am.mode
                val isCallMode = mode == AudioManager.MODE_IN_CALL ||
                        mode == AudioManager.MODE_IN_COMMUNICATION
                if (isCallMode) {
                    pollInterval = 250L // aggressive polling during call
                    if (!inCall) {
                        inCall = true
                        callStartedAtMs = System.currentTimeMillis()
                        // Delay 1.5s for audio routing to settle, then force speaker
                        handler.postDelayed({ forceSpeakerOn() }, 1500)
                    } else {
                        // After initial delay, keep enforcing speaker
                        val elapsed = System.currentTimeMillis() - callStartedAtMs
                        if (elapsed > 1500) {
                            forceSpeakerOn()
                        }
                    }
                } else {
                    inCall = false
                }
            }
            handler.postDelayed(this, pollInterval)
        }
    }

    companion object {
        private const val CHANNEL_ID = "kid_overlay_channel"
        private const val NOTIF_ID = 101
        private const val LONG_PRESS_DURATION = 3000L

        fun start(ctx: Context) {
            val intent = Intent(ctx, KidOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KidOverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addOverlayButton()
        startSpeakerEnforcer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeakerEnforcer()
        longPressRunnable?.let { handler.removeCallbacks(it) }
        floatingView?.let {
            runCatching { windowManager?.removeView(it) }
        }
        floatingView = null
        bottomBlocker?.let {
            runCatching { windowManager?.removeView(it) }
        }
        bottomBlocker = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kid Mode Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "מצב ילד פעיל" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("מצב ילד פעיל")
            .setContentText("לחיצה ארוכה 3 שניות על הכפתור הצף → חזרה הביתה")
            .setOngoing(true)
            .build()
    }

    private fun addOverlayButton() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val btn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setBackgroundResource(R.drawable.overlay_home_bg)
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setColorFilter(0xFFFFFFFF.toInt())
            alpha = 0.7f
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            (56 * resources.displayMetrics.density).toInt(),
            (56 * resources.displayMetrics.density).toInt(),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (resources.displayMetrics.heightPixels * 0.4).toInt()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isLongPressTriggered = false

                    v.alpha = 1f
                    v.scaleX = 1.2f
                    v.scaleY = 1.2f

                    longPressRunnable = Runnable {
                        isLongPressTriggered = true
                        goHome()
                    }
                    handler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                    }

                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { windowManager?.updateViewLayout(v, params) }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    v.alpha = 0.7f
                    v.scaleX = 1f
                    v.scaleY = 1f

                    if (!isLongPressTriggered && event.action == MotionEvent.ACTION_UP) {
                        val dx = Math.abs(event.rawX - initialTouchX)
                        val dy = Math.abs(event.rawY - initialTouchY)
                        if (dx < 15 && dy < 15) {
                            registerTap()
                        }
                    }
                    true
                }

                else -> false
            }
        }

        floatingView = btn
        runCatching { windowManager?.addView(btn, params) }
    }

    private fun addBottomBlocker() {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        val density = resources.displayMetrics.density
        val blockerHeight = (48 * density).toInt() // covers nav bar area

        val blocker = View(this).apply {
            setBackgroundColor(0x01000000) // almost transparent but catches touches
            setOnTouchListener { _, _ -> true } // consume all touches
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            blockerHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        bottomBlocker = blocker
        runCatching { windowManager?.addView(blocker, params) }
    }

    private fun pressBack() {
        // Simulate BACK key press using instrumentation
        Thread {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("input", "keyevent", "4"))
            }
        }.start()
    }

    private fun registerTap() {
        val now = System.currentTimeMillis()
        tapTimestamps.add(now)
        tapTimestamps.removeAll { now - it > TAP_WINDOW_MS }

        if (tapTimestamps.size >= TAP_COUNT) {
            tapTimestamps.clear()
            openParentMenu()
        }
    }

    private fun openParentMenu() {
        val intent = Intent().apply {
            setClassName(packageName, "com.vipo.kidlauncher.ui.ParentPinActivity")
            putExtra("action", "settings")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    private fun startSpeakerEnforcer() {
        // Start AudioManager mode polling (works without any permissions)
        handler.post(audioModePollRunnable)

        // Also try PhoneStateListener as backup (needs READ_PHONE_STATE)
        runCatching {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                @Suppress("DEPRECATION")
                phoneStateListener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        when (state) {
                            TelephonyManager.CALL_STATE_OFFHOOK -> {
                                if (!inCall) {
                                    inCall = true
                                    callStartedAtMs = System.currentTimeMillis()
                                    handler.postDelayed({ forceSpeakerOn() }, 1500)
                                }
                            }
                            TelephonyManager.CALL_STATE_IDLE -> {
                                inCall = false
                            }
                        }
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun stopSpeakerEnforcer() {
        inCall = false
        handler.removeCallbacks(audioModePollRunnable)
        runCatching {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            phoneStateListener?.let { tm?.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        phoneStateListener = null
    }

    @Suppress("DEPRECATION")
    private fun forceSpeakerOn() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (!am.isSpeakerphoneOn) {
            am.isSpeakerphoneOn = true
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(homeIntent) }
    }
}
