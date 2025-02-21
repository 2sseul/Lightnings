package com.my_app.lightning

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
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
        val detailsLayout: LinearLayout = findViewById(R.id.details)
        val detailsEditText: EditText = findViewById(R.id.details_editText)
        val descriptionBtn: Button = findViewById(R.id.remind_des_btn)

        timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = cal.get(Calendar.MINUTE)

        cancleBtn.setOnClickListener {
            startActivity(Intent(this@AddList, MainActivity::class.java))
        }

        descriptionBtn.setOnClickListener{
            showRemindDialog()
        }

        // 내용 스위치 상태 변경 이벤트 추가
        detailsSwitch.setOnCheckedChangeListener { _, isChecked ->
            detailsLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        saveBtn.setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            val remindEnabled = remindSwitch.isChecked
            val detailsEnabled = detailsSwitch.isChecked
            val detailsText = if (detailsEnabled) detailsEditText.text.toString() else ""

            val amPm = if (hour >= 12) "PM" else "AM"
            val formattedHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour

            val alarmTimeMillis = getAlarmTimeMillis(formattedHour, minute, amPm)
            val currentTimeMillis = System.currentTimeMillis()

            // 오늘 23:59:59까지를 기준으로 futureLimit 설정
            val futureLimit = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            // 현재시간 ~ 오늘 24시 이전이면 예정알람으로 추가
            val isCurrentAlarm = alarmTimeMillis in currentTimeMillis..futureLimit

            saveDataToFirebase(
                formattedHour, minute, amPm, true, remindEnabled, detailsEnabled, detailsText, alarmTimeMillis, isCurrentAlarm
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
        alarmTimeMillis: Long,
        isCurrentAlarm: Boolean
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
            "alarmTimeMillis" to alarmTimeMillis,
            "isCurrentAlarm" to isCurrentAlarm
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