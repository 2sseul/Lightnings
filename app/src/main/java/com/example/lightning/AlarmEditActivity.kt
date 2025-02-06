package com.example.lightning

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var alarmId: String

    // XML 뷰 변수들
    private lateinit var cancelTextView: TextView   // id: cancle
    private lateinit var saveButton: Button         // id: saveBtn
    private lateinit var detailsEditText: EditText    // id: details_editText
    private lateinit var detailsSwitch: Switch        // id: details_switch_btn (내용 스위치)
    private lateinit var remindSwitch: Switch         // id: remind_switch_btn

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 반드시 AppCompat 테마를 사용해야 합니다.
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
                    // 리마인드 스위치는 alarm.remindEnabled 값 반영
                    remindSwitch.isChecked = alarm.remindEnabled

                    // 내용이 존재하면 내용 스위치를 true로, 그리고 EditText 보이게
                    if (alarm.detailsText.isNotEmpty()) {
                        detailsSwitch.isChecked = true
                        detailsEditText.visibility = View.VISIBLE
                    } else {
                        detailsSwitch.isChecked = false
                        detailsEditText.visibility = View.GONE
                    }
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

        val updateMap = mapOf(
            "detailsText" to newDetails,
            "detailsEnabled" to newDetailsEnabled,
            "remindEnabled" to newRemindEnabled
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
