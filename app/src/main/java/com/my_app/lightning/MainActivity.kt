package com.my_app.lightning

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.compose.materialcore.currentTimeMillis
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar
import android.Manifest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var database: DatabaseReference

    // 예정알림 영역: lightningEnabled가 true인 알람
    private val currentAlarmList = mutableListOf<Pair<String, AlarmData>>()
    // 지난알림 영역: lightningEnabled가 false인 알람
    private val allAlarmList = mutableListOf<Pair<String, AlarmData>>()

    // 안내 문구 TextView (각 섹션)
    private lateinit var noCurrentAlarmsText: TextView
    private lateinit var noAllAlarmsText: TextView

    // (삭제된 항목 중복 방지를 위한 집합)
    private val hiddenAlarmIds = mutableSetOf<String>()

    // RecyclerView와 어댑터 선언
    private lateinit var currentAlarmRecyclerView: RecyclerView
    private lateinit var currentAlarmAdapter: AlarmAdapter

    private lateinit var allAlarmRecyclerView: RecyclerView
    private lateinit var allAlarmAdapter: AlarmAdapter

    private lateinit var uniqueUserId: String

    private var isAllStopped = false // 일괄 정지 상태 저장 변수

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase 초기화 ("alarms/test_user")
        uniqueUserId = UniqueIDManager.getInstance(applicationContext).getUniqueUserId()
        database = FirebaseDatabase.getInstance().reference.child("alarms").child(uniqueUserId)

        // UI 요소 연결
        currentAlarmRecyclerView = findViewById(R.id.currentAlarmRecyclerView)
        allAlarmRecyclerView = findViewById(R.id.allAlarmRecyclerView)
        noCurrentAlarmsText = findViewById(R.id.noCurrentAlarmsText)
        noAllAlarmsText = findViewById(R.id.noAllAlarmsText)

        // RecyclerView 초기화
        currentAlarmAdapter = AlarmAdapter(this, currentAlarmList)
        currentAlarmRecyclerView.apply {
            adapter = currentAlarmAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        allAlarmAdapter = AlarmAdapter(this, allAlarmList, isGrayColor = true)
        allAlarmRecyclerView.apply {
            adapter = allAlarmAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        allAlarmAdapter = AlarmAdapter(this, allAlarmList, isGrayColor = true)
        allAlarmRecyclerView.apply {
            adapter = allAlarmAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // **푸쉬 알림 권한 요청 런처 등록**
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d("MainActivity", "✅ 푸쉬 알림 권한 허용됨")
            } else {
                Log.w("MainActivity", "🚫 푸쉬 알림 권한 거부됨")
                showPermissionDeniedMessage()
            }
        }

        checkAndRequestNotificationPermission()

        loadAlarmsFromFirebase()


        // 기타 UI 처리 (북마크, 추가 버튼, 스위치 등)
        findViewById<ImageView>(R.id.bookmark).setOnClickListener {
            startActivity(Intent(this, BookmarkActivity::class.java))
        }
        findViewById<ImageView>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddList::class.java))
        }
        findViewById<Switch>(R.id.switch_all_stop).setOnCheckedChangeListener { _, isChecked ->
            updateCurrentAlarmsState(isChecked)
        }
        findViewById<ImageView>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsAcivity::class.java))
        }

        scheduleMidnightReset()

        resetAlarmsAtMidnight()
        loadAlarmsFromFirebase()

        attachSwipeHandler(currentAlarmRecyclerView, currentAlarmAdapter, currentAlarmList)
        attachSwipeHandler(allAlarmRecyclerView, allAlarmAdapter, allAlarmList)

        // 아이콘 클릭 이벤트 처리 (토글 기능)
        val itemClickListener = object : AlarmAdapter.OnItemClickListener {
            override fun onLightningClick(alarm: AlarmData, position: Int) {
                // 라이트닝 온/오프: lightningEnabled 필드를 토글
                if (!alarm.lightningEnabled && alarm.isActive) return
                val newLightningStatus = !alarm.lightningEnabled
                Log.d("MainActivity", "라이트닝 토글: 이전=${alarm.lightningEnabled}, 이후=$newLightningStatus")
                database.child(alarm.id).child("lightningEnabled").setValue(newLightningStatus)
                    .addOnSuccessListener { loadAlarmsFromFirebase() }
                    .addOnFailureListener { loadAlarmsFromFirebase() }
            }
            override fun onBookmarkClick(alarm: AlarmData, position: Int) {
                val newBookmarkStatus = !alarm.isBookmarked
                Log.d("MainActivity", "북마크 토글: 이전=${alarm.isBookmarked}, 이후=$newBookmarkStatus")
                database.child(alarm.id).child("isBookmarked").setValue(newBookmarkStatus)
                    .addOnSuccessListener { loadAlarmsFromFirebase() }
                    .addOnFailureListener { loadAlarmsFromFirebase() }
            }
            override fun onItemClick(alarm: AlarmData, position: Int) {
                val intent = Intent(this@MainActivity, AlarmEditActivity::class.java)
                intent.putExtra("alarmId", alarm.id)
                startActivity(intent)
            }
        }
        currentAlarmAdapter.setOnItemClickListener(itemClickListener)
        allAlarmAdapter.setOnItemClickListener(itemClickListener)
    }

    // 푸쉬 알림 권한 확인 및 요청
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13(API 33) 이상만 적용
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // 권한이 허용되지 않은 경우 사용자에게 요청
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    //사용자가 권한 거부했을 때 메시지
    private fun showPermissionDeniedMessage() {
        runOnUiThread {
            android.widget.Toast.makeText(
                this,
                "푸쉬 알림 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    // API 31 이상에서만 onResume, onDestroy에서 알람 예약 (테스트 시 에뮬레이터 API 버전 확인)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        scheduleLightningPushAlarms() // 라이트닝이 켜진 알람만 예약
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        super.onDestroy()
        scheduleLightningPushAlarms() // 앱 종료 시에도 재예약 (원한다면)
    }

    private fun attachSwipeHandler(
        recyclerView: RecyclerView,
        adapter: AlarmAdapter,
        alarmList: MutableList<Pair<String, AlarmData>>
    ) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val alarm = alarmList[position].second
                Log.d("MainActivity", "스와이프 삭제 시 alarm id: ${alarm.id}")
                database.child(alarm.id).child("isDeleted").setValue(true)
                    .addOnSuccessListener {
                        adapter.notifyItemChanged(position)
                        loadAlarmsFromFirebase()
                    }
                    .addOnFailureListener {
                        adapter.notifyItemChanged(position)
                    }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.RED)
                background.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                background.draw(c)
                val deleteIcon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_delete)
                deleteIcon?.let {
                    val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconBottom = iconTop + it.intrinsicHeight
                    val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    it.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun loadAlarmsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            @SuppressLint("RestrictedApi")
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onDataChange(snapshot: DataSnapshot) {
                // hiddenAlarmIds 내용 로그 출력 (비어있어야 함)
                Log.d("MainActivity", "hiddenAlarmIds: ${hiddenAlarmIds.joinToString(", ")}")

                // Firebase 전체 스냅샷 로그 (전체 데이터 확인)
                Log.d("MainActivity", "Firebase snapshot: ${snapshot.value}")

                // 목록 초기화
                currentAlarmList.clear()
                allAlarmList.clear()

                // 현재 시간 가져오기
                val now = Calendar.getInstance()
                val currentTimeMills = now.timeInMillis

                // 23시 59분까지만 예정알림에 두기
                val futureLimit = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23) // 오늘 23시
                    set(Calendar.MINUTE, 59)      // 59분
                    set(Calendar.SECOND, 59)      // 59초
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                val tempCurrentAlarms = mutableListOf<Pair<String, AlarmData>>()
                val tempAllAlarms = mutableListOf<Pair<String, AlarmData>>()

                // 각 알람 데이터에 대해 처리
                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        // 만약 alarm.id가 비어 있다면, Firebase 키를 사용
                        if (alarm.id.isEmpty()) {
                            alarm.id = alarmSnapshot.key ?: ""
                        }

                        val alarmCalendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, if (alarm.amPm == "PM" && alarm.hour < 12) alarm.hour + 12 else alarm.hour)
                            set(Calendar.MINUTE, alarm.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val alarmTimeMillis = alarmCalendar.timeInMillis

                        // 각 알람의 상태를 로그로 출력 (디버깅용)
                        Log.d("MainActivity", "알람 ${alarm.id}: isDeleted=${alarm.isDeleted}, hidden=${hiddenAlarmIds.contains(alarm.id)}, lightningEnabled=${alarm.lightningEnabled}")

                        // isDeleted가 true이거나 hiddenAlarmIds에 포함되어 있다면 건너뛰기
                        if (alarm.isDeleted || hiddenAlarmIds.contains(alarm.id)) {
                            Log.d("MainActivity", "알람 ${alarm.id} 건너뜀 (조건에 의해)")
                            continue
                        }

                        if (alarm.lightningEnabled && alarmTimeMillis in currentTimeMillis()..futureLimit) {
                            // 라이트닝이 켜져 있고, 현재시간 ~ 24시간 이내의 알람만 현재 알림 리스트에 추가
                            tempCurrentAlarms.add(Pair(alarmSnapshot.key ?: "", alarm))
                        } else {
                            // 24시간이 지났거나, 라이트닝이 꺼져 있는 경우 전체 알림 리스트에 추가
                            database.child(alarm.id).child("isActive").setValue(true)
                            tempAllAlarms.add(Pair(alarmSnapshot.key ?: "", alarm))
                        }

                    } else {
                        Log.d("MainActivity", "알람 데이터 매핑 실패: ${alarmSnapshot.value}")
                    }
                }

                Log.d("MainActivity", "currentAlarmList size: ${currentAlarmList.size}")
                Log.d("MainActivity", "allAlarmList size: ${allAlarmList.size}")

                currentAlarmList.addAll(tempCurrentAlarms.sortedWith(compareBy(::sortByAlarmTime)))
                allAlarmList.addAll(tempAllAlarms.sortedWith(compareBy(::sortByAlarmTime)))

                currentAlarmAdapter.notifyDataSetChanged()
                allAlarmAdapter.notifyDataSetChanged()
                updateNoAlarmsText()

                // 라이트닝이 켜진 알람만 예약 (예약 함수는 별도로 구현)
                scheduleLightningPushAlarms()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "데이터 읽기 실패: ${error.message}")
            }
        })
    }


    private fun sortByAlarmTime(alarmData: Pair<String, AlarmData>): Long {
        return getAlarmTimeMillis(alarmData.second)
    }

    private fun getAlarmTimeMillis(alarm: AlarmData): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 🔹 AM/PM을 고려하여 24시간 형식으로 변환
            val hour24 = when {
                alarm.amPm == "PM" && alarm.hour < 12 -> alarm.hour + 12
                alarm.amPm == "AM" && alarm.hour == 12 -> 0
                else -> alarm.hour
            }

            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, alarm.minute)
        }
        return calendar.timeInMillis
    }

    private fun updateNoAlarmsText() {
        noCurrentAlarmsText.visibility = if (currentAlarmList.isEmpty()) View.VISIBLE else View.GONE
        noAllAlarmsText.visibility = if (allAlarmList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun resetAlarmsAtMidnight() {
        val calendar = Calendar.getInstance()
        // 현재 시각이 자정(0시)인 경우에만 실행
        if (calendar.get(Calendar.HOUR_OF_DAY) == 1 && calendar.get(Calendar.MINUTE) == 20) {
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (alarmSnapshot in snapshot.children) {
                        val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                        // 북마크되어 있지 않은 알람만 업데이트
                        if (alarm != null && !alarm.isBookmarked) {
                            // isDeleted를 true, lightningEnabled를 false로 업데이트
                            val updateMap = mapOf<String, Any>(
                                "isDeleted" to true,
                                "lightningEnabled" to false
                            )
                            alarmSnapshot.ref.updateChildren(updateMap)
                                .addOnSuccessListener {
                                    Log.d("MainActivity", "알람 ${alarm.id} 업데이트 성공")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "알람 ${alarm.id} 업데이트 실패: ${e.message}")
                                }
                        }
                    }
                    // 업데이트 후 데이터를 다시 불러옴
                    loadAlarmsFromFirebase()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MainActivity", "데이터 읽기 실패: ${error.message}")
                }
            })
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun updateCurrentAlarmsState(isStopped: Boolean) {
        database.child("isAllStopped").setValue(isStopped)
            .addOnSuccessListener {
                Log.d("MainActivity", "일괄 정지 상태 업데이트 성공: $isStopped")

                // 🔹 상태 변경 즉시 푸쉬 알람 설정 업데이트
                scheduleLightningPushAlarms()
            }
            .addOnFailureListener {
                Log.e("MainActivity", "일괄 정지 상태 업데이트 실패")
            }
    }

    // 라이트닝이 켜진 알람만 예약하는 함수
    @RequiresApi(Build.VERSION_CODES.S)
    private fun scheduleLightningPushAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (isAllStopped) {
            // 🔹 현재 알림 리스트가 비어있지 않은 경우에만 취소 수행
            if (currentAlarmList.isNotEmpty()) {
                for ((_, alarm) in currentAlarmList) {
                    cancelPushAlarm(alarm, alarmManager)
                }
                Log.d("MainActivity", "🚫 일괄 정지 ON → 모든 푸쉬 알람 취소됨")
            } else {
                Log.d("MainActivity", "🚫 일괄 정지 ON → 하지만 예정 알람이 없음")
            }
            return
        }

        // 🔹 일괄 정지가 OFF인 경우 → 푸쉬 알람 다시 예약
        if (currentAlarmList.isNotEmpty()) {
            for ((_, alarm) in currentAlarmList) {
                if (!alarm.isDeleted && alarm.lightningEnabled) {
                    schedulePushAlarm(alarm, alarmManager)
                }
            }
            Log.d("MainActivity", "✅ 일괄 정지 OFF → 푸쉬 알람 정상 작동")
        } else {
            Log.d("MainActivity", "✅ 일괄 정지 OFF → 하지만 예정 알람이 없음")
        }
    }

    // 개별 알람 취소 함수
    private fun cancelPushAlarm(alarm: AlarmData, alarmManager: AlarmManager) {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // 개별 알람 예약 함수 (로컬 알림)
    @SuppressLint("ScheduleExactAlarm")
    private fun schedulePushAlarm(alarm: AlarmData, alarmManager: AlarmManager) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("contentText", alarm.detailsText)
            putExtra("alarmId", alarm.id)  // 알람 ID 전달
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, if (alarm.amPm == "PM" && alarm.hour < 12) alarm.hour + 12 else alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d("MainActivity", "푸시 알람 예약됨: alarmId=${alarm.id}, timeInMillis=${calendar.timeInMillis}, contentText=${alarm.detailsText}")
    }

    private fun scheduleMidnightReset() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, MidnightResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 자정에 실행될 시간을 계산
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 현재 시간이 이미 자정이 지난 경우 다음 날로 설정
            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // 매일 자정마다 실행 (INTERVAL_DAY)
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )

        Log.d("MainActivity", "자정 리셋 알람 예약됨: ${calendar.timeInMillis}")
    }




    private fun getAllStopStateFromFirebase() {
        database.child("isAllStopped").addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    isAllStopped = snapshot.getValue(Boolean::class.java) ?: false
                    Log.d("MainActivity", "일괄 정지 상태 변경됨: $isAllStopped")

                    // 변경된 정지 상태에 따라 푸쉬 알람 조정
                    scheduleLightningPushAlarms()
                } catch (e: Exception) {
                    Log.e("MainActivity", "일괄 정지 상태 업데이트 오류: ${e.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Firebase에서 isAllStopped 읽기 실패: ${error.message}")
            }
        })
    }

}
