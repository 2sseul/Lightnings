package com.example.lightning

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var alarmId: String

    // XML 뷰 변수들
    private lateinit var cancelTextView: TextView   // id: cancle
    private lateinit var saveButton: Button         // id: saveBtn
    private lateinit var detailsEditText: EditText    // id: details_editText
    private lateinit var detailsSwitch: Switch        // id: details_switch_btn (내용 스위치)
    private lateinit var remindSwitch: Switch         // id: remind_switch_btn
    private lateinit var timePicker: TimePicker      // id: timePicker (알람 시간 선택)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)

        // 인텐트를 통해 alarmId 전달받기
        alarmId = intent.getStringExtra("alarmId") ?: ""
        if (alarmId.isEmpty()) {
            Toast.makeText(this, "Alarm ID not provided", Toast.LENGTH_SHORT).show()
            finish()
        }

        // XML의 id와 매핑
        cancelTextView = findViewById(R.id.cancle)
        saveButton = findViewById(R.id.saveBtn)
        detailsEditText = findViewById(R.id.details_editText)
        detailsSwitch = findViewById(R.id.details_switch_btn)
        remindSwitch = findViewById(R.id.remind_switch_btn)
        timePicker = findViewById(R.id.timePicker)

        // TimePicker를 24시간 형식이 아닌 AM/PM 형식으로 설정
        timePicker.setIs24HourView(false)

        // 취소 버튼 클릭 시 Activity 종료
        cancelTextView.setOnClickListener {
            finish()
        }

        // Firebase에서 알람 데이터를 불러와 뷰에 채워줌
        loadAlarmData()

        // 저장 버튼 클릭 시 수정된 데이터를 업데이트
        saveButton.setOnClickListener {
            updateAlarm()
        }
    }

    private fun loadAlarmData() {
        val alarmRef = FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child("test_user")
            .child(alarmId)

        alarmRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alarm = snapshot.getValue(AlarmData::class.java)
                if (alarm != null) {
                    // EditText에 기존 내용을 채움
                    detailsEditText.setText(alarm.detailsText)
                    remindSwitch.isChecked = alarm.remindEnabled

                    // 내용이 존재하면 내용 스위치를 true로, 그리고 EditText 보이게
                    if (alarm.detailsText.isNotEmpty()) {
                        detailsSwitch.isChecked = true
                        detailsEditText.visibility = View.VISIBLE
                    } else {
                        detailsSwitch.isChecked = false
                        detailsEditText.visibility = View.GONE
                    }

                    // 시간 설정 (오전/오후, 시, 분 반영)
                    val hour = if (alarm.amPm == "PM" && alarm.hour < 12) {
                        alarm.hour + 12
                    } else if (alarm.amPm == "AM" && alarm.hour == 12) {
                        0
                    } else {
                        alarm.hour
                    }
                    timePicker.hour = hour
                    timePicker.minute = alarm.minute
                } else {
                    Toast.makeText(this@AlarmEditActivity, "알람 데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AlarmEditActivity, "데이터 로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateAlarm() {
        val newDetails = detailsEditText.text.toString()
        val newDetailsEnabled = detailsSwitch.isChecked  // 내용 스위치 상태
        val newRemindEnabled = remindSwitch.isChecked

        // TimePicker에서 시간 가져오기
        var newHour = timePicker.hour
        val newMinute = timePicker.minute
        val newAmPm: String

        // 24시간 형식을 AM/PM으로 변환
        when {
            newHour == 0 -> {
                newHour = 12
                newAmPm = "AM"
            }
            newHour == 12 -> {
                newAmPm = "PM"
            }
            newHour > 12 -> {
                newHour -= 12
                newAmPm = "PM"
            }
            else -> {
                newAmPm = "AM"
            }
        }

        val updateMap = mapOf(
            "detailsText" to newDetails,
            "detailsEnabled" to newDetailsEnabled,
            "remindEnabled" to newRemindEnabled,
            "hour" to newHour,
            "minute" to newMinute,
            "amPm" to newAmPm
        )

        FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child("test_user")
            .child(alarmId)
            .updateChildren(updateMap)
            .addOnSuccessListener {
                Toast.makeText(this, "알람이 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "알람 업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }
}
