package com.autodj.cast

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.util.UnstableApi
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.autodj.dj.MixEngine
import com.autodj.ui.Dj
import com.autodj.ui.vis.AmbientVisualizer
import com.autodj.ui.vis.VisualizerStyle
import kotlinx.coroutines.flow.StateFlow

/**
 * While the phone is connected to a TV as a wireless display (Miracast, Smart View, Moto Smart
 * Connect, or anything else Android treats as a presentation screen), the TV shows the picked
 * visualizer full screen, in the TV's own landscape, instead of a mirror of the phone. The
 * system carries the audio there with the picture. The phone keeps FireTheDJ's controls.
 */
@UnstableApi
@Composable
fun TvPresentation(engine: MixEngine, style: StateFlow<VisualizerStyle>) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    var display by remember { mutableStateOf(presentationDisplay(activity)) }
    // Follows TVs connecting and disconnecting.
    DisposableEffect(Unit) {
        val manager = activity.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { display = presentationDisplay(activity) }
            override fun onDisplayRemoved(id: Int) { display = presentationDisplay(activity) }
            override fun onDisplayChanged(id: Int) { display = presentationDisplay(activity) }
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    val target = display ?: return
    DisposableEffect(target.displayId) {
        val screen = VisualizerPresentation(activity, target, engine, style)
        runCatching { screen.show() }
        onDispose { screen.dismiss() }
    }
}

/** The external screen to present on, if one is connected. */
private fun presentationDisplay(context: Context): Display? =
    context.getSystemService(DisplayManager::class.java)
        .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.isValid }

@UnstableApi
private class VisualizerPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val engine: MixEngine,
    private val style: StateFlow<VisualizerStyle>,
) : Presentation(activity, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Compose looks these up from the view tree; the presentation lives as long as the activity.
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
        }
        setContentView(
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setContent { TvVisualizer(engine, style) }
            },
        )
    }
}

/** The TV picture: the visualizer edge to edge, with the track in a corner. */
@UnstableApi
@Composable
private fun TvVisualizer(engine: MixEngine, styleFlow: StateFlow<VisualizerStyle>) {
    val state by engine.state.collectAsStateWithLifecycle()
    val style by styleFlow.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AmbientVisualizer(engine::visual, style, Modifier.fillMaxSize())
        val deck = state.current
        if (deck != null) {
            Column(Modifier.align(Alignment.BottomStart).padding(40.dp)) {
                Text(deck.title, color = Dj.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val sub = listOfNotNull(deck.artist.takeIf { it.isNotBlank() }, "%.0f BPM".format(deck.bpm), "FireTheDJ")
                Text(sub.joinToString(" · "), color = Dj.Muted, fontSize = 18.sp, maxLines = 1)
            }
        } else {
            Text(
                "FireTheDJ · start a mix on the phone", color = Dj.Muted, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
