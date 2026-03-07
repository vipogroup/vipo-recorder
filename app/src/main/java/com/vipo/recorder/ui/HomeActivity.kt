package com.vipo.recorder.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vipo.recorder.databinding.ActivityHomeBinding
import com.vipo.recorder.kiosk.KioskPolicy
import com.vipo.recorder.kiosk.LaunchableApps
import com.vipo.recorder.util.Prefs

class HomeActivity : ComponentActivity() {

  private lateinit var b: ActivityHomeBinding

  private data class AllowedRow(val label: String, val pkg: String) {
    override fun toString(): String = label
  }

  private var rows: List<AllowedRow> = emptyList()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityHomeBinding.inflate(layoutInflater)
    setContentView(b.root)

    b.btnParent.setOnClickListener {
      startActivity(Intent(this, ParentPinActivity::class.java))
    }

    b.listApps.setOnItemClickListener { _, _, position, _ ->
      val pkg = rows.getOrNull(position)?.pkg ?: return@setOnItemClickListener
      openApp(pkg)
    }
  }

  override fun onResume() {
    super.onResume()
    refreshList()

    if (Prefs.isChildModeEnabled(this)) {
      KioskPolicy.startKioskIfPermitted(this)
    }
  }

  private fun refreshList() {
    val allowed = Prefs.getAllowedPackages(this)

    val entries = LaunchableApps.queryLaunchableEntries(this)
      .filter { allowed.contains(it.packageName) }

    rows = entries.map { AllowedRow(label = "${it.label} (${it.packageName})", pkg = it.packageName) }

    b.listApps.adapter = ArrayAdapter(
      this,
      android.R.layout.simple_list_item_1,
      rows
    )

    if (allowed.isEmpty()) {
      b.txtStatus.text = "אין אפליקציות מאושרות. לחץ 'הורה (PIN)' כדי לאשר אפליקציות."
    } else {
      b.txtStatus.text = getString(com.vipo.recorder.R.string.parental_home_status)
    }
  }

  private fun openApp(pkg: String) {
    val i = packageManager.getLaunchIntentForPackage(pkg)
    if (i == null) {
      Toast.makeText(this, "לא ניתן לפתוח: $pkg", Toast.LENGTH_SHORT).show()
      return
    }
    runCatching { startActivity(i) }
  }
}
