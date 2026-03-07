package com.vipo.recorder.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vipo.recorder.R
import com.vipo.recorder.databinding.ActivityParentAppsBinding
import com.vipo.recorder.kiosk.KioskPolicy
import com.vipo.recorder.kiosk.LaunchableApps
import com.vipo.recorder.util.Prefs

class ParentAppsActivity : ComponentActivity() {

  private lateinit var b: ActivityParentAppsBinding

  private var entries: List<LaunchableApps.Entry> = emptyList()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityParentAppsBinding.inflate(layoutInflater)
    setContentView(b.root)

    b.listApps.choiceMode = ListView.CHOICE_MODE_MULTIPLE

    b.btnSave.setOnClickListener {
      val allowed = selectedPackages()
      Prefs.setAllowedPackages(this, allowed)
      Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }

    b.btnActivate.setOnClickListener {
      if (!KioskPolicy.isDeviceOwner(this)) {
        Toast.makeText(this, "האפליקציה לא מוגדרת Device Owner עדיין", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }

      val allowed = selectedPackages()
      if (allowed.isEmpty()) {
        Toast.makeText(this, "בחר לפחות אפליקציה אחת", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      Prefs.setAllowedPackages(this, allowed)
      Prefs.setChildModeEnabled(this, true)
      KioskPolicy.applyAllowedApps(this, allowed)
      Toast.makeText(this, "מצב ילד הופעל", Toast.LENGTH_SHORT).show()
      val home = Intent(this, HomeActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
      startActivity(home)
      finish()
    }

    b.btnDeactivate.setOnClickListener {
      if (!KioskPolicy.isDeviceOwner(this)) {
        Prefs.setChildModeEnabled(this, false)
        Toast.makeText(this, "כובה (ללא Device Owner)", Toast.LENGTH_SHORT).show()
        finish()
        return@setOnClickListener
      }

      Prefs.setChildModeEnabled(this, false)
      KioskPolicy.clearAllSuspension(this)
      KioskPolicy.stopKiosk(this)
      Toast.makeText(this, "מצב ילד כובה", Toast.LENGTH_SHORT).show()

      val goHome = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
      startActivity(goHome)
      finish()
    }

    b.btnRecordPerms.setOnClickListener { showRecordPermissionsDialog() }

    b.btnBack.setOnClickListener { finish() }

    loadApps()
    updateDeviceOwnerStatus()
  }

  override fun onResume() {
    super.onResume()
    updateDeviceOwnerStatus()
  }

  private fun updateDeviceOwnerStatus() {
    b.txtDeviceOwner.text = if (KioskPolicy.isDeviceOwner(this)) {
      getString(R.string.parental_device_owner_yes)
    } else {
      getString(R.string.parental_device_owner_no)
    }
  }

  private fun loadApps() {
    entries = LaunchableApps.queryLaunchableEntries(this)
      .filter { it.packageName != packageName }

    val adapter = ArrayAdapter(
      this,
      android.R.layout.simple_list_item_multiple_choice,
      entries.map { "${it.label} (${it.packageName})" }
    )

    b.listApps.adapter = adapter

    val allowed = Prefs.getAllowedPackages(this)
    for (i in entries.indices) {
      b.listApps.setItemChecked(i, allowed.contains(entries[i].packageName))
    }
  }

  private fun selectedPackages(): Set<String> {
    val set = mutableSetOf<String>()
    for (i in entries.indices) {
      if (b.listApps.isItemChecked(i)) {
        set.add(entries[i].packageName)
      }
    }
    return set
  }

  private fun showRecordPermissionsDialog() {
    val allowedPkgs = Prefs.getAllowedPackages(this)
    val filteredEntries = entries.filter { it.packageName in allowedPkgs }

    if (filteredEntries.isEmpty()) {
      Toast.makeText(this, "אין אפליקציות מורשות", Toast.LENGTH_SHORT).show()
      return
    }

    val labels = filteredEntries.map { it.label }.toTypedArray()
    val recordable = Prefs.getRecordablePackages(this)
    val checked = BooleanArray(filteredEntries.size) { i ->
      if (recordable.isEmpty()) true else filteredEntries[i].packageName in recordable
    }

    AlertDialog.Builder(this)
      .setTitle("בחר אפליקציות להקלטה")
      .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
        checked[which] = isChecked
      }
      .setPositiveButton("שמור") { _, _ ->
        val selected = mutableSetOf<String>()
        for (i in filteredEntries.indices) {
          if (checked[i]) selected.add(filteredEntries[i].packageName)
        }
        Prefs.setRecordablePackages(this, selected)
        Toast.makeText(this, "הרשאות הקלטה נשמרו", Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton("ביטול", null)
      .show()
  }
}
