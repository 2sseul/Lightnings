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
import java.util.Calendar

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
            checkAndUpdateLightningStatus()

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

        // 현재 시간과 비교하여 지난 알람인지 확인
        val currentTime = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, if (alarm.amPm == "PM" && alarm.hour < 12) alarm.hour + 12 else alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
        }

        // 시간이 지난 경우 lightningEnabled를 false로 업데이트
        if (alarmTime.before(currentTime) && alarm.lightningEnabled) {
            alarm.lightningEnabled = false
            updateLightningStatusInFirebase(alarmId, false)
        }

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

        // 지난알림일 경우 본문 색 그레이로 바꿈
        if (isGrayColor) {
            holder.titleText.setTextColor(Color.GRAY)
        }

        // 라이트닝 아이콘 설정
        if (!alarm.isActive && alarm.lightningEnabled) {
            // isActive가 false면서 lightningEnabled가 true인 경우
            holder.lightningIcon.setImageResource(R.drawable.ok_thunder)
            holder.lightningIcon.isEnabled = false
        } else if (isGrayColor) {
            // 전체 알람(지난 알람)의 경우 라이트닝 아이콘 비활성화 및 no_thunder 적용
            holder.lightningIcon.setImageResource(R.drawable.no_thunder)
            if(!alarm.isActive && alarm.lightningEnabled){
                holder.lightningIcon.setImageResource(R.drawable.ok_thunder)
            }
            holder.lightningIcon.isEnabled = false
        } else {
            // 기본적으로 lightningEnabled에 따라 라이트닝 아이콘 변경
            holder.lightningIcon.setImageResource(
                if (alarm.lightningEnabled) R.drawable.ok_thunder else R.drawable.no_thunder
            )
            holder.lightningIcon.isEnabled = true
        }

        // 북마크 아이콘: isBookmarked 값에 따라 아이콘 변경
        holder.bookmarkIcon.setImageResource(
            if (alarm.isBookmarked) R.drawable.list_bookmark else R.drawable.list_no_bookmark
        )

        // 클릭 이벤트 설정
        holder.lightningIcon.setOnClickListener {
            if (holder.lightningIcon.isEnabled) { // 비활성화된 상태에서는 클릭 불가
                listener?.onLightningClick(alarm, position)
            }
        }
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

        checkAndUpdateLightningStatus()
    }

    // Firebase에서 기존 데이터를 업데이트하는 메서드
    private fun updateLightningStatusInFirebase(alarmId: String, status: Boolean) {
        FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child(uniqueUserId)
            .child(alarmId)
            .child("lightningEnabled")
            .setValue(status)
    }

    // Firebase에서 모든 알람을 가져와 지난 알람을 업데이트하는 메서드
    fun checkAndUpdateLightningStatus() {
        val databaseRef = FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child(uniqueUserId)

        databaseRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val currentTime = Calendar.getInstance()

                snapshot.children.forEach { childSnapshot ->
                    val alarmId = childSnapshot.key ?: return@forEach
                    val alarm = childSnapshot.getValue(AlarmData::class.java) ?: return@forEach

                    val alarmTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, if (alarm.amPm == "PM" && alarm.hour < 12) alarm.hour + 12 else alarm.hour)
                        set(Calendar.MINUTE, alarm.minute)
                    }

                    if (alarmTime.before(currentTime) && alarm.lightningEnabled) {
                        updateLightningStatusInFirebase(alarmId, false)
                    }
                }
            }
        }
    }
}
