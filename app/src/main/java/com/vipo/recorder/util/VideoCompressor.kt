package com.vipo.recorder.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

object VideoCompressor {

  data class Result(val success: Boolean, val outputFile: File?, val error: String? = null)

  /**
   * Compress a video file using Media3 Transformer.
   * Must be called from a background thread.
   */
  fun compress(
    context: Context,
    inputFile: File,
    outputDir: File
  ): Result {
    if (!inputFile.exists()) return Result(false, null, "Input file not found")

    outputDir.mkdirs()
    val outName = inputFile.nameWithoutExtension + "_compressed.mp4"
    val outputFile = File(outputDir, outName)
    if (outputFile.exists()) outputFile.delete()

    val latch = CountDownLatch(1)
    val resultRef = AtomicReference<Result>()

    val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
    val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

    val transformer = Transformer.Builder(context)
      .setVideoMimeType("video/avc")
      .build()

    transformer.addListener(object : Transformer.Listener {
      override fun onCompleted(composition: Composition, exportResult: ExportResult) {
        resultRef.set(Result(true, outputFile))
        latch.countDown()
      }

      override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
        resultRef.set(Result(false, null, exportException.message))
        latch.countDown()
      }
    })

    // Must start on main thread
    android.os.Handler(android.os.Looper.getMainLooper()).post {
      transformer.start(editedMediaItem, outputFile.absolutePath)
    }

    latch.await()
    return resultRef.get() ?: Result(false, null, "Unknown error")
  }
}
