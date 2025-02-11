package com.my_app.lightning

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
    private lateinit var uniqueUserId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addlist)

        database = FirebaseDatabase.getInstance().reference

        val cancleBtn: TextView = findViewById(R.id.cancle)
        val saveBtn: Button = findViewById(R.id.saveBtn)
        val timePicker: TimePicker = findViewById(R.id.timePicker)
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
            val remindEnabled = remindSwitch.isChecked
            val detailsEnabled = detailsSwitch.isChecked
            val detailsText = detailsEditText.text.toString()

            val amPm = if (hour >= 12) "PM" else "AM"
            val formattedHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour

            val alarmTimeMillis = getAlarmTimeMillis(formattedHour, minute, amPm)
            val currentTimeMillis = System.currentTimeMillis()

            // 🔹 오늘 23:59:59까지를 기준으로 futureLimit 설정
            val futureLimit = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23) // 오늘 23시
                set(Calendar.MINUTE, 59)      // 59분
                set(Calendar.SECOND, 59)      // 59초
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            // "현재시간 ~ 오늘 24시 이전"이면 예정알람으로 추가
            // 그 외(현재시간 이전이거나 내일 이후)는 전체알람으로 추가
            val lightningEnabled = alarmTimeMillis in currentTimeMillis..futureLimit

            saveDataToFirebase(
                formattedHour, minute, amPm, lightningEnabled, remindEnabled, detailsEnabled, detailsText, alarmTimeMillis
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
        uniqueUserId = UniqueIDManager.getInstance(applicationContext).getUniqueUserId()

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
            "isDeleted" to false,
            "alarmTimeMillis" to alarmTimeMillis // Firebase에 저장
        )

        database.child("alarms").child(uniqueUserId).push().setValue(alarmData)
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
