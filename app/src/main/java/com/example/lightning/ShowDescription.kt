package com.example.lightning

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDescription(){
    // 사용자를 위해 ?를 누르면 나타나는 설명 팝업
    var showDescription by remember { mutableStateOf(false) }

    Column {
        if(showDescription){
            AlertDialog(onDismissRequest = {showDescription = false}){
                Text("번개 알림이란?")
                Text("일정 시간 후 알림창에서 사라지는 알림으로,")
                Text("알림이 사라지기 전에 일을 수행하세요!")
            }
        }
    }
}