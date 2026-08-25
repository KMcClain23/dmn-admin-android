package com.dmnarration.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnAdminTheme
import com.dmnarration.admin.ui.theme.ThemeProofScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity. Navigation lands here in 1.4 once there is more than one
 * screen to move between; right now it hosts the 1.3 theme proof so the design
 * system can be looked at on a real device before anything is built on it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DmnAdminTheme {
                Surface(color = Background, modifier = Modifier.fillMaxSize()) {
                    ThemeProofScreen(
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .consumeWindowInsets(WindowInsets.systemBars)
                    )
                }
            }
        }
    }
}
