package com.vipo.recorder.util

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vipo.recorder.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateManager {

  private data class UpdateInfo(
    val versionCode: Int,
    val versionName: String?,
    val apkUrl: String,
    val sha256: String?
  )

  private data class DownloadState(
    val info: UpdateInfo,
    val apkFile: File,
    val downloadId: Long
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var receiver: BroadcastReceiver? = null
  private var activeDownload: DownloadState? = null
  private var pendingInstallFile: File? = null

  fun checkForUpdate(activity: Activity) {
    val url = Prefs.getUpdateJsonUrl(activity).ifBlank { UpdateConfig.UPDATE_JSON_URL }
    if (url.isBlank()) return

    scope.launch(Dispatchers.IO) {
      val info = runCatching { fetchUpdateInfo(url) }.getOrNull() ?: return@launch
      if (info.versionCode <= BuildConfig.VERSION_CODE) return@launch

      launch(Dispatchers.Main) {
        showUpdateDialog(activity, info)
      }
    }
  }

  fun tryInstallPending(activity: Activity) {
    val f = pendingInstallFile ?: return
    if (!f.exists()) {
      pendingInstallFile = null
      return
    }
    if (canInstallPackages(activity)) {
      pendingInstallFile = null
      installApk(activity, f)
    }
  }

  private fun showUpdateDialog(activity: Activity, info: UpdateInfo) {
    val cur = BuildConfig.VERSION_NAME
    val target = info.versionName ?: info.versionCode.toString()

    AlertDialog.Builder(activity)
      .setTitle("עדכון זמין")
      .setMessage("גרסה חדשה זמינה ($target). הגרסה הנוכחית: $cur")
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton("עדכן") { _, _ ->
        startDownload(activity, info)
      }
      .show()
  }

  private fun startDownload(activity: Activity, info: UpdateInfo) {
    val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val fileName = "VIPORecorder-${info.versionCode}.apk"
    val outDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    if (outDir == null) {
      Toast.makeText(activity, "אין גישה לתיקיית הורדות", Toast.LENGTH_LONG).show()
      return
    }

    val apkFile = File(outDir, fileName)
    runCatching {
      if (apkFile.exists()) apkFile.delete()
    }

    val req = DownloadManager.Request(Uri.parse(info.apkUrl))
      .setTitle("VIPORecorder")
      .setDescription("מוריד עדכון")
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      .setAllowedOverMetered(true)
      .setAllowedOverRoaming(true)
      .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)

    val downloadId = dm.enqueue(req)
    activeDownload = DownloadState(info = info, apkFile = apkFile, downloadId = downloadId)

    registerDownloadReceiver(activity)
    Toast.makeText(activity, "מוריד עדכון…", Toast.LENGTH_SHORT).show()
  }

  private fun registerDownloadReceiver(activity: Activity) {
    if (receiver != null) return

    val r = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val st = activeDownload ?: return
        if (id != st.downloadId) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(id)
        val ok = dm.query(q).use { c ->
          if (c == null || !c.moveToFirst()) return@use false
          val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
          status == DownloadManager.STATUS_SUCCESSFUL
        }

        runCatching { context.unregisterReceiver(this) }
        receiver = null
        activeDownload = null

        if (!ok) {
          Toast.makeText(context, "הורדה נכשלה", Toast.LENGTH_LONG).show()
          return
        }

        if (!st.apkFile.exists()) {
          Toast.makeText(context, "הקובץ לא נמצא אחרי הורדה", Toast.LENGTH_LONG).show()
          return
        }

        scope.launch(Dispatchers.IO) {
          val verified = verifyChecksumIfNeeded(st.info, st.apkFile)
          if (!verified) {
            launch(Dispatchers.Main) {
              Toast.makeText(context, "בדיקת קובץ נכשלה", Toast.LENGTH_LONG).show()
            }
            return@launch
          }

          launch(Dispatchers.Main) {
            promptInstall(activity, st.apkFile)
          }
        }
      }
    }

    receiver = r
    val f = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
    if (Build.VERSION.SDK_INT >= 33) {
      activity.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
    } else {
      activity.registerReceiver(r, f)
    }
  }

  private fun promptInstall(activity: Activity, apkFile: File) {
    if (!canInstallPackages(activity)) {
      pendingInstallFile = apkFile
      val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:${activity.packageName}")
      }
      runCatching { activity.startActivity(intent) }
      Toast.makeText(activity, "צריך לאפשר התקנה ממקור לא ידוע", Toast.LENGTH_LONG).show()
      return
    }

    installApk(activity, apkFile)
  }

  private fun canInstallPackages(activity: Activity): Boolean {
    return if (Build.VERSION.SDK_INT >= 26) {
      activity.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  private fun installApk(activity: Activity, apkFile: File) {
    val uri = FileProvider.getUriForFile(
      activity,
      "${activity.packageName}.fileprovider",
      apkFile
    )

    val i = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
      activity.startActivity(i)
    }.onFailure {
      Toast.makeText(activity, "לא ניתן לפתוח התקנה", Toast.LENGTH_LONG).show()
    }
  }

  private fun fetchUpdateInfo(url: String): UpdateInfo {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 12_000
      readTimeout = 12_000
      requestMethod = "GET"
      doInput = true
    }

    try {
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
      if (code !in 200..299) {
        throw IllegalStateException("HTTP $code")
      }

      val o = JSONObject(body)
      val versionCode = o.getInt("versionCode")
      val versionName = o.optString("versionName").trim().takeIf { it.isNotBlank() }
      val apkUrl = o.getString("apkUrl").trim()
      val sha256 = o.optString("sha256").trim().takeIf { it.isNotBlank() }
      return UpdateInfo(
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = apkUrl,
        sha256 = sha256
      )
    } finally {
      runCatching { conn.disconnect() }
    }
  }

  private fun verifyChecksumIfNeeded(info: UpdateInfo, apkFile: File): Boolean {
    val expected = info.sha256?.trim()?.lowercase() ?: return true
    val actual = sha256Hex(apkFile)
    return actual == expected
  }

  private fun sha256Hex(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { ins ->
      val buf = ByteArray(64 * 1024)
      while (true) {
        val r = ins.read(buf)
        if (r <= 0) break
        md.update(buf, 0, r)
      }
    }
    val dig = md.digest()
    val sb = StringBuilder(dig.size * 2)
    for (b in dig) {
      sb.append(String.format("%02x", b))
    }
    return sb.toString()
  }
}
