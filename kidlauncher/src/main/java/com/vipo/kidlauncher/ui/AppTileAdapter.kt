package com.vipo.kidlauncher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vipo.kidlauncher.databinding.ItemAppTileBinding
import kotlin.math.abs

class AppTileAdapter(
  private val ctx: Context,
  private var items: List<AppTile>,
  private val onClick: (AppTile) -> Unit,
) : RecyclerView.Adapter<AppTileAdapter.VH>() {

  data class AppTile(
    val label: String,
    val packageName: String,
    val isRecordingButton: Boolean = false,
  )

  class VH(val b: ItemAppTileBinding) : RecyclerView.ViewHolder(b.root)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
    val b = ItemAppTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return VH(b)
  }

  override fun getItemCount(): Int = items.size

  override fun onBindViewHolder(holder: VH, position: Int) {
    val item = items[position]

    holder.b.txtLabel.text = item.label
    holder.b.card.setCardBackgroundColor(tileColor(item.packageName))

    val icon = loadAppIcon(item.packageName)
    if (icon != null) {
      holder.b.imgIcon.setImageDrawable(icon)
    } else {
      holder.b.imgIcon.setImageResource(android.R.drawable.ic_menu_help)
    }

    holder.b.card.setOnClickListener { onClick(item) }
  }

  fun submit(newItems: List<AppTile>) {
    items = newItems
    notifyDataSetChanged()
  }

  private fun loadAppIcon(pkg: String) = runCatching {
    val pm: PackageManager = ctx.packageManager
    pm.getApplicationIcon(pkg)
  }.getOrNull()

  private fun tileColor(seed: String): Int {
    val h = abs(seed.hashCode())
    val r = 80 + (h % 120)
    val g = 80 + ((h / 7) % 120)
    val b = 80 + ((h / 13) % 120)
    return Color.rgb(r, g, b)
  }
}
