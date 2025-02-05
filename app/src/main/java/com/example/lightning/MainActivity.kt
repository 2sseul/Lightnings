package com.example.lightning

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.ComponentActivity
import com.google.firebase.database.*
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var database: DatabaseReference

    // 현재알림 영역 (RecyclerView)와 어댑터, 리스트
    private lateinit var currentAlarmRecyclerView: RecyclerView
    private lateinit var currentAlarmAdapter: AlarmAdapter
    private val currentAlarmList = mutableListOf<AlarmData>()

    // 전체알림 영역 (RecyclerView)와 어댑터, 리스트
    private lateinit var allAlarmRecyclerView: RecyclerView
    private lateinit var allAlarmAdapter: AlarmAdapter
    private val allAlarmList = mutableListOf<AlarmData>()

    // 안내 문구 TextView (각 섹션)
    private lateinit var noCurrentAlarmsText: TextView
    private lateinit var noAllAlarmsText: TextView

    // (삭제된 항목을 중복 처리 방지를 위한 집합 – 필요시 사용)
    private val hiddenAlarmIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase 초기화 ("alarms/test_user")
        database = FirebaseDatabase.getInstance().reference.child("alarms").child("test_user")

        // UI 요소 연결
        currentAlarmRecyclerView = findViewById(R.id.currentAlarmRecyclerView)
        allAlarmRecyclerView = findViewById(R.id.allAlarmRecyclerView)
        noCurrentAlarmsText = findViewById(R.id.noCurrentAlarmsText)
        noAllAlarmsText = findViewById(R.id.noAllAlarmsText)

        // 현재알림 RecyclerView 초기화
        currentAlarmAdapter = AlarmAdapter(currentAlarmList)
        currentAlarmRecyclerView.apply {
            adapter = currentAlarmAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            // (원하는 경우 XML의 layoutAnimation 속성을 적용할 수 있음)
        }

        // 전체알림 RecyclerView 초기화
        allAlarmAdapter = AlarmAdapter(allAlarmList)
        allAlarmRecyclerView.apply {
            adapter = allAlarmAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // 기타 UI 처리 (북마크, 추가 버튼, 일괄 정지 스위치)
        findViewById<ImageView>(R.id.bookmark).setOnClickListener {
            startActivity(Intent(this, BookmarkActivity::class.java))
        }
        findViewById<ImageView>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddList::class.java))
        }
        findViewById<Switch>(R.id.switch_all_stop).setOnCheckedChangeListener { _, isChecked ->
            updateCurrentAlarmsState(isChecked)
        }

        // 네비게이션(하단바) 설정 아이콘 클릭 시 SettingsActivity로 이동
        findViewById<ImageView>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }

        // 자정이면 북마크되지 않은 알람의 isDeleted 플래그 업데이트
        resetAlarmsAtMidnight()

        // Firebase 데이터 로드 후, 두 섹션에 배치
        loadAlarmsFromFirebase()

        // 두 RecyclerView에 스와이프 삭제 UI 적용 (삭제 시 isDeleted 업데이트 후 notifyItemChanged)
        attachSwipeHandler(currentAlarmRecyclerView, currentAlarmAdapter, currentAlarmList)
        attachSwipeHandler(allAlarmRecyclerView, allAlarmAdapter, allAlarmList)

        // 어댑터의 아이콘 클릭 이벤트 처리 (두 어댑터 모두 동일하게 MainActivity에서 처리)
        val itemClickListener = object : AlarmAdapter.OnItemClickListener {
            override fun onLightningClick(alarm: AlarmData, position: Int) {
                val newActiveStatus = !alarm.isActive
                // Firebase 업데이트
                database.child(alarm.id).child("isActive").setValue(newActiveStatus)
                    .addOnSuccessListener {
                        // 로컬 업데이트: 상태 변경 후 해당 알람을 리스트에서 서로 이동
                        alarm.isActive = newActiveStatus
                        if (newActiveStatus) {
                            // Lightning On → 해당 알람은 현재알림에 있어야 함
                            if (allAlarmList.remove(alarm)) {
                                currentAlarmList.add(alarm)
                            }
                        } else {
                            // Lightning Off → 해당 알람은 전체알림으로 이동
                            if (currentAlarmList.remove(alarm)) {
                                allAlarmList.add(alarm)
                            }
                        }
                        currentAlarmAdapter.notifyDataSetChanged()
                        allAlarmAdapter.notifyDataSetChanged()
                        // 추가로 loadAlarmsFromFirebase() 호출하여 서버와 동기화할 수도 있음
                    }
                    .addOnFailureListener {
                        loadAlarmsFromFirebase()
                    }
            }
            override fun onBookmarkClick(alarm: AlarmData, position: Int) {
                val newBookmarkStatus = !alarm.isBookmarked
                database.child(alarm.id).child("isBookmarked").setValue(newBookmarkStatus)
                    .addOnSuccessListener { loadAlarmsFromFirebase() }
                    .addOnFailureListener { loadAlarmsFromFirebase() }
            }
        }
        currentAlarmAdapter.setOnItemClickListener(itemClickListener)
        allAlarmAdapter.setOnItemClickListener(itemClickListener)
    }

    // 공통 스와이프 삭제 핸들러 (왼쪽 스와이프 시 isDeleted를 true로 업데이트한 후 notifyItemChanged)
    private fun attachSwipeHandler(
        recyclerView: RecyclerView,
        adapter: AlarmAdapter,
        alarmList: MutableList<AlarmData>
    ) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val alarm = alarmList[position]
                // Firebase 업데이트: isDeleted true
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
                dX: Float, dY: Float,
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

    // Firebase 데이터 로드 – 기존 데이터를 새로 분리하여 할당
    private fun loadAlarmsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentAlarmList.clear()
                allAlarmList.clear()
                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        if (alarm.id.isEmpty()) {
                            alarm.id = alarmSnapshot.key ?: ""
                        }
                        // isDeleted 또는 스와이프 처리된 항목은 제외
                        if (alarm.isDeleted || hiddenAlarmIds.contains(alarm.id)) continue
                        // 라이트닝(On) 상태이면 무조건 현재알림에, 그렇지 않으면 전체알림에 배치
                        if (alarm.isActive) {
                            currentAlarmList.add(alarm)
                        } else {
                            allAlarmList.add(alarm)
                        }
                    }
                }
                currentAlarmAdapter.notifyDataSetChanged()
                allAlarmAdapter.notifyDataSetChanged()
                // 스크롤 애니메이션 적용 (XML layoutAnimation이나 scheduleLayoutAnimation() 사용 가능)
                currentAlarmRecyclerView.scheduleLayoutAnimation()
                allAlarmRecyclerView.scheduleLayoutAnimation()
                updateNoAlarmsText()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // 안내 문구 업데이트 (데이터가 없으면 해당 TextView 표시)
    private fun updateNoAlarmsText() {
        noCurrentAlarmsText.visibility = if (currentAlarmList.isEmpty()) View.VISIBLE else View.GONE
        noAllAlarmsText.visibility = if (allAlarmList.isEmpty()) View.VISIBLE else View.GONE
    }

    // 자정이면 북마크되지 않은 알람의 isDeleted를 true로 업데이트
    private fun resetAlarmsAtMidnight() {
        val calendar = Calendar.getInstance()
        if (calendar.get(Calendar.HOUR_OF_DAY) == 0) {
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (alarmSnapshot in snapshot.children) {
                        val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                        if (alarm != null && !alarm.isBookmarked) {
                            alarmSnapshot.ref.child("isDeleted").setValue(true)
                        }
                    }
                    loadAlarmsFromFirebase()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // 스위치 토글 시 모든 알람의 isActive 업데이트 (일괄 정지)
    private fun updateCurrentAlarmsState(isStopped: Boolean) {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        alarmSnapshot.ref.child("isActive").setValue(!isStopped)
                    }
                }
                loadAlarmsFromFirebase()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // 알람 시간(ms) 계산 (alarmTimeMillis가 0이면 사용)
    private fun getAlarmTimeMillis(hour: Int, minute: Int, amPm: String): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        when {
            amPm == "PM" && hour < 12 -> calendar.set(Calendar.HOUR_OF_DAY, hour + 12)
            amPm == "AM" && hour == 12 -> calendar.set(Calendar.HOUR_OF_DAY, 0)
            else -> calendar.set(Calendar.HOUR_OF_DAY, hour)
        }
        calendar.set(Calendar.MINUTE, minute)
        return calendar.timeInMillis
    }
}
