package com.vipo.recorder.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import com.vipo.recorder.ui.HomeActivity

object KioskPolicy {

  fun adminComponent(ctx: Context): ComponentName {
    return ComponentName(ctx, VipoDeviceAdminReceiver::class.java)
  }

  fun dpm(ctx: Context): DevicePolicyManager {
    return ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
  }

  fun isDeviceOwner(ctx: Context): Boolean {
    return dpm(ctx).isDeviceOwnerApp(ctx.packageName)
  }

  fun applyAllowedApps(ctx: Context, allowedPackages: Set<String>) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)

    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    setHomeAsDefault(ctx)

    val lockPkgs = (allowedPackages + setOf(ctx.packageName)).toTypedArray()
    dpm.setLockTaskPackages(admin, lockPkgs)

    enableChildModeRestrictions(ctx)

    val launchable = LaunchableApps.queryLaunchablePackages(ctx)
    val disallowed = (launchable - allowedPackages - setOf(ctx.packageName)).toTypedArray()
    if (Build.VERSION.SDK_INT >= 24) {
      runCatching { dpm.setPackagesSuspended(admin, disallowed, true) }
    }

    if (Build.VERSION.SDK_INT >= 24) {
      val allow = (allowedPackages - setOf(ctx.packageName)).toTypedArray()
      runCatching { dpm.setPackagesSuspended(admin, allow, false) }
    }
  }

  fun clearAllSuspension(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    val launchable = LaunchableApps.queryLaunchablePackages(ctx)
    if (Build.VERSION.SDK_INT >= 24) {
      runCatching { dpm.setPackagesSuspended(admin, launchable.toTypedArray(), false) }
    }
    dpm.setLockTaskPackages(admin, arrayOf(ctx.packageName))

    disableChildModeRestrictions(ctx)
    setSystemLauncherAsHome(ctx)
  }

  private fun setSystemLauncherAsHome(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    val systemHome = findBestSystemHomeComponent(ctx) ?: run {
      runCatching { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName) }
      return
    }

    val filter = IntentFilter(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addCategory(Intent.CATEGORY_DEFAULT)
    }

    runCatching {
      dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
      dpm.addPersistentPreferredActivity(admin, filter, systemHome)
    }
  }

  private fun findBestSystemHomeComponent(ctx: Context): ComponentName? {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addCategory(Intent.CATEGORY_DEFAULT)
    }

    val ris = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return ris
      .mapNotNull { ri ->
        val ai = ri.activityInfo ?: return@mapNotNull null
        ComponentName(ai.packageName, ai.name)
      }
      .firstOrNull { cn ->
        cn.packageName != ctx.packageName &&
          cn.packageName != "com.android.settings"
      }
  }

  private fun setHomeAsDefault(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    val filter = IntentFilter(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addCategory(Intent.CATEGORY_DEFAULT)
    }

    val home = ComponentName(ctx, HomeActivity::class.java)
    runCatching {
      dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
      dpm.addPersistentPreferredActivity(admin, filter, home)
    }
  }

  private fun enableChildModeRestrictions(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS) }
    runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS) }
    runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }

    if (Build.VERSION.SDK_INT >= 23) {
      runCatching { dpm.setStatusBarDisabled(admin, true) }
    }
  }

  private fun disableChildModeRestrictions(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS) }
    runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS) }
    runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }

    if (Build.VERSION.SDK_INT >= 23) {
      runCatching { dpm.setStatusBarDisabled(admin, false) }
    }
  }

  fun startKioskIfPermitted(activity: Activity) {
    val dpm = dpm(activity)
    if (!dpm.isDeviceOwnerApp(activity.packageName)) return
    if (!dpm.isLockTaskPermitted(activity.packageName)) return
    runCatching { activity.startLockTask() }
  }

  fun stopKiosk(activity: Activity) {
    runCatching { activity.stopLockTask() }
  }

  fun openUninstallScreen(ctx: Context, pkg: String) {
    val i = Intent(Intent.ACTION_DELETE).apply {
      data = Uri.parse("package:$pkg")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(i) }
  }
}
