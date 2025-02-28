package com.my_app.lightning

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_settings)

        val bookmarkIcon = findViewById<ImageView>(R.id.bookmark)
        val settingsIcon = findViewById<ImageView>(R.id.settings)

        // 현재 화면이 '설정' 페이지이므로 설정 아이콘은 클릭 상태로
        settingsIcon.setImageResource(R.drawable.icon_light_settings_click)
        // 북마크 아이콘은 기본 상태로
        bookmarkIcon.setImageResource(R.drawable.icon_light_bookmark)

        findViewById<ImageView>(R.id.bookmark).setOnClickListener {
            startActivity(Intent(this, BookmarkActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
        }

        // 설명서 클릭 시 Notion 페이지 열기
        findViewById<LinearLayout>(R.id.manualGo).setOnClickListener {
            val url = "https://principled-saturn-9fe.notion.site/8c0bbb5f50b04a849d0405e122b14648"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }
}