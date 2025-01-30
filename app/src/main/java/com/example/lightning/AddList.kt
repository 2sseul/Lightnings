package com.example.lightning

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class AddList : ComponentActivity() {

    private lateinit var database: DatabaseReference
    private var cal = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addlist)

        // Firebase 데이터베이스 초기화
        database = FirebaseDatabase.getInstance().reference

        // UI 요소 가져오기
        val cancleBtn: TextView = findViewById(R.id.cancle)
        val saveBtn: Button = findViewById(R.id.saveBtn)
        val timePicker: TimePicker = findViewById(R.id.timePicker)
        val lightningSwitch: Switch = findViewById(R.id.lightning_switch_btn)
        val remindSwitch: Switch = findViewById(R.id.remind_switch_btn)
        val detailsSwitch: Switch = findViewById(R.id.details_switch_btn)
        val detailsEditText: EditText = findViewById(R.id.details_editText)

        // TimePicker 초기화
        timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = cal.get(Calendar.MINUTE)

        // 뒤로 가기 버튼
        cancleBtn.setOnClickListener {
            val intent = Intent(this@AddList, MainActivity::class.java)
            startActivity(intent)
        }

        // EditText 토글 기능 (Switch 변화 감지)
        detailsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                detailsEditText.visibility = View.VISIBLE
            } else {
                detailsEditText.visibility = View.GONE
            }
        }

        // 저장 버튼 클릭 이벤트
        saveBtn.setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            val lightningEnabled = lightningSwitch.isChecked
            val remindEnabled = remindSwitch.isChecked
            val detailsEnabled = detailsSwitch.isChecked
            val detailsText = detailsEditText.text.toString()

            // AM/PM 구분 추가
            val amPm = if (hour >= 12) "PM" else "AM"
            val formattedHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour

            saveDataToFirebase(formattedHour, minute, amPm, lightningEnabled, remindEnabled, detailsEnabled, detailsText)

            // MainActivity로 이동
            val intent = Intent(this@AddList, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveDataToFirebase(
        hour: Int,
        minute: Int,
        amPm: String,
        lightningEnabled: Boolean,
        remindEnabled: Boolean,
        detailsEnabled: Boolean,
        detailsText: String
    ) {
        val userId = "test_user" // 사용자의 고유 ID (실제 앱에서는 인증 기능 필요)

        // Firebase에 저장할 데이터 구조
        val alarmData = mapOf(
            "hour" to hour,
            "minute" to minute,
            "amPm" to amPm,
            "lightningEnabled" to lightningEnabled,
            "remindEnabled" to remindEnabled,
            "detailsEnabled" to detailsEnabled,
            "detailsText" to detailsText,
            "isBookmarked" to false, // ✅ 북마크 기본값 설정
            "isActive" to true // ✅ 활성화 기본값 설정
        )

        // Firebase의 "alarms/{userId}" 경로에 데이터 저장
        database.child("alarms").child(userId).push().setValue(alarmData)
            .addOnSuccessListener {
                Toast.makeText(this, "알림이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
