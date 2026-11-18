package com.example.geminidevagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geminidevagent.ui.AssistantScreen
import com.example.geminidevagent.ui.AssistantViewModel
import com.example.geminidevagent.ui.GeminiDevAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeminiDevAgentTheme {
                val vm: AssistantViewModel = viewModel()
                AssistantScreen(viewModel = vm)
            }
        }
    }
}
