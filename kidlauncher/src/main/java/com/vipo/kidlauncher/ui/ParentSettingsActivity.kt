package com.vipo.kidlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.vipo.kidlauncher.databinding.ActivityParentSettingsBinding
import com.vipo.kidlauncher.kiosk.KioskPolicy
import com.vipo.kidlauncher.kiosk.LaunchableApps
import com.vipo.kidlauncher.util.Prefs

class ParentSettingsActivity : ComponentActivity() {

  private lateinit var b: ActivityParentSettingsBinding
  private lateinit var appAdapter: AppSelectAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityParentSettingsBinding.inflate(layoutInflater)
    setContentView(b.root)

    appAdapter = AppSelectAdapter(packageManager)
    b.rvApps.layoutManager = LinearLayoutManager(this)
    b.rvApps.adapter = appAdapter

    b.btnSave.setOnClickListener {
      val allowed = appAdapter.selectedPackages().toMutableSet()
      autoAddRecorderIfInstalled(allowed)
      Prefs.setAllowedPackages(this, allowed)
      Prefs.setKidScreenName(this, b.editKidScreenName.text?.toString()?.trim().orEmpty())
      Toast.makeText(this, getString(com.vipo.kidlauncher.R.string.saved), Toast.LENGTH_SHORT).show()
    }

    b.btnActivate.setOnClickListener {
      if (!KioskPolicy.isDeviceOwner(this)) {
        Toast.makeText(this, "האפליקציה לא מוגדרת Device Owner עדיין", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }

      if (Prefs.getNormalHomeComponent(this).isBlank()) {
        Toast.makeText(this, "בחר קודם מסך בית רגיל כדי שנוכל לחזור למצב רגיל", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, ChooseNormalHomeActivity::class.java))
        return@setOnClickListener
      }

      val allowed = appAdapter.selectedPackages().toMutableSet()
      autoAddRecorderIfInstalled(allowed)

      if (allowed.isEmpty()) {
        Toast.makeText(this, "בחר לפחות אפליקציה אחת", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      if (!KioskPolicy.hasOverlayPermission(this)) {
        Toast.makeText(this, "נא לאשר הרשאת תצוגה מעל אפליקציות אחרות (כפתור חזרה)", Toast.LENGTH_LONG).show()
        KioskPolicy.requestOverlayPermission(this)
        return@setOnClickListener
      }

      Prefs.setAllowedPackages(this, allowed)
      Prefs.setChildModeEnabled(this, true)
      KioskPolicy.applyAllowedApps(this, allowed)
      Toast.makeText(this, "מצב ילד הופעל", Toast.LENGTH_SHORT).show()

      val home = Intent(this, KidHomeActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
      startActivity(home)
      finish()
    }

    b.btnDeactivate.setOnClickListener {
      if (KioskPolicy.isDeviceOwner(this)) {
        KioskPolicy.deactivateToSystemLauncher(this)
      } else {
        Prefs.setChildModeEnabled(this, false)
        Toast.makeText(this, "כובה (ללא Device Owner)", Toast.LENGTH_SHORT).show()
      }
      finish()
    }

    b.btnChangePin.setOnClickListener {
      startActivity(Intent(this, ChangePinActivity::class.java))
    }

    b.btnChooseNormalHome.setOnClickListener {
      startActivity(Intent(this, ChooseNormalHomeActivity::class.java))
    }

    b.btnRecordPerms.setOnClickListener { showRecordPermissionsDialog() }

    b.btnBack.setOnClickListener { finish() }

    loadApps()
    updateDeviceOwnerStatus()

    val savedName = Prefs.getKidScreenName(this)
    if (savedName.isNotBlank()) b.editKidScreenName.setText(savedName)
  }

  override fun onResume() {
    super.onResume()
    updateDeviceOwnerStatus()
  }

  private fun updateDeviceOwnerStatus() {
    b.txtDeviceOwner.text = if (KioskPolicy.isDeviceOwner(this)) {
      getString(com.vipo.kidlauncher.R.string.device_owner_yes)
    } else {
      getString(com.vipo.kidlauncher.R.string.device_owner_no)
    }
  }

  private fun loadApps() {
    val entries = LaunchableApps.queryLaunchableEntries(this)
      .filter { it.packageName != packageName }
    val allowed = Prefs.getAllowedPackages(this)
    appAdapter.submit(entries, allowed)
  }

  private fun showRecordPermissionsDialog() {
    val allowedPkgs = Prefs.getAllowedPackages(this)
    val entries = LaunchableApps.queryLaunchableEntries(this)
      .filter { it.packageName != packageName && it.packageName in allowedPkgs }

    if (entries.isEmpty()) {
      Toast.makeText(this, "אין אפליקציות מורשות במסך הילד", Toast.LENGTH_SHORT).show()
      return
    }

    val labels = entries.map { it.label }.toTypedArray()
    val recordable = Prefs.getRecordablePackages(this)
    val checked = BooleanArray(entries.size) { i ->
      if (recordable.isEmpty()) true else entries[i].packageName in recordable
    }

    val dialogView = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      setBackgroundColor(0xFF1A1A2E.toInt())
      setPadding(48, 32, 48, 16)

      val title = android.widget.TextView(this@ParentSettingsActivity).apply {
        text = "הרשאות הקלטה"
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 20f
        gravity = android.view.Gravity.CENTER
        setPadding(0, 0, 0, 24)
      }
      addView(title)

      val subtitle = android.widget.TextView(this@ParentSettingsActivity).apply {
        text = "בחר אילו אפליקציות יוקלטו:"
        setTextColor(0x99FFFFFF.toInt())
        textSize = 14f
        gravity = android.view.Gravity.CENTER
        setPadding(0, 0, 0, 16)
      }
      addView(subtitle)

      val scroll = android.widget.ScrollView(this@ParentSettingsActivity)
      val checkLayout = android.widget.LinearLayout(this@ParentSettingsActivity).apply {
        orientation = android.widget.LinearLayout.VERTICAL
      }

      for (i in entries.indices) {
        val cb = android.widget.CheckBox(this@ParentSettingsActivity).apply {
          text = labels[i]
          setTextColor(0xFFFFFFFF.toInt())
          buttonTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())
          isChecked = checked[i]
          textSize = 15f
          setPadding(16, 12, 16, 12)
          setOnCheckedChangeListener { _, isChecked -> checked[i] = isChecked }
        }
        checkLayout.addView(cb)
      }
      scroll.addView(checkLayout)
      addView(scroll, android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

      val btnLayout = android.widget.LinearLayout(this@ParentSettingsActivity).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER
        setPadding(0, 24, 0, 0)
      }

      val btnSave = android.widget.Button(this@ParentSettingsActivity).apply {
        text = "שמור"
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundResource(com.vipo.kidlauncher.R.drawable.btn_primary_bg)
        setPadding(48, 16, 48, 16)
      }
      val btnCancel = android.widget.Button(this@ParentSettingsActivity).apply {
        text = "ביטול"
        setTextColor(0xFFAAAAAA.toInt())
        setBackgroundResource(com.vipo.kidlauncher.R.drawable.btn_secondary_bg)
        setPadding(48, 16, 48, 16)
      }

      val btnParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = 8; marginEnd = 8
      }
      btnLayout.addView(btnCancel, btnParams)
      btnLayout.addView(btnSave, btnParams)
      addView(btnLayout)
    }

    val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
      .setView(dialogView)
      .create()

    val btnRow = (dialogView as android.view.ViewGroup).getChildAt(dialogView.childCount - 1) as android.view.ViewGroup
    (btnRow.getChildAt(1) as android.widget.Button).setOnClickListener {
      val selected = mutableSetOf<String>()
      for (i in entries.indices) {
        if (checked[i]) selected.add(entries[i].packageName)
      }
      Prefs.setRecordablePackages(this, selected)
      val intent = Intent("com.vipo.recorder.SET_RECORDABLE_PACKAGES").apply {
        setPackage("com.vipo.recorder")
        putStringArrayListExtra("packages", ArrayList(selected))
      }
      sendBroadcast(intent)
      Toast.makeText(this, "הרשאות הקלטה נשמרו", Toast.LENGTH_SHORT).show()
      dialog.dismiss()
    }
    (btnRow.getChildAt(0) as android.widget.Button).setOnClickListener {
      dialog.dismiss()
    }

    dialog.show()
  }

  private fun autoAddRecorderIfInstalled(allowed: MutableSet<String>) {
    val intent = packageManager.getLaunchIntentForPackage(KidHomeActivity.VIPO_RECORDER_PKG)
    if (intent != null) {
      allowed.add(KidHomeActivity.VIPO_RECORDER_PKG)
    }
  }
}
