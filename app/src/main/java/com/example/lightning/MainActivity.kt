package com.example.lightning

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
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

    // 현재알림 영역: 타입을 MutableList<Pair<String, AlarmData>>로 변경
    private val currentAlarmList = mutableListOf<Pair<String, AlarmData>>()
    // 전체알림 영역: 타입을 MutableList<Pair<String, AlarmData>>로 변경
    private val allAlarmList = mutableListOf<Pair<String, AlarmData>>()

    // 안내 문구 TextView (각 섹션)
    private lateinit var noCurrentAlarmsText: TextView
    private lateinit var noAllAlarmsText: TextView

    // (삭제된 항목을 중복 처리 방지를 위한 집합 – 필요시 사용)
    private val hiddenAlarmIds = mutableSetOf<String>()

    // RecyclerView와 어댑터 선언
    private lateinit var currentAlarmRecyclerView: RecyclerView
    private lateinit var currentAlarmAdapter: AlarmAdapter

    private lateinit var allAlarmRecyclerView: RecyclerView
    private lateinit var allAlarmAdapter: AlarmAdapter

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
        }

        // 전체알림 RecyclerView 초기화
        allAlarmAdapter = AlarmAdapter(allAlarmList)
        allAlarmRecyclerView.apply {
            adapter = allAlarmAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

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
            startActivity(Intent(this, SettingActivity::class.java))
        }

        resetAlarmsAtMidnight()
        loadAlarmsFromFirebase()

        // 스와이프 삭제 UI 적용
        attachSwipeHandler(currentAlarmRecyclerView, currentAlarmAdapter, currentAlarmList)
        attachSwipeHandler(allAlarmRecyclerView, allAlarmAdapter, allAlarmList)

        // 어댑터의 아이콘 클릭 이벤트 처리
        val itemClickListener = object : AlarmAdapter.OnItemClickListener {
            override fun onLightningClick(alarm: AlarmData, position: Int) {
                val newActiveStatus = !alarm.isActive
                database.child(alarm.id).child("isActive").setValue(newActiveStatus)
                    .addOnSuccessListener {
                        // 업데이트 후 서버와 동기화하거나 로컬 업데이트 후 리스트 이동 처리
                        loadAlarmsFromFirebase()
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
                // Pair의 두 번째 값(AlarmData)에서 id 사용
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

    private fun loadAlarmsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("Firebase", "데이터 스냅샷: ${snapshot.value}")
                currentAlarmList.clear()
                allAlarmList.clear()
                for (alarmSnapshot in snapshot.children) {
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null) {
                        if (alarm.id.isEmpty()) {
                            alarm.id = alarmSnapshot.key ?: ""
                        }
                        if (alarm.isDeleted || hiddenAlarmIds.contains(alarm.id)) continue
                        if (alarm.isActive) {
                            currentAlarmList.add(Pair(alarmSnapshot.key ?: "", alarm))
                        } else {
                            allAlarmList.add(Pair(alarmSnapshot.key ?: "", alarm))
                        }
                    }
                }
                currentAlarmAdapter.notifyDataSetChanged()
                allAlarmAdapter.notifyDataSetChanged()
                currentAlarmRecyclerView.scheduleLayoutAnimation()
                allAlarmRecyclerView.scheduleLayoutAnimation()
                updateNoAlarmsText()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateNoAlarmsText() {
        noCurrentAlarmsText.visibility = if (currentAlarmList.isEmpty()) View.VISIBLE else View.GONE
        noAllAlarmsText.visibility = if (allAlarmList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun resetAlarmsAtMidnight() {
        val calendar = Calendar.getInstance()
        if (calendar.get(Calendar.HOUR_OF_DAY) == 0) {
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("Firebase", "데이터 스냅샷: ${snapshot.value}")
                    for (alarmSnapshot in snapshot.children) {
                        val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                        if (alarm != null && !alarm.isBookmarked) {
                            alarmSnapshot.ref.child("isDeleted").setValue(true)
                        }
                    }
                    loadAlarmsFromFirebase()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "데이터 읽기 실패", error.toException())
                }
            })
        }
    }

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
