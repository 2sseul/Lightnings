package com.my_app.lightning

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase

class AlarmReceiver : BroadcastReceiver() {
    private lateinit var uniqueUserId: String

    @SuppressLint("ResourceAsColor")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        // SharedPreferences에서 전역 정지 상태를 동기적으로 읽어옴
        val sharedPref: SharedPreferences = context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
        val isAllStoppedLocal = sharedPref.getBoolean("isAllStopped", false)

        // 알람 내용 및 alarmId 가져오기
        val contentTitle = intent.getStringExtra("contentText") ?: "알람이 울립니다!"
        val alarmId = intent.getStringExtra("alarmId")
        // 천둥소리 기능 여부 확인 (true이면 천둥소리 적용)
        val thunderEnabled = intent.getBooleanExtra("remindEnabled", false)
        uniqueUserId = UniqueIDManager.getInstance(context).getUniqueUserId()

        Log.d("AlarmReceiver", "Alarm triggered: $contentTitle, alarmId: $alarmId, thunderEnabled: $thunderEnabled")

        // 알람이 울렸으므로 Firebase에서 해당 알람의 lightningEnabled를 false로 업데이트
        if (alarmId != null) {
            val dbRef = FirebaseDatabase.getInstance().reference
                .child("alarms")
                .child(uniqueUserId)
                .child(alarmId)
            dbRef.child("remindEnabled").setValue(false)
                .addOnSuccessListener {
                    Log.d("AlarmReceiver", "alarmId $alarmId → remindEnabled 업데이트 완료")
                }
                .addOnFailureListener { e ->
                    Log.e("AlarmReceiver", "alarmId $alarmId → remindEnabled 업데이트 실패: ${e.message}")
                }
        }

        // 전역 정지 상태(true)일 경우 알람 소리 없이 lightning off 처리 후 브로드캐스트 전송
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
            .setSmallIcon(R.drawable.icon_light_notification_ic)
            .setColor(ContextCompat.getColor(context, R.color.white))
            .setContentTitle(contentTitle)
            .setAutoCancel(true)
            .build()

        // 알림 표시
        notificationManager.notify(alarmId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)

        // 천둥소리 기능이 true인 경우에만, 25초 후부터 5초간 1초마다 진동 실행 (총 5회)
        if (thunderEnabled) {
            Handler(Looper.getMainLooper()).postDelayed({
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                object : CountDownTimer(5000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            vibrator.vibrate(1000)
                        }
                    }
                    override fun onFinish() {
                        // 필요 시 추가 작업 구현 가능
                    }
                }.start()
            }, 25_000)
        }

        // 30초 후 자동으로 Notification 삭제
        Handler(Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(alarmId?.hashCode() ?: 0)
            Log.d("AlarmReceiver", "alarmId $alarmId → Notification 30초 후 자동 삭제 완료")
        }, 30_000)
    }
}
