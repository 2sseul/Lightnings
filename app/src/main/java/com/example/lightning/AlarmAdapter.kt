package com.example.lightning

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(private val alarmList: MutableList<AlarmData>) :
    RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    interface OnItemClickListener {
        fun onLightningClick(alarm: AlarmData, position: Int)
        fun onBookmarkClick(alarm: AlarmData, position: Int)
    }

    private var listener: OnItemClickListener? = null
    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.time)
        val minText: TextView = itemView.findViewById(R.id.min)
        val titleText: TextView = itemView.findViewById(R.id.reminder_title)
        val remindText: TextView = itemView.findViewById(R.id.remind_text)
        val lightningIcon: ImageView = itemView.findViewById(R.id.lightning_icon)
        val bookmarkIcon: ImageView = itemView.findViewById(R.id.bookmark_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.alarm_listbox, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarmList[position]
        val displayHour = when {
            alarm.amPm == "PM" && alarm.hour != 12 -> alarm.hour + 12
            alarm.amPm == "AM" && alarm.hour == 12 -> 0
            else -> alarm.hour
        }
        holder.timeText.text = "${displayHour}시"
        holder.minText.text = "${alarm.minute}분"
        holder.titleText.text = alarm.detailsText
        holder.remindText.visibility = if (alarm.remindEnabled) View.VISIBLE else View.GONE
        holder.lightningIcon.setImageResource(
            if (alarm.isActive) R.drawable.ok_thunder else R.drawable.no_thunder
        )
        holder.bookmarkIcon.setImageResource(
            if (alarm.isBookmarked) R.drawable.list_bookmark else R.drawable.list_no_bookmark
        )
        // isDeleted가 true이면 투명도를 낮춰 "삭제됨" 상태로 표현
        holder.itemView.alpha = if (alarm.isDeleted) 0.5f else 1f

        // 아이콘 클릭 이벤트
        holder.lightningIcon.setOnClickListener {
            listener?.onLightningClick(alarm, position)
        }
        holder.bookmarkIcon.setOnClickListener {
            listener?.onBookmarkClick(alarm, position)
        }
    }

    override fun getItemCount(): Int = alarmList.size
}
