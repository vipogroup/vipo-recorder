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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private lateinit var b: ActivityMainBinding
  private val REQ_CAPTURE = 1001

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityMainBinding.inflate(layoutInflater)
    setContentView(b.root)

    if (Build.VERSION.SDK_INT >= 33) {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
        2001
      )
    }

    b.btnStart.setOnClickListener { requestCapturePermission() }
    b.btnStop.setOnClickListener { stopSession() }
    b.btnPlayLatest.setOnClickListener { playLatest() }
    b.btnLibrary.setOnClickListener { openLibrary() }
  }

  private fun openLibrary() {
    startActivity(Intent(this, LibraryActivity::class.java))
  }

  private fun requestCapturePermission() {
    // Optional but recommended: overlay permission for 5s-hold STOP bubble
    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
      val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
      startActivity(intent)
    }

    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQ_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
      val i = Intent(this, ScreenRecordService::class.java).apply {
        action = ScreenRecordService.ACTION_START_SESSION
        putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
        putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
      }
      ContextCompat.startForegroundService(this, i)
      b.txtStatus.text = "Status: recording (smart segments)."
    }
  }

  private fun stopSession() {
    val i = Intent(this, ScreenRecordService::class.java).apply {
      action = ScreenRecordService.ACTION_STOP_SESSION
    }
    startService(i)
    b.txtStatus.text = "Status: stopped."
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
