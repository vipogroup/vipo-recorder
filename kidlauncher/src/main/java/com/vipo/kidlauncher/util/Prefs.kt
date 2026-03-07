package com.vipo.kidlauncher.util

import android.content.Context
import java.security.MessageDigest

object Prefs {
  private const val PREFS_NAME = "kidlauncher"

  private const val KEY_PARENT_PIN_HASH = "parent_pin_hash"
  private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
  private const val KEY_CHILD_MODE = "child_mode"
  private const val KEY_NORMAL_HOME_COMPONENT = "normal_home_component"
  private const val KEY_KID_SCREEN_NAME = "kid_screen_name"
  private const val KEY_RECORDABLE_PACKAGES = "recordable_packages"

  private const val DEFAULT_PIN = "1234"

  fun ensureDefaultPin(ctx: Context) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existing = p.getString(KEY_PARENT_PIN_HASH, null)
    if (!existing.isNullOrBlank()) return
    p.edit().putString(KEY_PARENT_PIN_HASH, sha256Hex(DEFAULT_PIN)).apply()
  }

  fun setParentPin(ctx: Context, pin: String) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putString(KEY_PARENT_PIN_HASH, sha256Hex(pin.trim())).apply()
  }

  fun verifyParentPin(ctx: Context, pin: String): Boolean {
    ensureDefaultPin(ctx)
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val expected = p.getString(KEY_PARENT_PIN_HASH, null) ?: return false
    return expected == sha256Hex(pin.trim())
  }

  fun getAllowedPackages(ctx: Context): Set<String> {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = p.getString(KEY_ALLOWED_PACKAGES, "")?.trim().orEmpty()
    if (raw.isBlank()) return emptySet()
    return raw.split("|")
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .toSet()
  }

  fun setAllowedPackages(ctx: Context, packages: Set<String>) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = packages
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .sorted()
      .joinToString("|")
    p.edit().putString(KEY_ALLOWED_PACKAGES, raw).apply()
  }

  fun isChildModeEnabled(ctx: Context): Boolean {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return p.getBoolean(KEY_CHILD_MODE, false)
  }

  fun setChildModeEnabled(ctx: Context, enabled: Boolean) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putBoolean(KEY_CHILD_MODE, enabled).apply()
  }

  fun getNormalHomeComponent(ctx: Context): String {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return p.getString(KEY_NORMAL_HOME_COMPONENT, "")?.trim().orEmpty()
  }

  fun setNormalHomeComponent(ctx: Context, flattenedComponent: String) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putString(KEY_NORMAL_HOME_COMPONENT, flattenedComponent.trim()).apply()
  }

  fun getKidScreenName(ctx: Context): String {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return p.getString(KEY_KID_SCREEN_NAME, "")?.trim().orEmpty()
  }

  fun setKidScreenName(ctx: Context, name: String) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putString(KEY_KID_SCREEN_NAME, name.trim()).apply()
  }

  fun getRecordablePackages(ctx: Context): Set<String> {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = p.getString(KEY_RECORDABLE_PACKAGES, "")?.trim().orEmpty()
    if (raw.isBlank()) return emptySet()
    return raw.split("|").map { it.trim() }.filter { it.isNotBlank() }.toSet()
  }

  fun setRecordablePackages(ctx: Context, packages: Set<String>) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = packages.map { it.trim() }.filter { it.isNotBlank() }.sorted().joinToString("|")
    p.edit().putString(KEY_RECORDABLE_PACKAGES, raw).apply()
  }

  private fun sha256Hex(s: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) sb.append(String.format("%02x", b))
    return sb.toString()
  }
}
