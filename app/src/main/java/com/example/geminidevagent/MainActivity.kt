package com.example.geminidevagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geminidevagent.ui.AssistantScreen
import com.example.geminidevagent.ui.AssistantViewModel
import com.example.geminidevagent.ui.GeminiDevAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AssistantViewModel = viewModel()
            val isLightMode by vm.isLightMode.collectAsState()

            LaunchedEffect(isLightMode) {
                val barStyle = if (isLightMode) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            GeminiDevAgentTheme(darkTheme = !isLightMode) {
                AssistantScreen(viewModel = vm)
            }
        }
    }
}
