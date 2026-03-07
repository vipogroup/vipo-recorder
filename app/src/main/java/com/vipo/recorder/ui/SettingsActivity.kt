package com.vipo.recorder.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vipo.recorder.R
import com.vipo.recorder.databinding.ActivitySettingsBinding
import com.vipo.recorder.util.Prefs

class SettingsActivity : ComponentActivity() {

  private lateinit var b: ActivitySettingsBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivitySettingsBinding.inflate(layoutInflater)
    setContentView(b.root)

    val scale = Prefs.getRecordScale(this)
    when (nearestScale(scale)) {
      1.0f -> b.radioRes100.isChecked = true
      0.75f -> b.radioRes75.isChecked = true
      else -> b.radioRes50.isChecked = true
    }

    b.radioResolution.setOnCheckedChangeListener { _, checkedId ->
      val newScale = when (checkedId) {
        b.radioRes100.id -> 1.0f
        b.radioRes75.id -> 0.75f
        b.radioRes50.id -> 0.5f
        else -> 1.0f
      }
      Prefs.setRecordScale(this, newScale)
    }

    val quality = Prefs.getQuality(this)
    when (quality) {
      Prefs.QUALITY_LOW -> b.radioQualityLow.isChecked = true
      Prefs.QUALITY_HIGH -> b.radioQualityHigh.isChecked = true
      else -> b.radioQualityMedium.isChecked = true
    }

    b.radioQuality.setOnCheckedChangeListener { _, checkedId ->
      val q = when (checkedId) {
        b.radioQualityLow.id -> Prefs.QUALITY_LOW
        b.radioQualityHigh.id -> Prefs.QUALITY_HIGH
        else -> Prefs.QUALITY_MEDIUM
      }
      Prefs.setQuality(this, q)
    }

    b.editUpdateUrl.setText(Prefs.getUpdateJsonUrl(this))
    b.btnSaveUpdateUrl.setOnClickListener {
      Prefs.setUpdateJsonUrl(this, b.editUpdateUrl.text?.toString().orEmpty())
      Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }

    b.btnBack.setOnClickListener { finish() }
  }

  private fun nearestScale(v: Float): Float {
    val a = listOf(1.0f, 0.75f, 0.5f)
    var best = a.first()
    var bestD = kotlin.math.abs(v - best)
    for (x in a.drop(1)) {
      val d = kotlin.math.abs(v - x)
      if (d < bestD) {
        bestD = d
        best = x
      }
    }
    return best
  }
}
