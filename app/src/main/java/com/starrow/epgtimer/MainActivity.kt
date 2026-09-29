package com.starrow.epgtimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.starrow.epgtimer.ui.EpgTimerRoot
import com.starrow.epgtimer.ui.UiSettings
import com.starrow.epgtimer.ui.theme.EpgTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val activity = this
        val container = (application as EpgTimerApp).container
        val themeMode = UiSettings.themeMode(this)
        setContent {
            EpgTimerTheme(themeMode = themeMode) {
                EpgTimerRoot(
                    container = container,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        UiSettings.setThemeMode(activity, mode)
                        activity.recreate()
                    },
                )
            }
        }
    }
}
