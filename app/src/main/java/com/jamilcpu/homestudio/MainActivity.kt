package com.jamilcpu.homestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jamilcpu.homestudio.ui.HomeStudioRoot
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HomeStudioApp).container
        setContent {
            HomeStudioTheme {
                HomeStudioRoot(container)
            }
        }
    }
}
