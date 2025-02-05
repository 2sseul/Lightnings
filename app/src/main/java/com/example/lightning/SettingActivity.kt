package com.example.lightning

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setting.xml 레이아웃을 사용하도록 설정
        setContentView(R.layout.setting)

        // 북마크 아이콘을 클릭하면 BookmarkActivity로 이동
        findViewById<ImageView>(R.id.bookmark).setOnClickListener {
            startActivity(Intent(this, BookmarkActivity::class.java))
        }

        // 추가 버튼을 클릭하면 AddList 액티비티로 이동
        findViewById<ImageView>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddList::class.java))
        }

        findViewById<ImageView>(R.id.settings).setOnClickListener {
            Toast.makeText(this, "이미 설정 화면입니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
