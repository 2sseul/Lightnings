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
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar
import android.Manifest
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : ComponentActivity() {

    // 알람 데이터와 전역 설정 데이터를 위한 별도 DatabaseReference
    private lateinit var alarmDatabase: DatabaseReference
    private lateinit var settingsDatabase: DatabaseReference

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

    private var isAllStopped = false // 전역 설정 값: 일괄 정지 상태

    private var isExpanded = false

    private lateinit var currentAlarmFrame: View
    private lateinit var moreButton: LinearLayout
    private lateinit var moreButtonText: TextView
    private lateinit var moreButtonIcon: ImageView

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // uniqueUserId 초기화 및 DatabaseReference 초기화
        uniqueUserId = UniqueIDManager.getInstance(applicationContext).getUniqueUserId()
        alarmDatabase = FirebaseDatabase.getInstance().reference.child("alarms").child(uniqueUserId)
        settingsDatabase = FirebaseDatabase.getInstance().reference.child("userSettings").child(uniqueUserId)

        // UI 요소 연결
        currentAlarmRecyclerView = findViewById(R.id.currentAlarmRecyclerView)
        allAlarmRecyclerView = findViewById(R.id.allAlarmRecyclerView)
        noCurrentAlarmsText = findViewById(R.id.noCurrentAlarmsText)
        noAllAlarmsText = findViewById(R.id.noAllAlarmsText)
        currentAlarmFrame = findViewById(R.id.currentAlarmFrame)
        moreButton = findViewById(R.id.moreButton)
        moreButtonText = moreButton.findViewById(R.id.moreButtonText)
        moreButtonIcon = moreButton.findViewById(R.id.moreButtonIcon)

        // RecyclerView 초기화 (중복된 allAlarmAdapter 초기화 제거)
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

        // 푸쉬 알림 권한 요청 런처 등록
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

        // 알람 데이터 로드
        loadAlarmsFromFirebase()

        // 전역 설정 값(isAllStopped) 동기화
        getAllStopStateFromFirebase()

        getDontShowIntroStateFromFirebase()

        // 기타 UI 처리
        findViewById<ImageView>(R.id.home).setImageResource(R.drawable.icon_light_home_click)
        findViewById<ImageView>(R.id.bookmark).setOnClickListener {
            startActivity(Intent(this, BookmarkActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<FrameLayout>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddList::class.java))
        }
        findViewById<Switch>(R.id.switch_all_stop).setOnCheckedChangeListener { _, isChecked ->
            updateCurrentAlarmsState(isChecked)
        }
        findViewById<ImageView>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
        }

        scheduleMidnightReset()
        resetAlarmsAtMidnight()
        loadAlarmsFromFirebase()

        attachSwipeHandler(currentAlarmRecyclerView, currentAlarmAdapter, currentAlarmList)
        attachSwipeHandler(allAlarmRecyclerView, allAlarmAdapter, allAlarmList)

        val introOverlay = findViewById<FrameLayout>(R.id.introOverlay)
        val btnCloseIntro = findViewById<ImageButton>(R.id.btnCloseIntro)
        val btnDontShowAgain = findViewById<LinearLayout>(R.id.btnDontShowAgain)

        // '다시보지 않기' 버튼 클릭 시 Firebase에 값 저장
        btnDontShowAgain.setOnClickListener {
            settingsDatabase.child("dont_show_intro").setValue(true)
                .addOnSuccessListener {
                    Log.d("MainActivity", "'dont_show_intro' 저장 성공")
                }
                .addOnFailureListener {
                    Log.e("MainActivity", "'dont_show_intro' 저장 실패: ${it.message}")
                }
        }

        // X 버튼 클릭 시 오버레이만 닫음 (Firebase 값은 변경하지 않음)
        btnCloseIntro.setOnClickListener {
            introOverlay.visibility = View.GONE
        }

        // 아이콘 클릭 이벤트 처리 (토글 기능)
        val itemClickListener = object : AlarmAdapter.OnItemClickListener {
            override fun onLightningClick(alarm: AlarmData, position: Int) {
                // 라이트닝 온/오프: lightningEnabled 필드를 토글
                if (!alarm.lightningEnabled && alarm.isActive) return
                val newLightningStatus = !alarm.lightningEnabled
                Log.d("MainActivity", "라이트닝 토글: 이전=${alarm.lightningEnabled}, 이후=$newLightningStatus")
                alarmDatabase.child(alarm.id).child("lightningEnabled").setValue(newLightningStatus)
                    .addOnSuccessListener { loadAlarmsFromFirebase() }
                    .addOnFailureListener { loadAlarmsFromFirebase() }
            }
            override fun onBookmarkClick(alarm: AlarmData, position: Int) {
                val newBookmarkStatus = !alarm.isBookmarked
                Log.d("MainActivity", "북마크 토글: 이전=${alarm.isBookmarked}, 이후=$newBookmarkStatus")
                alarmDatabase.child(alarm.id).child("isBookmarked").setValue(newBookmarkStatus)
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

        moreButton.setOnClickListener {
            isExpanded = !isExpanded
            toggleCurrentAlarmsHeight(isExpanded)
        }

        // 브로드캐스트 리시버 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            alarmUpdateReceiver,
            IntentFilter("com.my_app.lightning.ALARM_UPDATED")
        )
    }

    private val alarmUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val updatedAlarmId = intent?.getStringExtra("alarmId")
            if (updatedAlarmId != null) {
                // 로컬 리스트에서 해당 알람 상태를 즉시 업데이트
                for (i in currentAlarmList.indices) {
                    val alarmData = currentAlarmList[i].second
                    if (alarmData.id == updatedAlarmId) {
                        alarmData.lightningEnabled = false
                        alarmData.isActive = true
                        currentAlarmAdapter.notifyItemChanged(i)
                        Log.d("MainActivity", "로컬 알람 업데이트: alarmId=$updatedAlarmId, lightningEnabled=false")
                        break
                    }
                }
            }
        }
    }

    private fun toggleCurrentAlarmsHeight(expanded: Boolean) {
        if (expanded) {
            currentAlarmFrame.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            moreButtonText.text = "접기"
            moreButtonIcon.setImageResource(R.drawable.icon_arrow_top)
        } else {
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                280f,
                resources.displayMetrics
            ).toInt()
            currentAlarmFrame.layoutParams.height = px
            moreButtonText.text = "더보기"
            moreButtonIcon.setImageResource(R.drawable.icon_arrow_bottom)
        }
        currentAlarmFrame.requestLayout()
    }

    // 푸쉬 알림 권한 확인 및 요청
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 사용자가 권한 거부 시 메시지 표시
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        getAllStopStateFromFirebase()
        scheduleLightningPushAlarms() // 라이트닝이 켜진 알람 예약
    }

    @SuppressLint("NewApi")
    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alarmUpdateReceiver)
        super.onDestroy()
        scheduleLightningPushAlarms() // 앱 종료 시에도 예약 (선택 사항)
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
                alarmDatabase.child(alarm.id).child("isDeleted").setValue(true)
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
        alarmDatabase.addValueEventListener(object : ValueEventListener {
            @SuppressLint("RestrictedApi")
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("MainActivity", "Firebase snapshot: ${snapshot.value}")
                currentAlarmList.clear()
                allAlarmList.clear()

                val now = Calendar.getInstance()
                val currentTimeMills = now.timeInMillis
                val futureLimit = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                val tempCurrentAlarms = mutableListOf<Pair<String, AlarmData>>()
                val tempAllAlarms = mutableListOf<Pair<String, AlarmData>>()

                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
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

                        Log.d("MainActivity", "알람 ${alarm.id}: isDeleted=${alarm.isDeleted}, lightningEnabled=${alarm.lightningEnabled}")

                        if (alarm.isDeleted) {
                            Log.d("MainActivity", "알람 ${alarm.id} 건너뜀 (삭제됨)")
                            continue
                        }
                        if (alarm.lightningEnabled && alarmTimeMillis in currentTimeMills..futureLimit) {
                            tempCurrentAlarms.add(Pair(alarmSnapshot.key ?: "", alarm))
                        } else {
                            alarmDatabase.child(alarm.id).child("isActive").setValue(true)
                            tempAllAlarms.add(Pair(alarmSnapshot.key ?: "", alarm))
                        }
                    } else {
                        Log.d("MainActivity", "알람 데이터 매핑 실패: ${alarmSnapshot.value}")
                    }
                }

                currentAlarmList.addAll(tempCurrentAlarms.sortedWith(compareBy(::sortByAlarmTime)))
                allAlarmList.addAll(tempAllAlarms.sortedWith(compareBy(::sortByAlarmTime)))

                currentAlarmAdapter.notifyDataSetChanged()
                allAlarmAdapter.notifyDataSetChanged()
                updateNoAlarmsText()

                if (currentAlarmList.size > 3) {
                    moreButton.visibility = View.VISIBLE
                } else {
                    moreButton.visibility = View.GONE
                }
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
        if (calendar.get(Calendar.HOUR_OF_DAY) == 1 && calendar.get(Calendar.MINUTE) == 20) {
            alarmDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (alarmSnapshot in snapshot.children) {
                        val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                        if (alarm != null && !alarm.isBookmarked) {
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
        isAllStopped = isStopped
        settingsDatabase.child("isAllStopped").setValue(isStopped)
            .addOnSuccessListener {
                Log.d("MainActivity", "일괄 정지 상태 업데이트 성공: $isStopped")
                scheduleLightningPushAlarms()
            }
            .addOnFailureListener {
                Log.e("MainActivity", "일괄 정지 상태 업데이트 실패")
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun scheduleLightningPushAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nowMillis = Calendar.getInstance().timeInMillis

        if (isAllStopped) {
            var localUpdateDone = false
            if (currentAlarmList.isNotEmpty()) {
                for ((index, pair) in currentAlarmList.withIndex()) {
                    val alarm = pair.second
                    // 예약된 푸쉬 알람 취소
                    cancelPushAlarm(alarm, alarmManager)
                    val alarmTimeMillis = getAlarmTimeMillis(alarm)
                    // 알람 시간이 도래했는데 lightningEnabled가 true이면 로컬 업데이트
                    if (nowMillis >= alarmTimeMillis && alarm.lightningEnabled) {
                        alarm.lightningEnabled = false
                        alarm.isActive = true
                        localUpdateDone = true
                        // Firebase 업데이트 (비동기)
                        alarmDatabase.child(alarm.id).child("lightningEnabled").setValue(false)
                        alarmDatabase.child(alarm.id).child("isActive").setValue(true)
                        Log.d("MainActivity", "알람 ${alarm.id} → 로컬 lightning off 및 isActive true")
                    }
                }
                // 로컬 업데이트가 있었으면 즉시 어댑터 갱신
                if (localUpdateDone) {
                    currentAlarmAdapter.notifyDataSetChanged()
                    // UI가 즉시 업데이트되도록 loadAlarmsFromFirebase()를 다시 호출할 수도 있습니다.
                    loadAlarmsFromFirebase()
                }
                Log.d("MainActivity", "🚫 일괄 정지 ON → 푸쉬 알람 취소 및 무음 알람으로 처리")
            } else {
                Log.d("MainActivity", "🚫 일괄 정지 ON → 예정 알람 없음")
            }
            return
        }

        // 일괄 정지 OFF일 경우: 정상적으로 푸쉬 알람 예약 및 지난 알람 처리
        if (currentAlarmList.isNotEmpty()) {
            for ((_, alarm) in currentAlarmList) {
                val alarmTimeMillis = getAlarmTimeMillis(alarm)
                if (nowMillis < alarmTimeMillis) {
                    if (!alarm.isDeleted && alarm.lightningEnabled) {
                        schedulePushAlarm(alarm, alarmManager)
                    }
                } else {
                    if (alarm.lightningEnabled) {
                        alarmDatabase.child(alarm.id).child("lightningEnabled").setValue(false)
                    }
                }
            }
            Log.d("MainActivity", "✅ 일괄 정지 OFF → 푸쉬 알람 예약 및 지난 알람 처리")
        } else {
            Log.d("MainActivity", "✅ 일괄 정지 OFF → 예정 알람 없음")
        }
    }

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

    @SuppressLint("ScheduleExactAlarm")
    private fun schedulePushAlarm(alarm: AlarmData, alarmManager: AlarmManager) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("contentText", alarm.detailsText)
            putExtra("alarmId", alarm.id)
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
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
        Log.d("MainActivity", "자정 리셋 알람 예약됨: ${calendar.timeInMillis}")
    }

    private fun getAllStopStateFromFirebase() {
        settingsDatabase.child("isAllStopped").addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    isAllStopped = snapshot.getValue(Boolean::class.java) ?: false
                    Log.d("MainActivity", "일괄 정지 상태 변경됨: $isAllStopped")
                    // SharedPreferences에 isAllStopped 값 저장
                    val sharedPref = getSharedPreferences("global_settings", Context.MODE_PRIVATE)
                    sharedPref.edit().putBoolean("isAllStopped", isAllStopped).apply()

                    // 필요한 경우 UI 스위치 업데이트 등
                    val switchAllStop = findViewById<Switch>(R.id.switch_all_stop)
                    switchAllStop.isChecked = isAllStopped

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

    // Firebase의 settingsDatabase를 통해 'dont_show_intro' 값을 읽어오는 메서드 추가
    private fun getDontShowIntroStateFromFirebase() {
        settingsDatabase.child("dont_show_intro").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dontShowIntro = snapshot.getValue(Boolean::class.java) ?: false
                // UI 업데이트는 메인스레드에서 처리
                runOnUiThread {
                    val introOverlay = findViewById<FrameLayout>(R.id.introOverlay)
                    introOverlay.visibility = if (dontShowIntro) View.GONE else View.VISIBLE
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Firebase에서 dont_show_intro 읽기 실패: ${error.message}")
            }
        })
    }

}
