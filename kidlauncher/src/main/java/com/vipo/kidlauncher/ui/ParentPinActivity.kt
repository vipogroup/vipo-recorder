package com.vipo.kidlauncher.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vipo.kidlauncher.databinding.ActivityParentPinBinding
import com.vipo.kidlauncher.kiosk.KioskPolicy
import com.vipo.kidlauncher.util.Prefs

class ParentPinActivity : ComponentActivity() {

  private lateinit var b: ActivityParentPinBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityParentPinBinding.inflate(layoutInflater)
    setContentView(b.root)

    Prefs.ensureDefaultPin(this)

    val action = intent?.getStringExtra(EXTRA_ACTION).orEmpty()

    b.btnContinue.setOnClickListener {
      val pin = b.editPin.text?.toString().orEmpty().trim()

      if (pin.length < 4) {
        Toast.makeText(this, "PIN חייב להיות לפחות 4 ספרות", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      if (!Prefs.verifyParentPin(this, pin)) {
        Toast.makeText(this, "PIN שגוי", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      when (action) {
        ACTION_SETTINGS -> {
          startActivity(android.content.Intent(this, ParentSettingsActivity::class.java))
          finish()
        }

        ACTION_EXIT -> {
          if (KioskPolicy.isDeviceOwner(this)) {
            KioskPolicy.deactivateToSystemLauncher(this)
          } else {
            Toast.makeText(this, "אין Device Owner. חזרה למסך הבית (ייתכן שתידרש בחירה ידנית)", Toast.LENGTH_LONG).show()
            val goHome = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
              addCategory(android.content.Intent.CATEGORY_HOME)
              addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(goHome)
          }
          finish()
        }

        else -> {
          finish()
        }
      }
    }

    b.btnBack.setOnClickListener { finish() }
  }

  companion object {
    const val EXTRA_ACTION = "action"

    const val ACTION_SETTINGS = "settings"
    const val ACTION_EXIT = "exit"
  }
}
