package com.vipo.kidlauncher.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.vipo.kidlauncher.svc.KidOverlayService
import com.vipo.kidlauncher.ui.KidHomeActivity
import com.vipo.kidlauncher.util.Prefs

object KioskPolicy {

  fun adminComponent(ctx: Context): ComponentName {
    return ComponentName(ctx, KidDeviceAdminReceiver::class.java)
  }

  private fun dpm(ctx: Context): DevicePolicyManager {
    return ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
  }

  fun isDeviceOwner(ctx: Context): Boolean {
    return dpm(ctx).isDeviceOwnerApp(ctx.packageName)
  }

  fun autoGrantPermissions(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    if (Build.VERSION.SDK_INT >= 23) {
      // Auto-grant READ_PHONE_STATE for call detection (speakerphone enforcement)
      runCatching {
        dpm.setPermissionGrantState(admin, ctx.packageName,
          android.Manifest.permission.READ_PHONE_STATE,
          DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
      }
      // Auto-grant RECORD_AUDIO for recorder app
      runCatching {
        dpm.setPermissionGrantState(admin, "com.vipo.recorder",
          android.Manifest.permission.RECORD_AUDIO,
          DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
      }
    }
  }

  fun applyAllowedApps(ctx: Context, allowedPackages: Set<String>) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)

    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    autoGrantPermissions(ctx)
    setHomeAsDefault(ctx)

    val normalHome = findNormalHomeComponent(ctx)
    if (normalHome != null) {
      Prefs.setNormalHomeComponent(ctx, normalHome.flattenToString())
    }

    val keepUnsuspended = mutableSetOf(ctx.packageName)
    if (normalHome != null) {
      keepUnsuspended.add(normalHome.packageName)
    }

    val lockPkgs = (allowedPackages + keepUnsuspended).toTypedArray()
    dpm.setLockTaskPackages(admin, lockPkgs)

    enableChildModeRestrictions(ctx)

    val launchable = LaunchableApps.queryLaunchablePackages(ctx)
    val disallowed = (launchable - allowedPackages - keepUnsuspended).toTypedArray()

    if (Build.VERSION.SDK_INT >= 24) {
      runCatching { dpm.setPackagesSuspended(admin, disallowed, true) }
    }

    if (Build.VERSION.SDK_INT >= 24) {
      val allow = (allowedPackages - keepUnsuspended).toTypedArray()
      runCatching { dpm.setPackagesSuspended(admin, allow, false) }
    }
  }

  fun deactivateToSystemLauncher(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)

    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    Prefs.setChildModeEnabled(ctx, false)

    KidOverlayService.stop(ctx)
    clearAllSuspension(ctx)
    stopKiosk(ctx)

    setSystemLauncherAsHome(ctx)

    val goHome = Intent(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    runCatching { ctx.startActivity(goHome) }
  }

  fun clearAllSuspension(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    if (Build.VERSION.SDK_INT >= 24) {
      val allPkgs = ctx.packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
        .mapNotNull { it.packageName }
        .toTypedArray()
      runCatching { dpm.setPackagesSuspended(admin, allPkgs, false) }
    }

    dpm.setLockTaskPackages(admin, arrayOf(ctx.packageName))
    disableChildModeRestrictions(ctx)
  }

  private fun setHomeAsDefault(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    val normalHome = findNormalHomeComponent(ctx)
    if (normalHome != null) {
      runCatching { dpm.clearPackagePersistentPreferredActivities(admin, normalHome.packageName) }
    }

    val filter = IntentFilter(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addCategory(Intent.CATEGORY_DEFAULT)
    }

    val home = ComponentName(ctx, KidHomeActivity::class.java)
    runCatching {
      dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
      dpm.addPersistentPreferredActivity(admin, filter, home)
    }
  }

  private fun setSystemLauncherAsHome(ctx: Context) {
    val dpm = dpm(ctx)
    val admin = adminComponent(ctx)
    if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

    val normalHome = findNormalHomeComponent(ctx)
    if (normalHome == null) {
      runCatching { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName) }
      return
    }

    val filter = IntentFilter(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addCategory(Intent.CATEGORY_DEFAULT)
    }

    runCatching {
      dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
      dpm.clearPackagePersistentPreferredActivities(admin, normalHome.packageName)
      dpm.addPersistentPreferredActivity(admin, filter, normalHome)
    }
  }

  private fun findNormalHomeComponent(ctx: Context): ComponentName? {
    val saved = Prefs.getNormalHomeComponent(ctx)
    if (saved.isNotBlank()) {
      ComponentName.unflattenFromString(saved)?.let { return it }
    }
    return findBestSystemHomeComponent(ctx)
  }

  private fun findBestSystemHomeComponent(ctx: Context): ComponentName? {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
      addCategory(Intent.CATEGORY_DEFAULT)
    }

    val ris = pm.queryIntentActivities(intent, 0)
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

    // Force immersive mode system-wide — hides nav bar for ALL apps
    runCatching {
      dpm.setGlobalSetting(admin, "policy_control", "immersive.full=*")
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

    // Restore normal navigation bar
    runCatching {
      dpm.setGlobalSetting(admin, "policy_control", "")
    }
  }

  fun startKioskIfPermitted(activity: Activity) {
    val dpm = dpm(activity)
    val admin = adminComponent(activity)
    if (!dpm.isDeviceOwnerApp(activity.packageName)) return
    if (!dpm.isLockTaskPermitted(activity.packageName)) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      runCatching {
        dpm.setLockTaskFeatures(admin,
          DevicePolicyManager.LOCK_TASK_FEATURE_NONE
        )
      }
    }

    runCatching { activity.startLockTask() }
  }

  fun hasOverlayPermission(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    return android.provider.Settings.canDrawOverlays(ctx)
  }

  fun requestOverlayPermission(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = Intent(
      android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      android.net.Uri.parse("package:${ctx.packageName}")
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { ctx.startActivity(intent) }
  }

  fun stopKiosk(activity: Activity) {
    runCatching { activity.stopLockTask() }
  }

  private fun stopKiosk(ctx: Context) {
    if (ctx is Activity) {
      stopKiosk(ctx)
      return
    }
  }
}
