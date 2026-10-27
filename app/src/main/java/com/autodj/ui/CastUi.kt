package com.autodj.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.autodj.cast.CastControl
import com.autodj.cast.CastReceiver
import com.autodj.cast.CastStatus
import com.autodj.cast.CastStreamService
import com.google.android.gms.cast.CastMediaControlIntent

/** The cast button: a screen with waves; lit while the mix is on a TV. */
@Composable
fun CastButton(casting: Boolean, diameter: Dp = 40.dp, onClick: () -> Unit) {
    val tint = if (casting) Glass.Ember else Glass.Text
    Box(
        Modifier.size(diameter).glass(CircleShape, if (casting) Glass.FireSelected else Glass.Fill)
            .clickable(role = Role.Button, onClickLabel = if (casting) "Casting: change or stop" else "Play on a TV", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.48f).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val w = size.width
            val s = w * 0.1f
            // The screen, open at the bottom-left where the waves come in.
            drawRoundRect(tint, Offset(w * 0.12f, w * 0.12f), Size(w * 0.82f, w * 0.64f), CornerRadius(w * 0.08f), style = Stroke(s))
            // Gap in the screen's corner for the waves, cut out rather than painted over (the button is see-through).
            drawRect(Color.Transparent, Offset(0f, w * 0.48f), Size(w * 0.42f, w * 0.52f), blendMode = BlendMode.Clear)
            for (i in 1..2) {
                val r = w * 0.17f * i
                drawArc(tint, 270f, 90f, false, Offset(w * 0.06f - r, w * 0.94f - r), Size(r * 2, r * 2), style = Stroke(s, cap = StrokeCap.Round))
            }
            drawCircle(tint, w * 0.07f, Offset(w * 0.08f, w * 0.92f))
        }
    }
}

/** Whether any TV is showing the mix: a Chromecast stream, or a wireless display. */
@Composable
fun rememberCasting(): Boolean {
    val status by CastControl.status.collectAsStateWithLifecycle()
    return status is CastStatus.Streaming || tvScreenConnected()
}

@Composable
private fun tvScreenConnected(): Boolean {
    val context = LocalContext.current
    val displays = remember { context.getSystemService(DisplayManager::class.java) }
    fun connected() = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()
    var on by remember { mutableStateOf(connected()) }
    DisposableEffect(Unit) {
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { on = connected() }
            override fun onDisplayRemoved(id: Int) { on = connected() }
            override fun onDisplayChanged(id: Int) { on = connected() }
        }
        displays.registerDisplayListener(l, null)
        onDispose { displays.unregisterDisplayListener(l) }
    }
    return on
}

/**
 * Picks where to cast: Chromecasts and Google TVs found on the Wi-Fi (the phone streams the mix
 * to them), or a wireless display (Miracast, Smart View), which Android connects from its own
 * screen-cast settings and FireTheDJ then fills with the visualizer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val status by CastControl.status.collectAsStateWithLifecycle()
    val tvScreen = tvScreenConnected()
    val cast = remember { CastControl.init(context) }
    val router = remember { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(CastReceiver.appId)).build()
    }
    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    // Searches only while the dialog is open.
    DisposableEffect(cast) {
        fun refresh() {
            routes = router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            // Everything discovery sees, and why a device isn't offered: what to read when a TV is missing.
            android.util.Log.i(
                "AutoDJ",
                "cast routes: " + router.routes.joinToString { r ->
                    "${r.name} [${r.providerInstance.metadata.packageName}" +
                        (if (r.isDefault) ", default" else "") + (if (!r.isEnabled) ", disabled" else "") +
                        (if (!r.matchesSelector(selector)) ", no cast" else "") + "]"
                },
            )
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        }
        if (cast != null) {
            router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            refresh()
        }
        onDispose { router.removeCallback(callback) }
    }
    val streaming = status as? CastStatus.Streaming

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // A solid smoky base, so the screen behind never shows through the words; the rows on it are glass.
        containerColor = Color(0xFF241A20),
        contentColor = Glass.Text,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Play on a TV", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("The TV shows the visualizers full screen. Your phone stays the remote.", color = Glass.Muted, fontSize = 14.sp)
            GlassHeading("Chromecast & Google TV", Modifier.padding(top = 8.dp))
            when {
                cast == null -> Text("Needs Google Play services.", fontSize = 14.sp, color = Dj.Warn)
                routes.isEmpty() -> Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Glass.Ember)
                    Text("Looking on this Wi-Fi…", fontSize = 15.sp)
                }
                else -> routes.forEach { route ->
                    val selected = route.isSelected
                    DeviceRow(route.name, if (selected) "Casting · tap to stop" else "Chromecast · plays the live mix", selected) {
                        if (selected) CastControl.stop() else router.selectRoute(route)
                        onClose()
                    }
                }
            }
            GlassHeading("Any smart TV", Modifier.padding(top = 8.dp))
            DeviceRow(
                if (tvScreen) "TV screen connected" else "Screen cast",
                if (tvScreen) "Showing the visualizer · stop it from screen cast" else "Miracast or Smart View",
                tvScreen,
            ) {
                openScreenCast(context)
                onClose()
            }
            (status as? CastStatus.Failed)?.let { Text(it.reason, color = Dj.Warn, fontSize = 14.sp) }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlameGlyph(Glass.Ember)
                Text("Have an Android TV? Install FireTheDJ TV and it DJs on its own.", color = Glass.Muted, fontSize = 13.sp)
            }
            Box(
                Modifier.padding(top = 6.dp).fillMaxWidth().height(52.dp).glass(RoundedCornerShape(26.dp), Glass.FillStrong)
                    .clickable(role = Role.Button) { if (streaming != null) CastControl.stop(); onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (streaming != null) "Stop casting" else "Cancel", color = if (streaming != null) Dj.Warn else Glass.Text,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DeviceRow(name: String, detail: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), if (on) Glass.FireSelected else Glass.Fill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = if (on) Glass.Ember else Glass.Muted, fontSize = 13.sp)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Glass.Muted)
    }
}

/** Android's own screen-cast picker (Miracast); Motorola's Smart Connect answers it on Moto phones. */
private fun openScreenCast(context: android.content.Context) {
    val intents = listOf(Intent(Settings.ACTION_CAST_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
    for (i in intents) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/**
 * When a Chromecast connects, asks to capture FireTheDJ's audio (the microphone permission
 * covers capturing app audio, then Android's own "start casting?" prompt) and starts the stream.
 */
@UnstableApi
@Composable
fun CastCapturePrompts(artworkFor: (String) -> String?) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        CastControl.init(context)
        CastControl.artworkFor = artworkFor
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val status by CastControl.status.collectAsStateWithLifecycle()
    val device = (status as? CastStatus.NeedsCapture)?.device
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CastStreamService.start(context, result.resultCode, data, device.orEmpty())
        } else {
            CastControl.onCaptureDenied()
        }
    }
    val askProjection = {
        projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askProjection() else CastControl.onCaptureDenied()
    }
    LaunchedEffect(device) {
        if (device == null) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            askProjection()
        } else {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
