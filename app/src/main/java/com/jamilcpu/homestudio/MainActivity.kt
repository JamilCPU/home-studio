package com.jamilcpu.homestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeStudioTheme {
                Text("Home Studio")
            }
        }
    }
}
