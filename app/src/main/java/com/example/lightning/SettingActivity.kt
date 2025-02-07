import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.lightning.R

class SettingActivity : AppCompatActivity() {
    private lateinit var lightningDescriptionLayout: LinearLayout
    private lateinit var lightningDesButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting)

        // 버튼과 설명 레이아웃 찾기
        lightningDesButton = findViewById(R.id.lightning_des_btn)
        lightningDescriptionLayout = findViewById(R.id.lightning_description_layout)

        // 초기 상태 로그 확인
        Log.d("SettingActivity", "lightningDesButton: $lightningDesButton")
        Log.d("SettingActivity", "lightningDescriptionLayout: $lightningDescriptionLayout")

        // 초기 상태: 설명 숨김
        lightningDescriptionLayout.visibility = View.GONE

        // 버튼 클릭 이벤트 (토글 기능)
        lightningDesButton.setOnClickListener {
            Log.d("SettingActivity", "lightningDesButton clicked!") // 클릭 로그

            if (lightningDescriptionLayout.visibility == View.VISIBLE) {
                Log.d("SettingActivity", "Hiding description")
                lightningDescriptionLayout.visibility = View.GONE // 숨기기
            } else {
                Log.d("SettingActivity", "Showing description")
                lightningDescriptionLayout.visibility = View.VISIBLE // 보이기
            }
        }
    }
}
