package com.example.lightning

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase

class AlarmAdapter(private val alarmList: MutableList<Pair<String, AlarmData>>) :
    RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    interface OnItemClickListener {
        fun onLightningClick(alarm: AlarmData, position: Int)
        fun onBookmarkClick(alarm: AlarmData, position: Int)
        fun onItemClick(alarm: AlarmData, position: Int)
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
        val (alarmId, alarm) = alarmList[position]
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

        // 기존 아이콘 클릭 리스너
        holder.lightningIcon.setOnClickListener {
            listener?.onLightningClick(alarm, position)
        }
        holder.bookmarkIcon.setOnClickListener {
            listener?.onBookmarkClick(alarm, position)
        }

        // 전체 아이템 클릭 시 편집 화면으로 전환 (새 리스너 메서드 호출)
        holder.itemView.setOnClickListener {
            listener?.onItemClick(alarm, position)
        }
    }


    override fun getItemCount(): Int = alarmList.size

    // Firebase에서 새 데이터를 받아 업데이트하는 메서드
    fun updateData(newList: MutableList<Pair<String, AlarmData>>) {
        alarmList.clear()
        alarmList.addAll(newList)
        notifyDataSetChanged()
    }

    // 스와이프 삭제 시 호출: Firebase의 isDeleted를 true로 업데이트 후, 로컬 리스트에서도 제거
    fun removeItem(position: Int) {
        if (position in 0 until alarmList.size) {
            val (alarmId, _) = alarmList[position]
            FirebaseDatabase.getInstance().reference
                .child("alarms")
                .child("test_user")
                .child(alarmId)
                .child("isDeleted")
                .setValue(true)
                .addOnSuccessListener {
                    alarmList.removeAt(position)
                    notifyDataSetChanged()
                }
        }
    }
}
