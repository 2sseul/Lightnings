package com.example.lightning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val alarmTitle = intent.getStringExtra("alarmTitle") ?: "알람"

        showNotification(context, alarmId, alarmTitle)
        updateAlarmStatus(alarmId) // 알람 울린 후 상태 업데이트
    }

    private fun showNotification(context: Context, alarmId: String, alarmTitle: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0 이상에서 채널 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ALARM_CHANNEL",
                "알람 채널",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 알람 클릭 시 실행될 인텐트 (MainActivity로 이동)
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent =
            PendingIntent.getActivity(context, alarmId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT)

        // 푸시 알림 생성
        val notification = NotificationCompat.Builder(context, "ALARM_CHANNEL")
            .setSmallIcon(R.drawable.ok_thunder)
            .setContentTitle("⏰ 알람")
            .setContentText(alarmTitle)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // 알림 표시
        notificationManager.notify(alarmId.hashCode(), notification)

        // 1분 뒤 알림 제거
        removeNotificationAfterDelay(notificationManager, alarmId.hashCode())
    }

    private fun removeNotificationAfterDelay(notificationManager: NotificationManager, notificationId: Int) {
        Thread {
            Thread.sleep(60 * 1000) // 1분 후
            notificationManager.cancel(notificationId) // 알림 제거
        }.start()
    }

    private fun updateAlarmStatus(alarmId: String) {
        val database = FirebaseDatabase.getInstance().reference.child("alarms").child("test_user")

        // 알람이 울린 후 상태 업데이트 (라이트닝 OFF + 전체알림으로 이동)
        database.child(alarmId).child("isActive").setValue(false)
    }
}
