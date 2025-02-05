package com.example.lightning

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import com.google.firebase.database.*
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var currentAlarmContainer: LinearLayout
    private lateinit var allAlarmContainer: LinearLayout
    private lateinit var bookmarkBtn: ImageView
    private lateinit var btnAdd: ImageView
    private lateinit var switchAllStop: Switch
    private var isAllStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = FirebaseDatabase.getInstance().reference.child("alarms").child("test_user")

        currentAlarmContainer = findViewById(R.id.currentAlarmContainer)
        allAlarmContainer = findViewById(R.id.allAlarmContainer)

        bookmarkBtn = findViewById(R.id.bookmark)
        bookmarkBtn.setOnClickListener {
            val intent = Intent(this, BookmarkActivity::class.java)
            startActivity(intent)
        }

        btnAdd = findViewById(R.id.btnAdd)
        btnAdd.setOnClickListener {
            val intent = Intent(this, AddList::class.java)
            startActivity(intent)
        }

        switchAllStop = findViewById(R.id.switch_all_stop)
        // switchAllStop 클릭 시 모든 알람 ON/OFF 토글
        switchAllStop.setOnCheckedChangeListener { _, isChecked ->
            isAllStopped = isChecked
            updateCurrentAlarmsState(isChecked)
        }


        resetAlarmsAtMidnight()
        loadAlarmsFromFirebase()
    }

    private fun resetAlarmsAtMidnight() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // ✅ 현재 시간이 00시(자정)인 경우, 이전 알람 삭제
        if (currentHour == 0) {
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (alarmSnapshot in snapshot.children) {
                        val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                        if (alarm != null) {
                            if (!alarm.isBookmarked) {
                                alarmSnapshot.ref.removeValue() // ✅ 북마크되지 않은 알람 삭제
                            }
                        }
                    }
                    loadAlarmsFromFirebase() // ✅ 삭제 후 UI 갱신
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun loadAlarmsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentAlarmContainer.removeAllViews()
                allAlarmContainer.removeAllViews()

                val noCurrentAlarmsText = findViewById<TextView>(R.id.noCurrentAlarmsText)
                val noAllAlarmsText = findViewById<TextView>(R.id.noAllAlarmsText)

                val currentTimeMillis = System.currentTimeMillis()
                var hasCurrentAlarms = false
                var hasAllAlarms = false

                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        val alarmTimeMillis = alarm.alarmTimeMillis ?: getAlarmTimeMillis(alarm.hour, alarm.minute, alarm.amPm)
                        val alarmView = createAlarmView(alarm, alarmSnapshot.key!!)

                        if (alarm.isActive && alarmTimeMillis >= currentTimeMillis) {
                            currentAlarmContainer.addView(alarmView)
                            hasCurrentAlarms = true
                        } else {
                            allAlarmContainer.addView(alarmView)
                            hasAllAlarms = true
                        }
                    }
                }

                // ✅ 현재 알림이 없을 경우 안내 문구 표시
                noCurrentAlarmsText.visibility = if (!hasCurrentAlarms) View.VISIBLE else View.GONE

                // ✅ 전체 알림이 없을 경우 안내 문구 표시
                noAllAlarmsText.visibility = if (!hasAllAlarms) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


    // ✅ 안내 문구를 동적으로 생성하는 함수
    private fun createNoAlarmsTextView(message: String): TextView {
        val textView = TextView(this)
        textView.text = message
        textView.textSize = 16f
        textView.setTextColor(android.graphics.Color.GRAY)

        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 50, 0, 50) // 위아래 50dp 여백
        textView.layoutParams = layoutParams

        return textView
    }

    private fun createAlarmView(alarm: AlarmData, alarmId: String): View {
        val inflater = LayoutInflater.from(this)
        val alarmView = inflater.inflate(R.layout.alarm_listbox, null)

        val timeText = alarmView.findViewById<TextView>(R.id.time)
        val minText = alarmView.findViewById<TextView>(R.id.min)
        val titleText = alarmView.findViewById<TextView>(R.id.reminder_title)
        val remindText = alarmView.findViewById<TextView>(R.id.remind_text)
        val lightningIcon = alarmView.findViewById<ImageView>(R.id.lightning_icon)
        val bookmarkIcon = alarmView.findViewById<ImageView>(R.id.bookmark_icon)

        val displayHour = if (alarm.amPm == "PM" && alarm.hour != 12) {
            alarm.hour + 12
        } else if (alarm.amPm == "AM" && alarm.hour == 12) {
            0
        } else {
            alarm.hour
        }

        timeText.text = "${displayHour}시"
        minText.text = "${alarm.minute}분"
        titleText.text = alarm.detailsText

        remindText.visibility = if (alarm.remindEnabled) View.VISIBLE else View.GONE

        // 번개 아이콘 상태 반영
        lightningIcon.setImageResource(if (alarm.isActive) R.drawable.ok_thunder else R.drawable.no_thunder)

        // 번개 아이콘 클릭 시 ON/OFF 토글
        lightningIcon.setOnClickListener {
            val newActiveStatus = !alarm.isActive
            database.child(alarmId).child("isActive").setValue(newActiveStatus)
                .addOnSuccessListener {
                    loadAlarmsFromFirebase() // UI 즉시 업데이트
                }
        }

        updateBookmarkIcon(bookmarkIcon, alarm.isBookmarked)

        bookmarkIcon.setOnClickListener {
            val newBookmarkStatus = !alarm.isBookmarked
            database.child(alarmId).child("isBookmarked").setValue(newBookmarkStatus)
                .addOnSuccessListener {
                    loadAlarmsFromFirebase() // UI 즉시 업데이트
                }
        }

        // marginBottom 20dp 적용
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 0, 0, 20) // Bottom margin = 20dp
        alarmView.layoutParams = layoutParams

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

    // 일괄 정지 기능
    // ✅ 모든 알람의 라이트닝 ON/OFF 전환
    private fun updateCurrentAlarmsState(isStopped: Boolean) {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        // ✅ 현재 상태 반전 (ON → OFF, OFF → ON)
                        alarmSnapshot.ref.child("isActive").setValue(!isStopped)
                    }
                }
                loadAlarmsFromFirebase() // ✅ UI 즉시 업데이트
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }



}
