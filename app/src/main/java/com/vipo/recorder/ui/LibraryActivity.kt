package com.vipo.recorder.ui

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.vipo.recorder.R
import com.vipo.recorder.data.RecordingDb
import com.vipo.recorder.data.SessionSummary
import com.vipo.recorder.databinding.ActivityLibraryBinding
import com.vipo.recorder.util.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LibraryActivity : ComponentActivity() {

  private lateinit var b: ActivityLibraryBinding

  private data class PackageOption(val label: String, val pkg: String?) {
    override fun toString(): String = label
  }

  private data class SessionRow(val sessionId: String, val text: String)

  private var packageOptions: List<PackageOption> = emptyList()
  private var sessionRows: List<SessionRow> = emptyList()

  private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityLibraryBinding.inflate(layoutInflater)
    setContentView(b.root)

    b.rangeWeek.isChecked = true

    b.spnPackage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
      override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: android.view.View?,
        position: Int,
        id: Long
      ) {
        refresh()
      }

      override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
        refresh()
      }
    }

    b.radioRange.setOnCheckedChangeListener { _, _ ->
      refresh()
    }

    b.listSessions.setOnItemClickListener { _, _, position, _ ->
      val sid = sessionRows.getOrNull(position)?.sessionId ?: return@setOnItemClickListener
      val i = Intent(this, PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_SESSION_ID, sid)
      }
      startActivity(i)
    }

    b.listSessions.setOnItemLongClickListener { _, _, position, _ ->
      val sid = sessionRows.getOrNull(position)?.sessionId ?: return@setOnItemLongClickListener true

      val options = arrayOf("שתף לוואצאפ / מייל", "מחק הקלטה")
      AlertDialog.Builder(this)
        .setTitle("פעולות")
        .setItems(options) { _, which ->
          when (which) {
            0 -> shareSession(sid)
            1 -> {
              AlertDialog.Builder(this)
                .setTitle("מחיקת הקלטה")
                .setMessage("למחוק את ההקלטה הזאת? הפעולה תמחק גם את הקבצים מהטלפון.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("מחק") { _, _ ->
                  deleteSessionAndFiles(sid)
                }
                .show()
            }
          }
        }
        .show()

      true
    }

    loadPackages()
  }

  private fun deleteSessionAndFiles(sessionId: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      val dao = RecordingDb.get(this@LibraryActivity).dao()
      val segs = dao.segmentsForSession(sessionId)

      segs.forEach { s ->
        runCatching { File(s.path).delete() }
      }

      dao.deleteSegmentsForSession(sessionId)
      dao.deleteSession(sessionId)

      launch(Dispatchers.Main) {
        Toast.makeText(this@LibraryActivity, "נמחק", Toast.LENGTH_SHORT).show()
        refresh()
      }
    }
  }

  private fun loadPackages() {
    lifecycleScope.launch(Dispatchers.IO) {
      val dao = RecordingDb.get(this@LibraryActivity).dao()
      val pkgs = dao.distinctPackages()

      val options = mutableListOf(PackageOption(getString(R.string.all_apps), null))
      pkgs.forEach { pkg ->
        val label = appLabel(pkg)
        options.add(PackageOption("$label ($pkg)", pkg))
      }

      launch(Dispatchers.Main) {
        packageOptions = options
        val adapter = ArrayAdapter(
          this@LibraryActivity,
          R.layout.item_spinner,
          packageOptions
        ).also { it.setDropDownViewResource(R.layout.item_spinner_dropdown) }

        b.spnPackage.adapter = adapter
        b.spnPackage.setSelection(0)
        refresh()
      }
    }
  }

  private fun refresh() {
    val selectedPackage = packageOptions.getOrNull(b.spnPackage.selectedItemPosition)?.pkg

    val now = System.currentTimeMillis()
    val fromTs = when (b.radioRange.checkedRadioButtonId) {
      R.id.rangeDay -> now - 24L * 60L * 60L * 1000L
      R.id.rangeWeek -> now - 7L * 24L * 60L * 60L * 1000L
      R.id.rangeMonth -> now - 30L * 24L * 60L * 60L * 1000L
      R.id.rangeAll -> 0L
      else -> now - 7L * 24L * 60L * 60L * 1000L
    }

    lifecycleScope.launch(Dispatchers.IO) {
      val dao = RecordingDb.get(this@LibraryActivity).dao()
      val summaries: List<SessionSummary> = if (selectedPackage == null) {
        dao.sessionSummariesAll(fromTs, now)
      } else {
        dao.sessionSummariesFiltered(fromTs, now, selectedPackage)
      }

      val segFiles = if (selectedPackage == null) {
        dao.segmentFilesAll(fromTs, now)
      } else {
        dao.segmentFilesFiltered(fromTs, now, selectedPackage)
      }
      val sizeBySessionId = HashMap<String, Long>()
      for (row in segFiles) {
        val f = File(row.path)
        val len = if (f.exists()) f.length() else 0L
        sizeBySessionId[row.sessionId] = (sizeBySessionId[row.sessionId] ?: 0L) + len
      }

      val rows = summaries.map { s ->
        val sizeBytes = sizeBySessionId[s.sessionId] ?: 0L
        SessionRow(
          sessionId = s.sessionId,
          text = sessionLabel(s, sizeBytes)
        )
      }

      launch(Dispatchers.Main) {
        sessionRows = rows
        val adapter = ArrayAdapter(
          this@LibraryActivity,
          R.layout.item_session,
          sessionRows.map { it.text }
        )
        b.listSessions.adapter = adapter
        b.txtStatus.text = getString(R.string.found_sessions, sessionRows.size)
      }
    }
  }

  private fun sessionLabel(s: SessionSummary): String {
    val start = dateFmt.format(Date(s.startTs))
    val dur = formatDuration(s.totalDurationMs)
    return "$start | $dur | ${s.segmentCount} segments"
  }

  private fun sessionLabel(s: SessionSummary, sizeBytes: Long): String {
    val start = dateFmt.format(Date(s.startTs))
    val dur = formatDuration(s.totalDurationMs)
    val size = formatBytes(sizeBytes)
    return "$start | $dur | ${s.segmentCount} segments | $size"
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

  private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) {
      String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
      String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
  }

  private fun shareSession(sessionId: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      val dao = RecordingDb.get(this@LibraryActivity).dao()
      val segs = dao.segmentsForSession(sessionId)

      val files = segs.map { File(it.path) }.filter { it.exists() && it.length() > 0 }

      if (files.isEmpty()) {
        launch(Dispatchers.Main) {
          Toast.makeText(this@LibraryActivity, "אין קבצי הקלטה לשיתוף", Toast.LENGTH_SHORT).show()
        }
        return@launch
      }

      val totalMB = files.sumOf { it.length() } / (1024.0 * 1024.0)

      launch(Dispatchers.Main) {
        if (totalMB > 15) {
          AlertDialog.Builder(this@LibraryActivity)
            .setTitle("הקובץ גדול (${String.format("%.0f", totalMB)}MB)")
            .setMessage("רוצה לדחוס לפני שליחה?\nדחיסה תקטין את הגודל בכ-90%.")
            .setPositiveButton("דחוס ושתף") { _, _ ->
              compressAndShare(files)
            }
            .setNegativeButton("שתף כמו שזה") { _, _ ->
              shareFiles(files)
            }
            .setNeutralButton("ביטול", null)
            .show()
        } else {
          shareFiles(files)
        }
      }
    }
  }

  private fun compressAndShare(files: List<File>) {
    @Suppress("DEPRECATION")
    val progress = ProgressDialog(this).apply {
      setMessage("דוחס הקלטה... אנא המתן")
      setCancelable(false)
      isIndeterminate = true
      show()
    }

    lifecycleScope.launch(Dispatchers.IO) {
      val compressDir = File(filesDir, "compressed")
      compressDir.mkdirs()

      val compressedFiles = mutableListOf<File>()
      var allSuccess = true

      for (f in files) {
        val result = VideoCompressor.compress(this@LibraryActivity, f, compressDir)
        if (result.success && result.outputFile != null) {
          compressedFiles.add(result.outputFile)
        } else {
          allSuccess = false
          compressedFiles.add(f) // fallback to original
        }
      }

      launch(Dispatchers.Main) {
        progress.dismiss()
        if (!allSuccess) {
          Toast.makeText(this@LibraryActivity, "חלק מהקבצים לא נדחסו — נשלחים כמו שהם", Toast.LENGTH_SHORT).show()
        }
        val totalMB = compressedFiles.sumOf { it.length() } / (1024.0 * 1024.0)
        Toast.makeText(this@LibraryActivity, "גודל אחרי דחיסה: ${String.format("%.1f", totalMB)}MB", Toast.LENGTH_SHORT).show()
        shareFiles(compressedFiles)
      }
    }
  }

  private fun shareFiles(files: List<File>) {
    val uris = ArrayList<Uri>()
    for (f in files) {
      val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
      uris.add(uri)
    }

    val shareIntent = if (uris.size == 1) {
      Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uris[0])
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    } else {
      Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "video/mp4"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }

    startActivity(Intent.createChooser(shareIntent, "שתף הקלטה"))
  }

  private fun appLabel(pkg: String): String {
    return try {
      val ai = packageManager.getApplicationInfo(pkg, 0)
      packageManager.getApplicationLabel(ai).toString()
    } catch (_: Throwable) {
      pkg
    }
  }
}
