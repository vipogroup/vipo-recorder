package com.vipo.recorder.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.vipo.recorder.R
import com.vipo.recorder.data.RecordingDb
import com.vipo.recorder.data.SessionSummary
import com.vipo.recorder.databinding.ActivityLibraryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    loadPackages()
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
          android.R.layout.simple_spinner_item,
          packageOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

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
      val summaries: List<SessionSummary> = dao.sessionSummaries(fromTs, now, selectedPackage)

      val rows = summaries.map { s ->
        SessionRow(
          sessionId = s.sessionId,
          text = sessionLabel(s)
        )
      }

      launch(Dispatchers.Main) {
        sessionRows = rows
        val adapter = ArrayAdapter(
          this@LibraryActivity,
          android.R.layout.simple_list_item_1,
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

  private fun appLabel(pkg: String): String {
    return try {
      val ai = packageManager.getApplicationInfo(pkg, 0)
      packageManager.getApplicationLabel(ai).toString()
    } catch (_: Throwable) {
      pkg
    }
  }
}
