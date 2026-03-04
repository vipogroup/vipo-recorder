package com.vipo.recorder.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vipo.recorder.data.RecordingDb
import com.vipo.recorder.databinding.ActivityPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class PlayerActivity : ComponentActivity() {

  companion object {
    const val EXTRA_SESSION_ID = "sessionId"
  }

  private lateinit var b: ActivityPlayerBinding
  private var player: ExoPlayer? = null

  private data class SegmentUi(val idx: Int, val file: File, val sizeBytes: Long)
  private var segmentsUi: List<SegmentUi> = emptyList()
  private var totalBytes: Long = 0L

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityPlayerBinding.inflate(layoutInflater)
    setContentView(b.root)

    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

    player = ExoPlayer.Builder(this).build().also { p ->
      b.playerView.player = p
      p.addListener(object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          updateInfoText()
        }
      })
    }

    lifecycleScope.launch(Dispatchers.IO) {
      val segs = RecordingDb.get(this@PlayerActivity).dao().segmentsForSession(sessionId)

      val ui = segs.mapNotNull { s ->
        val f = File(s.path)
        if (!f.exists()) return@mapNotNull null
        SegmentUi(idx = s.idx, file = f, sizeBytes = f.length().coerceAtLeast(0L))
      }

      val items = ui.map { MediaItem.fromUri(Uri.fromFile(it.file)) }
      val total = ui.sumOf { it.sizeBytes }

      launch(Dispatchers.Main) {
        segmentsUi = ui
        totalBytes = total
        val p = player ?: return@launch
        p.setMediaItems(items)
        p.prepare()
        p.playWhenReady = true
        updateInfoText()
      }
    }
  }

  private fun updateInfoText() {
    val p = player ?: return
    val cur = p.currentMediaItemIndex
    val seg = segmentsUi.getOrNull(cur)
    val segText = if (seg != null) {
      "סגמנט ${seg.idx}/${segmentsUi.size}: ${formatBytes(seg.sizeBytes)}"
    } else {
      "סגמנט: -"
    }
    b.txtInfo.text = "$segText | סה\"כ: ${formatBytes(totalBytes)}"
  }

  private fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
      b >= gb -> String.format(Locale.getDefault(), "%.2f GB", b / gb)
      b >= mb -> String.format(Locale.getDefault(), "%.1f MB", b / mb)
      b >= kb -> String.format(Locale.getDefault(), "%.1f KB", b / kb)
      else -> "$b B"
    }
  }

  override fun onStop() {
    super.onStop()
    player?.release()
    player = null
  }
}
