package com.example.lightning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class TodoList(val idx:Int,
                    var isEditing:Boolean = false,
                    var hour:Int,
                    var minute:Int,
                    var todo:String,
                    var alarm:Boolean = false,
                    var remind:Boolean = false,
                    var bookmark:Boolean = false
)

@Composable
fun TodoListApp(){
    // 사용자가 추가한 투두리스트 중 현재 알림설정을 해 놓은 리스트
    var nowLists by remember{ mutableStateOf(listOf<TodoList>()) }
    // 사용자가 추가한 투두리스트 중 현재 알림설정을 해 놓지 않은 리스트 + 이미 울린 알림을 모아둔 리스트
    var allLists by remember { mutableStateOf(listOf<TodoList>()) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ){
        LazyColumn(
            modifier = Modifier
                .width(350.dp)
                .background(Color.Green)
        ){
            items(nowLists){

            }
        }
        LazyColumn(
            modifier = Modifier
                .width(350.dp)
                .background(Color.Red)
        ){
            items(allLists){

            }
        }
        Button(
            onClick = {},
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ){
            Text("Add List")
        }
    }
}