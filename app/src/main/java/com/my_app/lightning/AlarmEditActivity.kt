package com.my_app.lightning

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.util.Calendar

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var alarmId: String
    private lateinit var uniqueUserId: String

    // XML 뷰 변수들
    private lateinit var cancelTextView: TextView
    private lateinit var saveButton: Button
    private lateinit var detailsEditText: EditText
    private lateinit var detailsSwitch: Switch
    private lateinit var remindSwitch: Switch
    private lateinit var timePicker: TimePicker
    private lateinit var detailsLayout: LinearLayout
    private lateinit var descriptionBtn: Button

    private var loadedDetailsText: String = ""

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
        detailsLayout = findViewById(R.id.details)
        detailsSwitch = findViewById(R.id.details_switch_btn)
        remindSwitch = findViewById(R.id.remind_switch_btn)
        timePicker = findViewById(R.id.timePicker)
        descriptionBtn = findViewById(R.id.remind_des_btn)

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

        // 내용 스위치 상태 변경 이벤트 추가
        detailsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                detailsLayout.visibility = View.VISIBLE
                detailsEditText.setText(loadedDetailsText)
                detailsEditText.requestFocus() // EditText에 포커스 주기
            } else {
                detailsLayout.visibility = View.GONE
                loadedDetailsText = detailsEditText.text.toString()
                detailsEditText.text.clear() // 내용 초기화
            }
        }

        descriptionBtn.setOnClickListener{
            showRemindDialog()
        }
    }

    private fun loadAlarmData() {
        uniqueUserId = UniqueIDManager.getInstance(applicationContext).getUniqueUserId()
        val alarmRef = FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child(uniqueUserId)
            .child(alarmId)

        alarmRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alarm = snapshot.getValue(AlarmData::class.java)
                if (alarm != null) {
                    loadedDetailsText = alarm.detailsText

                    // EditText에 기존 내용을 채움
                    detailsEditText.setText(alarm.detailsText)
                    remindSwitch.isChecked = alarm.remindEnabled

                    // 스위치 초기 상태 설정
                    if (alarm.detailsText.isNotEmpty()) {
                        detailsEditText.setText(alarm.detailsText)
                        detailsSwitch.isChecked = true
                        detailsLayout.visibility = View.VISIBLE
                    } else {
                        detailsSwitch.isChecked = false
                        detailsLayout.visibility = View.GONE
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

        // 현재 시간과 비교하여 lightningEnabled 설정
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY) // 24시간 형식
        val currentMinute = calendar.get(Calendar.MINUTE)

        val alarmHour24 = when {
            newAmPm == "PM" && newHour != 12 -> newHour + 12
            newAmPm == "AM" && newHour == 12 -> 0
            else -> newHour
        }

        val lightningEnabled = if (alarmHour24 > currentHour || (alarmHour24 == currentHour && newMinute > currentMinute)) {
            true // 현재 시간 이후라면 예정 알람
        } else {
            false // 현재 시간 이전이면 지난 알람
        }

        val updateMap = mapOf(
            "detailsText" to newDetails,
            "detailsEnabled" to newDetailsEnabled,
            "remindEnabled" to newRemindEnabled,
            "hour" to newHour,
            "minute" to newMinute,
            "amPm" to newAmPm,
            "lightningEnabled" to lightningEnabled // ⚡ 라이트닝 상태 업데이트
        )

        uniqueUserId = UniqueIDManager.getInstance(applicationContext).getUniqueUserId()
        FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child(uniqueUserId)
            .child(alarmId)
            .updateChildren(updateMap)
            .addOnSuccessListener {
                Toast.makeText(this, "알림 업데이트 완료 ⚡️", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "알림 업데이트 실패 ⚡️", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRemindDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.toggle)

        // 루트/팝업 레이아웃 참조
        val rootLayout = dialog.findViewById<LinearLayout>(R.id.rootLayout)
        val popupLayout = dialog.findViewById<LinearLayout>(R.id.popupLayout)

        // 루트 영역 클릭 시 -> 다이얼로그 닫기
        rootLayout.setOnClickListener {
            dialog.dismiss()
        }
        // 팝업 영역 클릭 시 -> 이벤트 소비 (닫히지 않음)
        popupLayout.setOnClickListener {
        }

        // 화면 전체를 MATCH_PARENT로 확장
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        // 배경을 50% 투명 검정으로 설정
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                android.graphics.Color.parseColor("#50000000")
            )
        )

        dialog.show()
    }
}