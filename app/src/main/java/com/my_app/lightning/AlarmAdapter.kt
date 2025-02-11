package com.my_app.lightning

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase

class AlarmAdapter(private val context : Context, private val alarmList: MutableList<Pair<String, AlarmData>>, private val isGrayColor: Boolean = false) :

    RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    private var uniqueUserId: String = UniqueIDManager.getInstance(context).getUniqueUserId()

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

        init {
            lightningIcon.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val alarm = alarmList[position].second
                    if (!isGrayColor) { // 지난알림에서는 비활성화
                        listener?.onLightningClick(alarm, position)
                    }
                }
            }
            bookmarkIcon.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val alarm = alarmList[position].second
                    listener?.onBookmarkClick(alarm, position)
                }
            }
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val alarm = alarmList[position].second
                    listener?.onItemClick(alarm, position)
                }
            }

        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.alarm_listbox, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val (alarmId, alarm) = alarmList[position]
        // 시간 표시 (24시간 형식 변환)
        val displayHour = when {
            alarm.amPm == "PM" && alarm.hour != 12 -> alarm.hour + 12
            alarm.amPm == "AM" && alarm.hour == 12 -> 0
            else -> alarm.hour
        }
        holder.timeText.text = "${displayHour}시"
        holder.minText.text = "${alarm.minute}분"
        holder.titleText.text = alarm.detailsText
        holder.remindText.visibility = if (alarm.remindEnabled) View.VISIBLE else View.GONE

        if (isGrayColor) {
            holder.titleText.setTextColor(Color.GRAY)
        }

        // 라이트닝 아이콘: lightningEnabled 값에 따라 아이콘 변경
        holder.lightningIcon.setImageResource(
            if (alarm.lightningEnabled) R.drawable.ok_thunder else R.drawable.no_thunder
        )

        // 북마크 아이콘: isBookmarked 값에 따라 아이콘 변경
        holder.bookmarkIcon.setImageResource(
            if (alarm.isBookmarked) R.drawable.list_bookmark else R.drawable.list_no_bookmark
        )

//        // 클릭 이벤트 처리
//        holder.lightningIcon.setOnClickListener {
//            listener?.onLightningClick(alarm, position)
//        }
        holder.bookmarkIcon.setOnClickListener {
            listener?.onBookmarkClick(alarm, position)
        }
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
        uniqueUserId = UniqueIDManager.getInstance(context).getUniqueUserId()
        if (position in 0 until alarmList.size) {
            val (alarmId, _) = alarmList[position]
            FirebaseDatabase.getInstance().reference
                .child("alarms")
                .child(uniqueUserId)
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
