package com.vipo.recorder.util

import android.content.Context
import java.security.MessageDigest

object Prefs {
  private const val PREFS_NAME = "vipo_recorder"
  private const val KEY_RECORD_SCALE = "record_scale"
  private const val KEY_UPDATE_JSON_URL = "update_json_url"

  private const val KEY_PARENT_PIN_HASH = "parent_pin_hash"
  private const val KEY_ALLOWED_PACKAGES = "parent_allowed_packages"
  private const val KEY_CHILD_MODE = "parent_child_mode"
  private const val KEY_RECORDABLE_PACKAGES = "recordable_packages"
  private const val KEY_QUALITY = "record_quality" // 0=low, 1=medium, 2=high

  fun getRecordScale(ctx: Context): Float {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return p.getFloat(KEY_RECORD_SCALE, 1.0f)
      .coerceIn(0.25f, 1.0f)
  }

  fun setRecordScale(ctx: Context, scale: Float) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putFloat(KEY_RECORD_SCALE, scale.coerceIn(0.25f, 1.0f)).apply()
  }

  fun getUpdateJsonUrl(ctx: Context): String {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return p.getString(KEY_UPDATE_JSON_URL, "")?.trim().orEmpty()
  }

  fun setUpdateJsonUrl(ctx: Context, url: String) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putString(KEY_UPDATE_JSON_URL, url.trim()).apply()
  }

  fun hasParentPin(ctx: Context): Boolean {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return !p.getString(KEY_PARENT_PIN_HASH, null).isNullOrBlank()
  }

  fun setParentPin(ctx: Context, pin: String) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putString(KEY_PARENT_PIN_HASH, sha256Hex(pin.trim())).apply()
  }

  fun verifyParentPin(ctx: Context, pin: String): Boolean {
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

  const val QUALITY_LOW = 0
  const val QUALITY_MEDIUM = 1
  const val QUALITY_HIGH = 2

  fun getQuality(ctx: Context): Int {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return p.getInt(KEY_QUALITY, QUALITY_MEDIUM)
  }

  fun setQuality(ctx: Context, quality: Int) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    p.edit().putInt(KEY_QUALITY, quality.coerceIn(QUALITY_LOW, QUALITY_HIGH)).apply()
  }

  fun getRecordablePackages(ctx: Context): Set<String> {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = p.getString(KEY_RECORDABLE_PACKAGES, "")?.trim().orEmpty()
    if (raw.isBlank()) return emptySet()
    return raw.split("|")
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .toSet()
  }

  fun setRecordablePackages(ctx: Context, packages: Set<String>) {
    val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = packages
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .sorted()
      .joinToString("|")
    p.edit().putString(KEY_RECORDABLE_PACKAGES, raw).apply()
  }

  fun isPackageRecordable(ctx: Context, pkg: String?): Boolean {
    if (pkg.isNullOrBlank()) return true
    val recordable = getRecordablePackages(ctx)
    if (recordable.isEmpty()) return true // empty = record all
    return pkg in recordable
  }

  private fun sha256Hex(s: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) sb.append(String.format("%02x", b))
    return sb.toString()
  }
}
