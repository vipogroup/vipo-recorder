package com.vipo.kidlauncher.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vipo.kidlauncher.R
import com.vipo.kidlauncher.kiosk.LaunchableApps

class AppSelectAdapter(
    private val pm: PackageManager
) : RecyclerView.Adapter<AppSelectAdapter.VH>() {

    private var items: List<LaunchableApps.Entry> = emptyList()
    private val checked = mutableSetOf<String>()

    fun submit(entries: List<LaunchableApps.Entry>, selected: Set<String>) {
        items = entries
        checked.clear()
        checked.addAll(selected)
        notifyDataSetChanged()
    }

    fun selectedPackages(): Set<String> = checked.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_select, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        holder.txtLabel.text = entry.label

        val icon = runCatching {
            pm.getApplicationIcon(entry.packageName)
        }.getOrNull()
        if (icon != null) {
            holder.imgIcon.setImageDrawable(icon)
        } else {
            holder.imgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        holder.chkSelected.setOnCheckedChangeListener(null)
        holder.chkSelected.isChecked = checked.contains(entry.packageName)
        holder.chkSelected.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checked.add(entry.packageName) else checked.remove(entry.packageName)
        }

        holder.itemView.setOnClickListener {
            holder.chkSelected.toggle()
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgIcon: ImageView = v.findViewById(R.id.imgIcon)
        val txtLabel: TextView = v.findViewById(R.id.txtLabel)
        val chkSelected: CheckBox = v.findViewById(R.id.chkSelected)
    }
}
