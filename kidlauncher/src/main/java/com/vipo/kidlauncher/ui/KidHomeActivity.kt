package com.vipo.kidlauncher.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.vipo.kidlauncher.databinding.ActivityKidHomeBinding
import com.vipo.kidlauncher.kiosk.KioskPolicy
import com.vipo.kidlauncher.kiosk.LaunchableApps
import com.vipo.kidlauncher.svc.KidOverlayService
import com.vipo.kidlauncher.ui.AppTileAdapter.AppTile
import com.vipo.kidlauncher.util.Prefs

class KidHomeActivity : ComponentActivity() {

  private lateinit var b: ActivityKidHomeBinding
  private lateinit var adapter: AppTileAdapter
  private val handler = Handler(Looper.getMainLooper())
  private var titleLongPressRunnable: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityKidHomeBinding.inflate(layoutInflater)
    setContentView(b.root)

    Prefs.ensureDefaultPin(this)

    adapter = AppTileAdapter(this, emptyList(), ::onTileClick)
    b.rvApps.layoutManager = GridLayoutManager(this, 3)
    b.rvApps.adapter = adapter


    b.txtTitle.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          titleLongPressRunnable = Runnable {
            startActivity(Intent(this, ParentPinActivity::class.java)
              .putExtra(ParentPinActivity.EXTRA_ACTION, ParentPinActivity.ACTION_SETTINGS))
          }
          handler.postDelayed(titleLongPressRunnable!!, 3000L)
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          titleLongPressRunnable?.let { handler.removeCallbacks(it) }
          true
        }
        else -> false
      }
    }

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
      }
    })
  }

  override fun onResume() {
    super.onResume()
    refreshTiles()

    val customName = Prefs.getKidScreenName(this)
    if (customName.isNotBlank()) {
      b.txtTitle.text = customName
    }

    if (Prefs.isChildModeEnabled(this)) {
      KioskPolicy.startKioskIfPermitted(this)
      if (KioskPolicy.hasOverlayPermission(this)) {
        KidOverlayService.start(this)
      }
    }
  }

  private fun refreshTiles() {
    val allowed = Prefs.getAllowedPackages(this)

    val entries = LaunchableApps.queryLaunchableEntries(this)
      .filter { allowed.contains(it.packageName) }

    val tiles = mutableListOf<AppTile>()

    // Always show recording button (opens VIPORecorder) even if not explicitly chosen.
    tiles.add(AppTile(label = getString(com.vipo.kidlauncher.R.string.kid_record), packageName = VIPO_RECORDER_PKG, isRecordingButton = true))

    tiles.addAll(entries.map { AppTile(label = it.label, packageName = it.packageName) })

    adapter.submit(tiles.distinctBy { it.packageName })

    b.txtStatus.text = if (allowed.isEmpty()) {
      "אין אפליקציות מאושרות."
    } else {
      ""
    }
  }

  private fun onTileClick(tile: AppTile) {
    openApp(tile.packageName)
  }

  private fun openApp(pkg: String) {
    val i = packageManager.getLaunchIntentForPackage(pkg)
    if (i == null) {
      Toast.makeText(this, "לא ניתן לפתוח: $pkg", Toast.LENGTH_SHORT).show()
      return
    }
    runCatching { startActivity(i) }
  }

  companion object {
    const val VIPO_RECORDER_PKG = "com.vipo.recorder"
  }
}
