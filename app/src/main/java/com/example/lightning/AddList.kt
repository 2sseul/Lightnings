package com.example.lightning

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import java.util.Calendar

class AddList : ComponentActivity() {

    var cal = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addlist)

        //취소 버튼
        val btnCancle : TextView = findViewById(R.id.cancle)

        btnCancle.setOnClickListener {
            val intent = Intent(this@AddList, MainActivity::class.java)
            startActivity(intent)
        }


        // TimePicker 위젯 가져오기
        val timePicker: TimePicker = findViewById(R.id.timePicker)

        // TimePicker 초기 시간 설정
        timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = cal.get(Calendar.MINUTE)

        // TimePicker 변경 이벤트 처리
        timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
            cal.set(Calendar.MINUTE, minute)
            println("시간이 설정되었습니다: ${hourOfDay}시 ${minute}분")
        }
    }
}
