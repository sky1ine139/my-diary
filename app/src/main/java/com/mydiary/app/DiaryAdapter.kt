package com.mydiary.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DiaryAdapter(
    private val onOpen: (Diary) -> Unit
) : RecyclerView.Adapter<DiaryAdapter.VH>() {

    private val items = ArrayList<Diary>()

    fun submit(list: List<Diary>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvPreview: TextView = view.findViewById(R.id.tvPreview)
        val tvTags: TextView = view.findViewById(R.id.tvTags)
        val tvMoodWeather: TextView = view.findViewById(R.id.tvMoodWeather)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diary, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        holder.tvDate.text = DateUtil.prettyFull(d.date)

        val mw = buildString {
            val em = DiaryDbHelper.moodEmoji(d.mood)
            val we = DiaryDbHelper.weatherEmoji(d.weather)
            if (em.isNotEmpty()) append(em)
            if (we.isNotEmpty()) { if (isNotEmpty()) append(" "); append(we) }
        }
        holder.tvMoodWeather.text = mw
        holder.tvMoodWeather.visibility = if (mw.isEmpty()) View.GONE else View.VISIBLE

        holder.tvTitle.text = d.displayTitle()
        val pre = d.preview()
        holder.tvPreview.text = pre
        holder.tvPreview.visibility = if (pre.isEmpty()) View.GONE else View.VISIBLE

        val tags = d.tagList()
        if (tags.isEmpty()) {
            holder.tvTags.visibility = View.GONE
        } else {
            holder.tvTags.visibility = View.VISIBLE
            holder.tvTags.text = tags.joinToString("  ") { "#$it" }
        }

        holder.itemView.setOnClickListener { onOpen(d) }
    }
}
