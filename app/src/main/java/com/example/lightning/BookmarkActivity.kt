package com.example.lightning

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.database.*

class BookmarkActivity : ComponentActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var bookmarkContainer: LinearLayout
    private lateinit var btnAdd: ImageView // 추가 버튼

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmark)

        database = FirebaseDatabase.getInstance().reference.child("alarms").child("test_user")
        bookmarkContainer = findViewById(R.id.bookmarkContainer)

        // 알람 추가 버튼 (btnAdd) 클릭 시 `AddList`로 이동하도록 설정
        btnAdd = findViewById(R.id.btnAdd)
        btnAdd.setOnClickListener {
            val intent = Intent(this, AddList::class.java)
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }

        loadBookmarkedAlarms()
    }

    private fun loadBookmarkedAlarms() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                bookmarkContainer.removeAllViews() // 기존 데이터 초기화

                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null && alarm.isBookmarked) { // isBookmarked=true 인 경우만 추가
                        val alarmView = createAlarmView(alarm, alarmSnapshot.key!!)
                        bookmarkContainer.addView(alarmView)
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
        val bookmarkIcon = alarmView.findViewById<ImageView>(R.id.bookmark_icon)

        // ✅ 오후(PM) 시간 변환: 12 더하기
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

        // 북마크 상태 반영
        bookmarkIcon.setImageResource(R.drawable.list_bookmark)

        bookmarkIcon.setOnClickListener {
            database.child(alarmId).child("isBookmarked").setValue(false) // 북마크 해제
                .addOnSuccessListener {
                    // UI 즉시 갱신 (북마크 목록에서 제거)
                    loadBookmarkedAlarms()
                }
        }

        // `marginBottom` 적용 (20dp)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 0, 0, 20) // Bottom margin = 20dp
        alarmView.layoutParams = layoutParams

        return alarmView
    }


}
