package com.vipo.kidlauncher.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vipo.kidlauncher.databinding.ActivityChooseNormalHomeBinding
import com.vipo.kidlauncher.util.Prefs

class ChooseNormalHomeActivity : ComponentActivity() {

  private lateinit var b: ActivityChooseNormalHomeBinding

  private data class Row(val label: String, val component: ComponentName) {
    override fun toString(): String = label
  }

  private var rows: List<Row> = emptyList()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityChooseNormalHomeBinding.inflate(layoutInflater)
    setContentView(b.root)

    b.listHomes.choiceMode = ListView.CHOICE_MODE_SINGLE

    loadHomes()

    b.btnSave.setOnClickListener {
      val pos = b.listHomes.checkedItemPosition
      val row = rows.getOrNull(pos)
      if (row == null) {
        Toast.makeText(this, "בחר מסך בית", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      Prefs.setNormalHomeComponent(this, row.component.flattenToString())
      Toast.makeText(this, "נשמר", Toast.LENGTH_SHORT).show()
      finish()
    }

    b.btnBack.setOnClickListener { finish() }
  }

  private fun loadHomes() {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addCategory(Intent.CATEGORY_DEFAULT)
    }

    val ris = pm.queryIntentActivities(intent, 0)

    val saved = Prefs.getNormalHomeComponent(this)

    rows = ris
      .mapNotNull { ri ->
        val ai = ri.activityInfo ?: return@mapNotNull null
        if (ai.packageName == packageName) return@mapNotNull null
        val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull()
          ?: ai.packageName
        Row(label = "$label (${ai.packageName})", component = ComponentName(ai.packageName, ai.name))
      }
      .distinctBy { it.component.flattenToString() }
      .sortedWith(compareBy({ it.label.lowercase() }, { it.component.packageName }))

    b.listHomes.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, rows)

    if (rows.isNotEmpty()) {
      val idx = rows.indexOfFirst { it.component.flattenToString() == saved }
      b.listHomes.setItemChecked(if (idx >= 0) idx else 0, true)
    }
  }
}
