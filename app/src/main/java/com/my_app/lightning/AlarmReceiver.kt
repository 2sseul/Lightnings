package com.my_app.lightning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase

class AlarmReceiver : BroadcastReceiver() {
    private lateinit var uniqueUserId: String

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        // SharedPreferences에서 전역 정지 상태를 동기적으로 읽어옴
        val sharedPref: SharedPreferences = context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
        val isAllStoppedLocal = sharedPref.getBoolean("isAllStopped", false)

        // 알람 내용 및 alarmId 가져오기
        val contentTitle = intent.getStringExtra("contentText") ?: "알람이 울립니다!"
        val alarmId = intent.getStringExtra("alarmId")
        uniqueUserId = UniqueIDManager.getInstance(context).getUniqueUserId()

        Log.d("AlarmReceiver", "Alarm triggered: $contentTitle, alarmId: $alarmId")

        // 알람이 울렸으므로 Firebase에서 해당 알람의 lightningEnabled를 false로 업데이트
        if (alarmId != null) {
            val dbRef = FirebaseDatabase.getInstance().reference
                .child("alarms")
                .child(uniqueUserId)
                .child(alarmId)
            dbRef.child("lightningEnabled").setValue(false)
                .addOnSuccessListener {
                    Log.d("AlarmReceiver", "alarmId $alarmId → lightningEnabled 업데이트 완료")
                }
                .addOnFailureListener { e ->
                    Log.e("AlarmReceiver", "alarmId $alarmId → lightningEnabled 업데이트 실패: ${e.message}")
                }
        }

        // 전역 정지 상태(true)일 경우 알람 소리 없이 lightning만 off 처리 후 브로드캐스트 전송
        if (isAllStoppedLocal) {
            Log.d("AlarmReceiver", "전역 정지 상태(true)이므로, Notification 표시 없이 lightning off만 처리")
            val updateIntent = Intent("com.my_app.lightning.ALARM_UPDATED")
            updateIntent.putExtra("alarmId", alarmId)
            context.sendBroadcast(updateIntent)
            return
        }


        // 전역 정지 상태가 false이면 정상적으로 푸쉬 알림 생성
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "alarm_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification) // 아이콘 리소스 필요
            .setContentTitle(contentTitle)
            .setAutoCancel(true)
            .build()

        // 알림 표시
        notificationManager.notify(alarmId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)

        // 30초 후 자동으로 Notification 삭제
        Handler(Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(alarmId?.hashCode() ?: 0)
            Log.d("AlarmReceiver", "alarmId $alarmId → Notification 30초 후 자동 삭제 완료")
        }, 30_000)
    }
}