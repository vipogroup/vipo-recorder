package com.vipo.recorder.util

import android.content.Context

object Prefs {
  private const val PREFS_NAME = "vipo_recorder"
  private const val KEY_RECORD_SCALE = "record_scale"
  private const val KEY_UPDATE_JSON_URL = "update_json_url"

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
}
