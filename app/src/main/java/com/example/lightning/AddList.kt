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

        database = FirebaseDatabase.getInstance().reference

        val cancleBtn: TextView = findViewById(R.id.cancle)
        val saveBtn: Button = findViewById(R.id.saveBtn)
        val timePicker: TimePicker = findViewById(R.id.timePicker)
        val lightningSwitch: Switch = findViewById(R.id.lightning_switch_btn)
        val remindSwitch: Switch = findViewById(R.id.remind_switch_btn)
        val detailsSwitch: Switch = findViewById(R.id.details_switch_btn)
        val detailsEditText: EditText = findViewById(R.id.details_editText)

        timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = cal.get(Calendar.MINUTE)

        cancleBtn.setOnClickListener {
            startActivity(Intent(this@AddList, MainActivity::class.java))
        }

        detailsSwitch.setOnCheckedChangeListener { _, isChecked ->
            detailsEditText.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        saveBtn.setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            val lightningEnabled = lightningSwitch.isChecked
            val remindEnabled = remindSwitch.isChecked
            val detailsEnabled = detailsSwitch.isChecked
            val detailsText = detailsEditText.text.toString()

            val amPm = if (hour >= 12) "PM" else "AM"
            val formattedHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour

            val alarmTimeMillis = getAlarmTimeMillis(formattedHour, minute, amPm)
            val currentTimeMillis = System.currentTimeMillis()

            // ✅ 알람 시간이 현재 시간보다 과거라면 자동으로 "다음 날"로 설정
            val adjustedAlarmTimeMillis = if (alarmTimeMillis <= currentTimeMillis) {
                alarmTimeMillis + 24 * 60 * 60 * 1000 // 24시간 추가
            } else {
                alarmTimeMillis
            }

            saveDataToFirebase(
                formattedHour, minute, amPm, lightningEnabled, remindEnabled, detailsEnabled, detailsText, adjustedAlarmTimeMillis
            )

            startActivity(Intent(this@AddList, MainActivity::class.java))
        }
    }

    private fun saveDataToFirebase(
        hour: Int,
        minute: Int,
        amPm: String,
        lightningEnabled: Boolean,
        remindEnabled: Boolean,
        detailsEnabled: Boolean,
        detailsText: String,
        alarmTimeMillis: Long
    ) {
        val userId = "test_user"

        val alarmData = mapOf(
            "hour" to hour,
            "minute" to minute,
            "amPm" to amPm,
            "lightningEnabled" to lightningEnabled,
            "remindEnabled" to remindEnabled,
            "detailsEnabled" to detailsEnabled,
            "detailsText" to detailsText,
            "isBookmarked" to false,
            "isActive" to lightningEnabled,
            "alarmTimeMillis" to alarmTimeMillis // ✅ Firebase에 저장
        )

        database.child("alarms").child(userId).push().setValue(alarmData)
            .addOnSuccessListener {
                Toast.makeText(this, "알림이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getAlarmTimeMillis(hour: Int, minute: Int, amPm: String): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (amPm == "PM" && hour < 12) {
            calendar.set(Calendar.HOUR_OF_DAY, hour + 12)
        } else if (amPm == "AM" && hour == 12) {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, hour)
        }

        calendar.set(Calendar.MINUTE, minute)
        return calendar.timeInMillis
    }
}
