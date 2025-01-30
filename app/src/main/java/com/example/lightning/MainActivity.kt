package com.example.lightning

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.database.*

class MainActivity : ComponentActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var currentAlarmContainer: LinearLayout
    private lateinit var allAlarmContainer: LinearLayout
    private lateinit var bookmarkBtn: ImageView
    private lateinit var btnAdd: ImageView // 추가 버튼
    private lateinit var switchAllStop: Switch
    private var isAllStopped = false // 일괄 정지 상태

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase 데이터베이스 초기화
        database = FirebaseDatabase.getInstance().reference.child("alarms").child("test_user")

        // XML에서 리스트 추가할 컨테이너 가져오기
        currentAlarmContainer = findViewById(R.id.currentAlarmContainer)
        allAlarmContainer = findViewById(R.id.allAlarmContainer)

        // 북마크 페이지 이동 버튼
        bookmarkBtn = findViewById(R.id.bookmark)
        bookmarkBtn.setOnClickListener {
            val intent = Intent(this, BookmarkActivity::class.java)
            startActivity(intent)
        }

        // 알람 추가 버튼 (btnAdd) 클릭 시 `AddList`로 이동하도록 설정
        btnAdd = findViewById(R.id.btnAdd)
        btnAdd.setOnClickListener {
            val intent = Intent(this, AddList::class.java)
            startActivity(intent)
        }

        // ✅ 현재알림 일괄 정지 토글 버튼 설정
        switchAllStop = findViewById(R.id.switch_all_stop)
        switchAllStop.setOnCheckedChangeListener { _, isChecked ->
            isAllStopped = isChecked
            updateCurrentAlarmsState(isChecked) // 현재알림만 정지
        }


        loadAlarmsFromFirebase()
    }

    private fun loadAlarmsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentAlarmContainer.removeAllViews()
                allAlarmContainer.removeAllViews()

                val currentTimeMillis = System.currentTimeMillis()

                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        val alarmTimeMillis = getAlarmTimeMillis(alarm.hour, alarm.minute, alarm.amPm)
                        val alarmView = createAlarmView(alarm, alarmSnapshot.key!!)

                        if (alarmTimeMillis > currentTimeMillis && alarm.remindEnabled) {
                            currentAlarmContainer.addView(alarmView)
                        } else {
                            allAlarmContainer.addView(alarmView)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun createAlarmView(alarm: AlarmData, alarmId: String): View {
        val inflater = LayoutInflater.from(this)
        val alarmView = inflater.inflate(R.layout.alarm_listbox, null)

        val timeText = alarmView.findViewById<TextView>(R.id.time)
        val minText = alarmView.findViewById<TextView>(R.id.min)
        val titleText = alarmView.findViewById<TextView>(R.id.reminder_title)
        val remindText = alarmView.findViewById<TextView>(R.id.remind_text) // 리마인드 표시 TextView
        val lightningIcon = alarmView.findViewById<ImageView>(R.id.lightning_icon) // 번개 아이콘
        val bookmarkIcon = alarmView.findViewById<ImageView>(R.id.bookmark_icon)

        val displayHour = if (alarm.amPm == "PM" && alarm.hour != 12) {
            alarm.hour + 12
        } else if (alarm.amPm == "AM" && alarm.hour == 12) {
            0 // 12 AM → 0시 변환
        } else {
            alarm.hour
        }

        timeText.text = "${displayHour}시"
        minText.text = "${alarm.minute}분"
        titleText.text = alarm.detailsText

        // ✅ 리마인드 토글이 `true`일 때만 표시
        remindText.visibility = if (alarm.remindEnabled) View.VISIBLE else View.GONE

        // ✅ 번개 아이콘 상태 반영 (울린 알람은 OFF 상태)
        lightningIcon.setImageResource(if (alarm.isActive) R.drawable.ok_thunder else R.drawable.no_thunder)

        // ✅ 북마크 상태 반영
        updateBookmarkIcon(bookmarkIcon, alarm.isBookmarked)

        bookmarkIcon.setOnClickListener {
            val newBookmarkStatus = !alarm.isBookmarked // ✅ 현재 값 반전
            database.child(alarmId).child("isBookmarked").setValue(newBookmarkStatus) // ✅ Firebase 업데이트
                .addOnSuccessListener {
                    // ✅ Firebase 업데이트 성공 시 UI 갱신
                    loadAlarmsFromFirebase()
                }
        }

        // ✅ `marginBottom` 적용 (20dp)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 0, 0, 20) // Bottom margin = 20dp
        alarmView.layoutParams = layoutParams

        bookmarkIcon.setOnClickListener {
            val newBookmarkStatus = !alarm.isBookmarked // ✅ 현재 값 반전
            database.child(alarmId).child("isBookmarked").setValue(newBookmarkStatus) // ✅ Firebase 업데이트

            // ✅ 데이터 변경 즉시 UI에 반영
            loadAlarmsFromFirebase()
        }

        return alarmView
    }

    private fun updateBookmarkIcon(bookmarkIcon: ImageView, isBookmarked: Boolean) {
        bookmarkIcon.setImageResource(if (isBookmarked) R.drawable.list_bookmark else R.drawable.list_no_bookmark)
    }

    private fun getAlarmTimeMillis(hour: Int, minute: Int, amPm: String): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        if (amPm == "PM" && hour < 12) {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour + 12)
        } else if (amPm == "AM" && hour == 12) {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        } else {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
        }

        calendar.set(java.util.Calendar.MINUTE, minute)
        return calendar.timeInMillis
    }

    private fun updateCurrentAlarmsState(isStopped: Boolean) {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        val alarmTimeMillis = getAlarmTimeMillis(alarm.hour, alarm.minute, alarm.amPm)

                        // 현재시간 이후 + 리마인드 활성화된 알람만 정지 가능
                        if (alarmTimeMillis > System.currentTimeMillis() && alarm.remindEnabled) {
                            alarmSnapshot.ref.child("isActive").setValue(!isStopped) // 현재알림만 활성화/비활성화
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

}
