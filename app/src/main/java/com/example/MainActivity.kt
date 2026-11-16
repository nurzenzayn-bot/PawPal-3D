package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.AvatarViewModel
import com.example.ui.screens.AvatarRoomScreen
import com.example.ui.theme.PawPalTheme

class MainActivity : ComponentActivity() {
    private val avatarViewModel: AvatarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            PawPalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    AvatarRoomScreen(viewModel = avatarViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also retries reminders that fired while Android notification permission was unavailable.
        com.example.notifications.ConversationReminderScheduler.rescheduleAll(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.example.util.configureAppCheck(this, intent)
    }
}
