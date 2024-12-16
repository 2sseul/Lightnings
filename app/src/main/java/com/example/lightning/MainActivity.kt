package com.example.lightning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lightning.ui.theme.LightningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LightningTheme {
                Surface (
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ){
                    var Lists by remember{ mutableStateOf(listOf<TodoList>()) }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ){
                        LazyColumn(
                            modifier = Modifier
                                .width(350.dp)
                                .background(Color.Red)
                        ){
                            items(Lists){

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
            }
        }
    }
}

data class TodoList(val idx:Int,
                    var isEditing:Boolean = false,
                    var hour:Int,
                    var minute:Int,
                    var todo:String,
                    var alarm:Boolean = false,
                    var remind:Boolean = false,
                    var bookmark:Boolean = false
)