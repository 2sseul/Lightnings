package com.my_app.lightning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase

class AlarmReceiver : BroadcastReceiver() {
    private lateinit var uniqueUserId: String

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        // 알람 내용 및 고유 ID 가져오기
        val contentTitle = intent.getStringExtra("contentText") ?: "알람이 울립니다!"
        val alarmId = intent.getStringExtra("alarmId") // 예약 시 전달한 alarmId

        uniqueUserId = UniqueIDManager.getInstance(context).getUniqueUserId()

        Log.d("AlarmReceiver", "Alarm triggered: $contentTitle, alarmId: $alarmId")

        // 알람이 울리면 해당 알람의 lightningEnabled를 false로 업데이트
        if (alarmId != null) {
            val dbRef = FirebaseDatabase.getInstance().reference
                .child("alarms")
                .child(uniqueUserId)
                .child(alarmId)
            dbRef.child("lightningEnabled").setValue(false)
                .addOnSuccessListener {
                    Log.d("AlarmReceiver", "alarmId $alarmId lightningEnabled 업데이트 완료")
                }
                .addOnFailureListener { e ->
                    Log.e("AlarmReceiver", "alarmId $alarmId lightningEnabled 업데이트 실패: ${e.message}")
                }
        }

        // 알림 생성
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "alarm_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.enableLights(true)
            channel.lightColor = Color.RED
            channel.enableVibration(true)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification) // 반드시 아이콘 리소스를 추가하세요.
            .setContentTitle(contentTitle)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
