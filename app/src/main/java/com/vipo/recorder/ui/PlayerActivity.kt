package com.vipo.recorder.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.vipo.recorder.data.RecordingDb
import com.vipo.recorder.databinding.ActivityPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PlayerActivity : ComponentActivity() {

  companion object {
    const val EXTRA_SESSION_ID = "sessionId"
  }

  private lateinit var b: ActivityPlayerBinding
  private var player: ExoPlayer? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityPlayerBinding.inflate(layoutInflater)
    setContentView(b.root)

    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

    player = ExoPlayer.Builder(this).build().also { p ->
      b.playerView.player = p
    }

    lifecycleScope.launch(Dispatchers.IO) {
      val segs = RecordingDb.get(this@PlayerActivity).dao().segmentsForSession(sessionId)
      val items = segs.mapNotNull { s ->
        val f = File(s.path)
        if (f.exists()) MediaItem.fromUri(Uri.fromFile(f)) else null
      }
      launch(Dispatchers.Main) {
        val p = player ?: return@launch
        p.setMediaItems(items)
        p.prepare()
        p.playWhenReady = true
      }
    }
  }

  override fun onStop() {
    super.onStop()
    player?.release()
    player = null
  }
}
