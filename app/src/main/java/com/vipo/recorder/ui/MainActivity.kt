package com.vipo.recorder.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vipo.recorder.databinding.ActivityMainBinding
import com.vipo.recorder.data.RecordingDb
import com.vipo.recorder.svc.ScreenRecordService
import com.vipo.recorder.util.Prefs
import com.vipo.recorder.R
import com.vipo.recorder.util.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private lateinit var b: ActivityMainBinding
  private val REQ_CAPTURE = 1001
  private val REQ_OVERLAY = 1002

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (Prefs.isChildModeEnabled(this)) {
      val home = Intent(this, HomeActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
      startActivity(home)
      finish()
      return
    }

    b = ActivityMainBinding.inflate(layoutInflater)
    setContentView(b.root)

    val permsNeeded = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= 33) {
      permsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      permsNeeded.add(android.Manifest.permission.RECORD_AUDIO)
    }
    if (permsNeeded.isNotEmpty()) {
      ActivityCompat.requestPermissions(this, permsNeeded.toTypedArray(), 2001)
    }

    b.btnStart.setOnClickListener { requestCapturePermission() }
    b.btnStop.setOnClickListener { stopSession() }
    b.btnPlayLatest.setOnClickListener { playLatest() }
    b.btnLibrary.setOnClickListener { openLibrary() }
    b.btnSettings.setOnClickListener { openSettings() }

    val alreadyRecording = ScreenRecordService.isRecording
    setRecordingState(alreadyRecording)
    if (alreadyRecording) {
      b.txtStatus.text = "Status: recording (smart segments)."
    }

    UpdateManager.checkForUpdate(this)
  }

  override fun onResume() {
    super.onResume()
    val recording = ScreenRecordService.isRecording
    setRecordingState(recording)
    if (recording) {
      b.txtStatus.text = "Status: recording (smart segments)."
    }
    UpdateManager.tryInstallPending(this)
  }

  private fun openLibrary() {
    startActivity(Intent(this, LibraryActivity::class.java))
  }

  private fun openSettings() {
    startActivity(Intent(this, SettingsActivity::class.java))
  }

  private fun requestCapturePermission() {
    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
      val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
      startActivityForResult(intent, REQ_OVERLAY)
      return
    }
    launchCapture()
  }

  private fun launchCapture() {
    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQ_OVERLAY) {
      // Returned from overlay settings — try capture now regardless
      launchCapture()
      return
    }
    if (requestCode == REQ_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
      val i = Intent(this, ScreenRecordService::class.java).apply {
        action = ScreenRecordService.ACTION_START_SESSION
        putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
        putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
      }
      ContextCompat.startForegroundService(this, i)
      b.txtStatus.text = "Status: recording (smart segments)."
      setRecordingState(true)
    }
  }

  private fun stopSession() {
    val i = Intent(this, ScreenRecordService::class.java).apply {
      action = ScreenRecordService.ACTION_STOP_SESSION
    }
    startService(i)
    b.txtStatus.text = "Status: stopped."
    setRecordingState(false)
  }

  private fun setRecordingState(recording: Boolean) {
    if (recording) {
      b.btnStart.isEnabled = false
      b.btnStart.setBackgroundResource(R.drawable.btn_disabled_bg)
      b.btnStart.setTextColor(0xFF555555.toInt())
      b.btnStop.isEnabled = true
      b.btnStop.setBackgroundResource(R.drawable.btn_record_bg)
      b.btnStop.setTextColor(0xFFFFFFFF.toInt())
    } else {
      b.btnStart.isEnabled = true
      b.btnStart.setBackgroundResource(R.drawable.btn_start_bg)
      b.btnStart.setTextColor(0xFFFFFFFF.toInt())
      b.btnStop.isEnabled = false
      b.btnStop.setBackgroundResource(R.drawable.btn_disabled_bg)
      b.btnStop.setTextColor(0xFF555555.toInt())
    }
  }

  private fun playLatest() {
    lifecycleScope.launch(Dispatchers.IO) {
      val latest = RecordingDb.get(this@MainActivity).dao().latestSession()
      if (latest != null) {
        val pi = Intent(this@MainActivity, PlayerActivity::class.java).apply {
          putExtra(PlayerActivity.EXTRA_SESSION_ID, latest.sessionId)
        }
        runOnUiThread { startActivity(pi) }
      } else {
        runOnUiThread { b.txtStatus.text = "Status: no sessions yet." }
      }
    }
  }
}
