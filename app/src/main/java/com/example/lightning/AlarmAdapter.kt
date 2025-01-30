package com.example.lightning

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(private val alarmList: List<AlarmData>) :
    RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.time)  // 시간 (시)
        val minuteText: TextView = view.findViewById(R.id.min)  // 분
        val reminderTitle: TextView = view.findViewById(R.id.reminder_title)  // 알람 제목
        val remindText: TextView = view.findViewById(R.id.remind_text)  // 리마인드 여부
        val lightningIcon: ImageView = view.findViewById(R.id.lightning_icon)  // 번개 아이콘
        val bookmarkIcon: ImageView = view.findViewById(R.id.bookmark_icon)  // 북마크 아이콘
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.alarm_listbox, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarmList[position]

        // 시간 설정 (AM/PM 포함)
        holder.timeText.text = "${alarm.hour}시"
        holder.minuteText.text = "${alarm.minute}분"

        // 알람 제목 설정
        holder.reminderTitle.text = alarm.detailsText

        // 리마인드 여부 표시
        if (alarm.remindEnabled) {
            holder.remindText.visibility = View.VISIBLE
        } else {
            holder.remindText.visibility = View.GONE
        }

        // 번개 아이콘 표시 여부
        if (alarm.lightningEnabled) {
            holder.lightningIcon.setImageResource(R.drawable.ok_thunder)  // 활성화된 번개 아이콘
        } else {
            holder.lightningIcon.setImageResource(R.drawable.no_thunder)  // 비활성화된 번개 아이콘
        }

        // 북마크 아이콘 클릭 이벤트 예시 (추가 기능 가능)
        holder.bookmarkIcon.setOnClickListener {
            Toast.makeText(it.context, "${alarm.detailsText} 북마크 클릭됨!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = alarmList.size
}

