package com.example.lightning

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.Calendar

object AlarmScheduler {
    // 정확한 알람 예약 권한 체크 및 요청 함수
    @RequiresApi(Build.VERSION_CODES.S)
    fun checkAndRequestExactAlarmPermission(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "정확한 알람 예약 권한이 필요합니다. 설정에서 허용해주세요.", Toast.LENGTH_LONG).show()
            // 설정 화면으로 이동
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return false
        }
        return true
    }

    // 알람 시간(밀리초) 계산 함수
    fun getAlarmTimeMillis(hour: Int, minute: Int, amPm: String): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (amPm.equals("PM", ignoreCase = true) && hour < 12) {
            calendar.set(Calendar.HOUR_OF_DAY, hour + 12)
        } else if (amPm.equals("AM", ignoreCase = true) && hour == 12) {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, hour)
        }
        calendar.set(Calendar.MINUTE, minute)
        // 예약 시간이 현재 시간보다 이전이면 다음 날로 예약
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.timeInMillis
    }

    // 알람 예약 함수: AlarmData 객체를 받아 AlarmReceiver를 호출하도록 예약
    @RequiresApi(Build.VERSION_CODES.S)
    fun scheduleAlarm(context: Context, alarm: AlarmData) {
        if (!checkAndRequestExactAlarmPermission(context)) {
            return
        }
        val timeInMillis = if (alarm.alarmTimeMillis == 0L) {
            getAlarmTimeMillis(alarm.hour, alarm.minute, alarm.amPm)
        } else {
            alarm.alarmTimeMillis
        }
        val contentText = alarm.detailsText
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("contentText", contentText)
        }
        val requestCode = alarm.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
    }
}
