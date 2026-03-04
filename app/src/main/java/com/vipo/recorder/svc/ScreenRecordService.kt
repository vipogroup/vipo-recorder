package com.vipo.recorder.svc

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.vipo.recorder.R
import com.vipo.recorder.data.RecordingDb
import com.vipo.recorder.data.SegmentEntity
import com.vipo.recorder.data.SessionEntity
import com.vipo.recorder.util.DisplayUtils
import com.vipo.recorder.util.Ids
import com.vipo.recorder.util.Prefs
import kotlinx.coroutines.*
import java.io.File

/**
 * Smart screen recorder:
 * - Records screen to internal app storage (hidden)
 * - Splits recording to segments
 * - Stops segment when idle, starts a new segment when activity resumes
 */
class ScreenRecordService : Service() {

  companion object {
    const val ACTION_START_SESSION = "vipo.START_SESSION"
    const val ACTION_STOP_SESSION = "vipo.STOP_SESSION"
    const val ACTION_ACTIVITY_PING = "vipo.ACTIVITY_PING"

    const val EXTRA_RESULT_CODE = "resultCode"
    const val EXTRA_RESULT_DATA = "data"
    const val EXTRA_LAST_PACKAGE = "lastPackage"

    private const val NOTIF_CHANNEL_ID = "vipo_recorder"
    private const val NOTIF_ID = 1001

    // Idle behavior
    private const val IDLE_TIMEOUT_MS = 30_000L // 30s
    private const val TICK_MS = 1000L
    private const val GRACE_AFTER_START_MS = 7_000L
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val io = Dispatchers.IO

  private var mp: MediaProjection? = null
  private var vd: VirtualDisplay? = null
  private var recorder: MediaRecorder? = null

  private var activeSessionId: String? = null
  private var sessionStartTs: Long = 0L

  private var activeSegmentId: String? = null
  private var activeSegmentIdx: Int = 0
  private var activeSegmentStartTs: Long = 0L
  private var activeSegmentPath: String? = null

  private var lastActiveTs: Long = 0L
  private var lastPackage: String? = null

  private var hasActivitySignal: Boolean = false

  private val handler = Handler(Looper.getMainLooper())
  private val overlay by lazy { OverlayController(this) }
  private val ticker = object : Runnable {
    override fun run() {
      val now = System.currentTimeMillis()
      val sid = activeSessionId
      if (sid != null) {
        val grace = now - sessionStartTs < GRACE_AFTER_START_MS
        val idle = now - lastActiveTs > IDLE_TIMEOUT_MS
        val isSegOn = activeSegmentId != null

        if (hasActivitySignal && !grace && idle && isSegOn) {
          // stop current segment due to idle
          stopSegment(reason = "idle")
        }
      }
      handler.postDelayed(this, TICK_MS)
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotifChannel()
    handler.post(ticker)
  }

  override fun onDestroy() {
    overlay.hide()
    handler.removeCallbacks(ticker)
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_SESSION -> {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || data == null) {
          stopSelf()
          return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        startSession(resultCode, data)
      }

      ACTION_STOP_SESSION -> {
        stopSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }

      ACTION_ACTIVITY_PING -> {
        // Update activity time and last package; if session active and no segment -> start new segment
        hasActivitySignal = true
        lastActiveTs = System.currentTimeMillis()
        lastPackage = intent.getStringExtra(EXTRA_LAST_PACKAGE) ?: lastPackage

        if (activeSessionId != null && activeSegmentId == null) {
          startSegment()
        }
      }
    }
    return START_STICKY
  }

  private fun startSession(resultCode: Int, data: Intent) {
    if (activeSessionId != null) return

    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    mp = mpm.getMediaProjection(resultCode, data)

    val sid = Ids.sessionId()
    activeSessionId = sid
    sessionStartTs = System.currentTimeMillis()
    lastActiveTs = sessionStartTs
    hasActivitySignal = false
    activeSegmentIdx = 0

    scope.launch(io) {
      val db = RecordingDb.get(this@ScreenRecordService)
      db.dao().upsertSession(
        SessionEntity(
          sessionId = sid,
          type = "SCREEN",
          startTs = sessionStartTs,
          endTs = null,
          title = null,
          note = null
        )
      )
    }

    // Start first segment immediately
    startSegment()

    // Floating overlay STOP (hold 5s). Requires overlay permission.
    overlay.show {
      // stop session from overlay
      val stopI = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP_SESSION }
      startService(stopI)
    }
  }

  private fun stopSession() {
    val sid = activeSessionId ?: return
    stopSegment(reason = "stop_session")
    try { vd?.release() } catch (_: Throwable) {}
    vd = null
    try { mp?.stop() } catch (_: Throwable) {}
    mp = null

    val endTs = System.currentTimeMillis()
    scope.launch(io) {
      RecordingDb.get(this@ScreenRecordService).dao().closeSession(sid, endTs)
    }

    overlay.hide()

    activeSessionId = null
    hasActivitySignal = false
  }

  private fun startSegment() {
    val sid = activeSessionId ?: return
    if (activeSegmentId != null) return
    val projection = mp ?: return

    val segId = Ids.segmentId()
    activeSegmentId = segId
    activeSegmentIdx += 1
    activeSegmentStartTs = System.currentTimeMillis()

    val outFile = createSegmentFile(sid, activeSegmentIdx)
    activeSegmentPath = outFile.absolutePath

    val m = DisplayUtils.metrics(this)
    val scale = Prefs.getRecordScale(this)
    val targetW = ((m.w.toFloat() * scale).toInt()).coerceAtLeast(2)
    val targetH = ((m.h.toFloat() * scale).toInt()).coerceAtLeast(2)
    val w = if (targetW % 2 == 0) targetW else targetW - 1
    val h = if (targetH % 2 == 0) targetH else targetH - 1
    val baseBitrate = 8_000_000
    val bitrate = (baseBitrate.toDouble() * (scale.toDouble() * scale.toDouble()))
      .toInt()
      .coerceAtLeast(1_000_000)

    val rec = MediaRecorder().apply {
      setVideoSource(MediaRecorder.VideoSource.SURFACE)
      setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      setOutputFile(outFile.absolutePath)

      setVideoEncoder(MediaRecorder.VideoEncoder.H264)
      setVideoSize(w, h)
      setVideoFrameRate(30)
      setVideoEncodingBitRate(bitrate)

      prepare()
    }
    recorder = rec

    val surface = rec.surface
    vd = projection.createVirtualDisplay(
      "VIPORecorderDisplay",
      w, h, m.densityDpi,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      surface,
      null,
      null
    )

    // Insert segment row (endTs filled later)
    scope.launch(io) {
      RecordingDb.get(this@ScreenRecordService).dao().upsertSegment(
        SegmentEntity(
          segmentId = segId,
          sessionId = sid,
          idx = activeSegmentIdx,
          startTs = activeSegmentStartTs,
          endTs = null,
          durationMs = null,
          path = outFile.absolutePath,
          lastPackage = lastPackage
        )
      )
    }

    rec.start()
  }

  private fun stopSegment(reason: String) {
    val segId = activeSegmentId ?: return

    // Stop recorder safely
    try { recorder?.stop() } catch (_: Throwable) {}
    try { recorder?.reset() } catch (_: Throwable) {}
    try { recorder?.release() } catch (_: Throwable) {}
    recorder = null

    try { vd?.release() } catch (_: Throwable) {}
    vd = null

    val endTs = System.currentTimeMillis()
    val dur = (endTs - activeSegmentStartTs).coerceAtLeast(0L)

    // If segment is too short (accidental), delete it and remove from DB
    val path = activeSegmentPath
    val tooShort = dur < 1200L // < 1.2s
    scope.launch(io) {
      val dao = RecordingDb.get(this@ScreenRecordService).dao()
      if (!tooShort) {
        dao.closeSegment(segId, endTs, dur)
      } else {
        // crude cleanup: mark it closed with 0, and delete file; (simple MVP)
        dao.closeSegment(segId, endTs, 0L)
        if (path != null) runCatching { File(path).delete() }
      }
    }

    activeSegmentId = null
    activeSegmentPath = null
  }

  private fun createSegmentFile(sessionId: String, idx: Int): File {
    val base = File(filesDir, "records/sessions/$sessionId")
    if (!base.exists()) base.mkdirs()
    val ts = System.currentTimeMillis()
    val name = "seg_%04d_%d.mp4".format(idx, ts)
    return File(base, name)
  }

  private fun buildNotification(): Notification {
    val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
      action = ACTION_STOP_SESSION
    }
    val stopPi = PendingIntent.getService(
      this, 1, stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.presence_video_online)
      .setContentTitle("VIPORecorder")
      .setContentText("Recording (smart segments) …")
      .setOngoing(true)
      .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
      .build()
  }

  private fun createNotifChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val ch = NotificationChannel(
        NOTIF_CHANNEL_ID,
        "VIPO Recorder",
        NotificationManager.IMPORTANCE_LOW
      )
      mgr.createNotificationChannel(ch)
    }
  }
}
