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

class BookmarkActivity : ComponentActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var btnAdd: ImageView

    private lateinit var uniqueUserId: String

    // 하나의 리스트 인스턴스를 공유 (어댑터와 동일)
    private val alarmList = mutableListOf<Pair<String, AlarmData>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmark)

        uniqueUserId = UniqueIDManager.getInstance(applicationContext).getUniqueUserId()

        // Firebase 데이터베이스의 "alarms/test_user" 경로 참조
        database = FirebaseDatabase.getInstance().reference
            .child("alarms")
            .child(uniqueUserId)

        // RecyclerView 초기화 (레이아웃 파일의 RecyclerView ID가 currentAlarmRecyclerView)
        recyclerView = findViewById(R.id.currentAlarmRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // alarmList를 그대로 전달하여 어댑터와 공유
        alarmAdapter = AlarmAdapter(this, alarmList, isGrayColor = true)
        recyclerView.adapter = alarmAdapter

        // 단 하나의 ItemTouchHelper 부착 (중복 부착 제거)
        attachSwipeHandler(recyclerView)

        btnAdd = findViewById(R.id.btnAdd)
        btnAdd.setOnClickListener {
            startActivity(Intent(this, AddList::class.java))
        }
        findViewById<ImageView>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsAcivity::class.java))
        }

        loadBookmarkedAlarms()
    }

    // 단 하나의 ItemTouchHelper 부착
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
                    // 반드시 알람 키가 null 또는 빈 문자열이 아니어야 함.
                    if (alarmId.isEmpty()) {
                        Log.e("BookmarkActivity", "빈 alarmId를 발견했습니다. 삭제를 건너뜁니다.")
                        alarmAdapter.notifyDataSetChanged()
                        return
                    }
                    Log.d("BookmarkActivity", "스와이프 삭제 시 alarm id: $alarmId, position: $position")

                    // 삭제 동작: isDeleted 값을 true로 업데이트 (삭제 처리)
                    database.child(alarmId).child("isDeleted").setValue(true)
                        .addOnSuccessListener {
                            Log.d("BookmarkActivity", "Firebase에서 삭제 성공: $alarmId")
                            // 여기서는 로컬 리스트 업데이트 없이, 데이터베이스의 최신 상태를 다시 불러옵니다.
                            loadBookmarkedAlarms()
                        }
                        .addOnFailureListener {
                            Log.e("BookmarkActivity", "Firebase에서 삭제 실패: $alarmId")
                            alarmAdapter.notifyDataSetChanged()
                        }
                } else {
                    Log.e("BookmarkActivity", "onSwiped()에서 유효하지 않은 인덱스 접근: $position")
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

    private fun loadBookmarkedAlarms() {
        // 사용 후 리스너를 중복해서 붙이지 않도록 주의할 것(이 예제에서는 단순화를 위해 addValueEventListener 사용)
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<Pair<String, AlarmData>>()
                for (alarmSnapshot in snapshot.children) {
                    val key = alarmSnapshot.key
                    val alarm = alarmSnapshot.getValue(AlarmData::class.java)
                    // key가 null이 아니어야 함
                    if (alarm != null && key != null) {
                        alarm.id = key
                        // 북마크된 항목 중 삭제되지 않은 것만 추가
                        if (alarm.isBookmarked && !alarm.isDeleted) {
                            newList.add(Pair(key, alarm))
                        }
                    }
                }
                alarmAdapter.updateData(newList)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("BookmarkActivity", "데이터 읽기 실패", error.toException())
            }
        })
    }
}
