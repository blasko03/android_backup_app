package dev.danielblasina.androidbackup

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.danielblasina.androidbackup.ui.screens.MainScreen
import dev.danielblasina.androidbackup.ui.screens.Screen
import dev.danielblasina.androidbackup.ui.screens.SettingsScreen
import dev.danielblasina.androidbackup.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Environment.isExternalStorageManager()) {
            // Permission granted
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }

        enableEdgeToEdge()
        setContent {
            MainApplication()
        }
    }

    @Composable
    private fun MainApplication() {
        var screen by remember { mutableStateOf(Screen.Main) }

        MyApplicationTheme {
            when (screen) {
                Screen.Settings -> SettingsScreen(
                    context = applicationContext,
                    onBack = { screen = Screen.Main },
                )

                Screen.Main -> MainScreen(
                    onNavigateToSettings = { screen = Screen.Settings },
                    context = applicationContext,
                )
            }
        }
    }
}
