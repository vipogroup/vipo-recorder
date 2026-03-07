package com.vipo.kidlauncher.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vipo.kidlauncher.databinding.ActivityChangePinBinding
import com.vipo.kidlauncher.util.Prefs

class ChangePinActivity : ComponentActivity() {

  private lateinit var b: ActivityChangePinBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityChangePinBinding.inflate(layoutInflater)
    setContentView(b.root)

    Prefs.ensureDefaultPin(this)

    b.btnSave.setOnClickListener {
      val current = b.editCurrentPin.text?.toString().orEmpty().trim()
      val pin1 = b.editNewPin.text?.toString().orEmpty().trim()
      val pin2 = b.editConfirmPin.text?.toString().orEmpty().trim()

      if (!Prefs.verifyParentPin(this, current)) {
        Toast.makeText(this, "PIN נוכחי שגוי", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      if (pin1.length < 4) {
        Toast.makeText(this, "PIN חייב להיות לפחות 4 ספרות", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      if (pin1 != pin2) {
        Toast.makeText(this, "אימות PIN לא תואם", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      Prefs.setParentPin(this, pin1)
      Toast.makeText(this, "נשמר", Toast.LENGTH_SHORT).show()
      finish()
    }

    b.btnBack.setOnClickListener { finish() }
  }
}
