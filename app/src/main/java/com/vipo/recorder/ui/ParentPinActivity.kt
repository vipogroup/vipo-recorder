package com.vipo.recorder.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vipo.recorder.databinding.ActivityParentPinBinding
import com.vipo.recorder.util.Prefs

class ParentPinActivity : ComponentActivity() {

  private lateinit var b: ActivityParentPinBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityParentPinBinding.inflate(layoutInflater)
    setContentView(b.root)

    val hasPin = Prefs.hasParentPin(this)
    b.editPinConfirm.visibility = if (hasPin) View.GONE else View.VISIBLE

    b.btnContinue.setOnClickListener {
      val pin = b.editPin.text?.toString().orEmpty().trim()
      val pin2 = b.editPinConfirm.text?.toString().orEmpty().trim()

      if (pin.length < 4) {
        Toast.makeText(this, "PIN חייב להיות לפחות 4 ספרות", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      if (!hasPin) {
        if (pin != pin2) {
          Toast.makeText(this, "ה-PIN לא תואם", Toast.LENGTH_SHORT).show()
          return@setOnClickListener
        }
        Prefs.setParentPin(this, pin)
        openApps()
        return@setOnClickListener
      }

      if (!Prefs.verifyParentPin(this, pin)) {
        Toast.makeText(this, "PIN שגוי", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      openApps()
    }

    b.btnBack.setOnClickListener { finish() }
  }

  private fun openApps() {
    startActivity(Intent(this, ParentAppsActivity::class.java))
    finish()
  }
}
