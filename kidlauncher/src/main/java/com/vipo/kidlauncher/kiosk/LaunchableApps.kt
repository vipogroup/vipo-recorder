package com.vipo.kidlauncher.kiosk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object LaunchableApps {

  data class Entry(val label: String, val packageName: String)

  fun queryLaunchableEntries(ctx: Context): List<Entry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
      addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val res = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    val entries = res.mapNotNull { ri ->
      val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
      val pkg = ai.packageName ?: return@mapNotNull null
      val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrNull() ?: pkg
      Entry(label = label, packageName = pkg)
    }

    return entries
      .distinctBy { it.packageName }
      .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
  }

  fun queryLaunchablePackages(ctx: Context): Set<String> {
    return queryLaunchableEntries(ctx).map { it.packageName }.toSet()
  }
}
