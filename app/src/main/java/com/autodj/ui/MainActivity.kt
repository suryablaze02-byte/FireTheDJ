package com.autodj.ui

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MainActivity : ComponentActivity() {
    private val vm: DjViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A fresh launch opens upright for the splash, even with the phone held sideways.
        if (savedInstanceState == null) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Always edge to edge (under the bars and the camera cutout); the screens pad themselves with
        // Compose's insets, which follow every rotation. Switching this on and off for full screen
        // left stale landscape padding behind after rotating back.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        setContent {
            val accent = Color(0xFF7C5CFF)
            val colors = if (isSystemInDarkTheme()) {
                darkColorScheme(primary = accent, secondary = Color(0xFF26D9C4))
            } else {
                lightColorScheme(primary = accent, secondary = Color(0xFF0E9E8E))
            }
            MaterialTheme(colorScheme = colors) {
                // The splash shows once per launch, not again when the activity is recreated.
                var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                // The guided tour, once ever, right after the first splash.
                var tour by remember { mutableStateOf(!TourMemory.done(this)) }
                val tourState = remember { TourState() }
                // The splash and the guided tour (which points at the bottom tabs) stay upright;
                // after them the app turns with the phone.
                val upright = splash || tour
                LaunchedEffect(upright) {
                    requestedOrientation = if (upright) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
                CompositionLocalProvider(LocalTour provides tourState) {
                    DjScreen(vm)
                    if (splash) {
                        SplashScreen { splash = false }
                    } else if (tour) {
                        TourOverlay(tourState, PHONE_TOUR, tv = false) {
                            tour = false
                            TourMemory.markDone(this@MainActivity)
                        }
                    }
                }
            }
        }
    }

    /** Back from Android's "Install unknown apps" screen: an update waiting on it starts downloading. */
    override fun onResume() {
        super.onResume()
        com.autodj.update.Updates.resume(this)
    }
}
