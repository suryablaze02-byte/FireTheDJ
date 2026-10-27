package com.autodj.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.support.Upi
import com.autodj.ui.Glass
import com.autodj.ui.songs
import com.autodj.update.UpdateState
import com.autodj.update.Updates
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** The update card in the TV's Settings: what's there to do, and OK does it. */
@Composable
fun TvUpdateCard(state: UpdateState, modifier: Modifier) {
    val context = LocalContext.current
    val (title, sub) = when (state) {
        UpdateState.Idle, UpdateState.Checking -> "Checking for updates…" to "FireTheDJ TV ${Updates.current}"
        UpdateState.UpToDate -> "You're up to date" to "FireTheDJ TV ${Updates.current} · OK checks again"
        is UpdateState.Available -> "Update to ${state.release.version}" to
            listOfNotNull("You have ${Updates.current}", mb(state.release.sizeBytes), state.release.notes.firstOrNull()).joinToString(" · ")
        is UpdateState.NeedsPermission -> "Allow installing updates" to "Let FireTheDJ TV install apps in the TV's settings, then come back"
        is UpdateState.Downloading -> "Downloading ${state.release.version}" to "${mb(state.read)} of ${mb(state.release.sizeBytes)} · OK cancels"
        is UpdateState.Installing -> "Installing ${state.release.version}" to "Confirm on screen. The music, crates and settings stay."
        is UpdateState.Failed -> "Update didn't work" to state.reason
    }
    TvCard(
        title, sub, modifier,
        selected = state is UpdateState.Available,
        icon = { Box(Modifier.size(46.dp).background(Glass.Fire, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { com.autodj.ui.DownloadGlyph(Color.White, 24.dp) } },
    ) {
        when (state) {
            is UpdateState.Available -> Updates.install(context, state.release)
            is UpdateState.NeedsPermission -> Updates.openInstallPermission(context)
            is UpdateState.Downloading -> Updates.cancel()
            is UpdateState.Installing, UpdateState.Checking -> Unit
            else -> Updates.check(context)
        }
    }
}

/** Support on the TV: pick an amount and scan the UPI code with a phone. */
@Composable
fun TvSupportDialog(onDismiss: () -> Unit) {
    var rupees by remember { mutableIntStateOf(100) }
    val qr = remember(rupees) { qrCode(Upi.link(rupees).toString()) }
    TvGlassDialog(onDismiss = onDismiss, width = 760.dp) { first ->
        Row(horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            Column(Modifier.width(380.dp)) {
                Text("Support FireTheDJ", color = Glass.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = TV_EDGE))
                Text("Free, with no ads. If it has played at your party, scan the code with any UPI app on your phone.",
                    color = Glass.Muted, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(start = TV_EDGE, top = 8.dp, bottom = 14.dp))
                // Two rows of two amounts; the code follows the one picked.
                Upi.AMOUNTS.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = TV_EDGE, vertical = 8.dp)) {
                        pair.forEach { a ->
                            val focus = if (row == 0 && a == Upi.AMOUNTS[1]) Modifier.focusRequester(first) else Modifier
                            TvCard("₹$a", null, Modifier.width(160.dp).then(focus), selected = a == rupees) { rupees = a }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TvCard("Done", null, Modifier.padding(start = TV_EDGE).width(140.dp), onClick = onDismiss)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    qr, null,
                    Modifier.size(250.dp).background(Color.White, RoundedCornerShape(22.dp)).padding(14.dp)
                        .semantics { contentDescription = "UPI code to pay ₹$rupees to ${Upi.NAME}" },
                )
                Text("₹$rupees", color = Glass.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp))
                Text(Upi.NAME, color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Text(Upi.ID, color = Glass.Muted, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** The TV's Settings: updates, support, and clearing the library. */
@Composable
fun TvSettingsDialog(
    state: UpdateState,
    librarySize: Int,
    onSupport: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvGlassDialog(onDismiss = onDismiss, width = 640.dp) { first ->
        Text("Settings", color = Glass.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = TV_EDGE))
        TvUpdateCard(state, Modifier.padding(horizontal = TV_EDGE, vertical = 12.dp).fillMaxWidth().focusRequester(first))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(horizontal = TV_EDGE, vertical = 6.dp)) {
            TvCard("Support FireTheDJ", "Chip in over UPI", Modifier.width(260.dp), onClick = onSupport)
            TvCard(
                "Clear library",
                if (librarySize == 0) "The library is empty" else "Remove all ${songs(librarySize)}",
                Modifier.width(260.dp), titleColor = com.autodj.ui.Dj.Warn, onClick = onClear,
            )
        }
        Row(Modifier.padding(start = TV_EDGE, top = 8.dp, end = TV_EDGE), verticalAlignment = Alignment.CenterVertically) {
            TvCard("Done", null, Modifier.width(140.dp), onClick = onDismiss)
            Text("FireTheDJ TV ${Updates.current}", color = Glass.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp))
        }
    }
}

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0)

/** A QR code for [text], black on white, with a quiet border. */
private fun qrCode(text: String, px: Int = 480): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(px * px) { i -> if (matrix[i % px, i / px]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
}
