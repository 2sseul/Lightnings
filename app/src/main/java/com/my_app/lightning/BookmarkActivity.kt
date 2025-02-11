package com.my_app.lightning

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.util.*

class BookmarkActivity : ComponentActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var btnAdd: ImageView

    private lateinit var uniqueUserId: String

    private val alarmList = mutableListOf<Pair<String, AlarmData>>() // 알람 리스트

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmark)

        uniqueUserId = UniqueIDManager.getInstance(applicationContext).getUniqueUserId()

        // Firebase 데이터베이스 참조
        database = FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child(uniqueUserId)

        // RecyclerView 초기화
        recyclerView = findViewById(R.id.currentAlarmRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        alarmAdapter = AlarmAdapter(this, alarmList, isGrayColor = true)
        recyclerView.adapter = alarmAdapter

        attachSwipeHandler(recyclerView)

        btnAdd = findViewById(R.id.btnAdd)
        btnAdd.setOnClickListener {
            startActivity(Intent(this, AddList::class.java))
        }
        findViewById<ImageView>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsAcivity::class.java))
        }

        loadBookmarkedAlarms()
        resetBookmarkedAlarmsAtMidnight() // 자정 이후 라이트닝 자동 ON 설정
    }

    private fun attachSwipeHandler(recyclerView: RecyclerView) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position in 0 until alarmList.size) {
                    val (alarmId, _) = alarmList[position]
                    if (alarmId.isEmpty()) {
                        alarmAdapter.notifyDataSetChanged()
                        return
                    }

                    database.child(alarmId).child("isDeleted").setValue(true)
                        .addOnSuccessListener {
                            loadBookmarkedAlarms()
                        }
                        .addOnFailureListener {
                            alarmAdapter.notifyDataSetChanged()
                        }
                } else {
                    alarmAdapter.notifyDataSetChanged()
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
                val deleteIcon = ContextCompat.getDrawable(this@BookmarkActivity, R.drawable.ic_delete)
                deleteIcon?.setBounds(
                    itemView.right - 100, itemView.top + 20,
                    itemView.right - 20, itemView.bottom - 20
                )
                deleteIcon?.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun loadBookmarkedAlarms() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<Pair<String, AlarmData>>()
                for (alarmSnapshot in snapshot.children) {
                    val key = alarmSnapshot.key
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    if (alarm != null && key != null) {
                        alarm.id = key
                        if (alarm.isBookmarked && !alarm.isDeleted) {
                            newList.add(Pair(key, alarm))
                        }
                    }
                }
1
                // 시간순 정렬: AM/PM 변환 후 24시간 기준 정렬
                val sortedList = newList.sortedWith(compareBy(
                    { if (it.second.amPm == "PM" && it.second.hour != 12) it.second.hour + 12 else if (it.second.amPm == "AM" && it.second.hour == 12) 0 else it.second.hour },
                    { it.second.minute }
                ))
                alarmAdapter.updateData(sortedList.toMutableList())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BookmarkActivity", "데이터 읽기 실패", error.toException())
            }
        })
    }

    private fun resetBookmarkedAlarmsAtMidnight() {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        // 자정 시간 설정 (오늘 24:00:00)
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1) // 이미 자정을 넘었으면 다음 날로 설정
            }
        }

        val resetTimeMillis = calendar.timeInMillis
        val delayMillis = resetTimeMillis - now

        Log.d("BookmarkActivity", "북마크 알람 리셋 예정: ${resetTimeMillis}, 현재시간: ${now}, 남은시간: ${delayMillis}ms")

        // 일정 시간 후 실행되도록 지연 실행
        android.os.Handler(mainLooper).postDelayed({
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (alarmSnapshot in snapshot.children) {
                        val key = alarmSnapshot.key
                        val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                        if (alarm != null && key != null && alarm.isBookmarked) {
                            // 모든 북마크된 알람의 lightningEnabled 값을 true로 변경
                            database.child(key).child("lightningEnabled").setValue(true)
                        }
                    }
                    loadBookmarkedAlarms() // 변경 후 다시 불러오기
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BookmarkActivity", "북마크된 알람 리셋 실패", error.toException())
                }
            })
        }, delayMillis)
    }
}
