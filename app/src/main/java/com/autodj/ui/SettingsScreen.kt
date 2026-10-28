package com.autodj.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autodj.support.Upi
import com.autodj.update.Changelog
import com.autodj.update.Release
import com.autodj.update.UpdateState
import com.autodj.update.Updates

/** Settings: updates, supporting FireTheDJ, and where it comes from. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by Updates.state.collectAsStateWithLifecycle()
    val checked by Updates.lastChecked.collectAsStateWithLifecycle()
    var auto by remember { mutableStateOf(Updates.autoCheckEnabled(context)) }
    var support by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    // Opening Settings with nothing known yet looks for an update straight away.
    LaunchedEffect(Unit) { if (state == UpdateState.Idle) Updates.check(context) }
    if (support) SupportSheet(onClose = { support = false })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            GlassIconButton("Back", onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Filled.KeyboardArrowLeft, null, tint = Glass.Text)
            }
            Text("Settings", color = Glass.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 16.dp))
            GlassHeading("Updates", Modifier.padding(start = 4.dp, bottom = 8.dp))
            UpdateCard(state, checked)
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(60.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Check for updates automatically", color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(
                    checked = auto,
                    onCheckedChange = { auto = it; Updates.setAutoCheck(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF7A2E), checkedThumbColor = Color.White),
                )
            }

            WhatsNew()

            GlassHeading("Support", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
            Row(
                Modifier.fillMaxWidth().height(64.dp).glass(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) { support = true }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF6B8A))
                Text("Support FireTheDJ", color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = Glass.Muted)
            }

            GlassHeading("About", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp))) {
                AboutRow("Source code on GitHub", "›") { open(context, "https://github.com/suryablaze02-byte/FireTheDJ") }
                AboutRow("License", "GPL-3.0", onClick = null)
                AboutRow("Version", Updates.current, onClick = null)
            }
        }
    }
}

@Composable
private fun UpdateCard(state: UpdateState, checkedAt: Long) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 18.dp)) {
        val release: Release? = when (state) {
            is UpdateState.Available -> state.release
            is UpdateState.NeedsPermission -> state.release
            is UpdateState.Downloading -> state.release
            is UpdateState.Installing -> state.release
            is UpdateState.Failed -> state.release
            else -> null
        }
        val (title, sub) = when (state) {
            UpdateState.Idle, UpdateState.Checking -> "Checking for updates…" to "You have ${Updates.current}"
            UpdateState.UpToDate -> "You're up to date" to "FireTheDJ ${Updates.current} is the latest version"
            is UpdateState.Available -> "Version ${state.release.version} is ready" to "You have ${Updates.current} · ${mb(state.release.sizeBytes)} download"
            is UpdateState.NeedsPermission -> "Allow installing updates" to
                "Android needs you to let FireTheDJ install apps. Turn it on, then come back and tap Download again."
            is UpdateState.Downloading -> "Downloading ${state.release.version}" to
                "${mb(state.read)} of ${mb(state.release.sizeBytes)} · the music keeps playing"
            is UpdateState.Installing -> "Installing ${state.release.version}" to
                "Android asks you to confirm. FireTheDJ restarts on the new version; your music and settings stay."
            is UpdateState.Failed -> "Update didn't work" to state.reason
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(Glass.Fire, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                DownloadGlyph(Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Glass.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = Glass.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (state is UpdateState.Available && state.release.notes.isNotEmpty()) {
            Text("WHAT'S NEW", color = Glass.EmberText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, modifier = Modifier.padding(top = 16.dp))
            state.release.notes.forEach { Text("•  $it", color = Glass.Text.copy(alpha = .88f), fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (state is UpdateState.Downloading) {
            val total = state.release.sizeBytes.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (state.read.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Glass.Ember, trackColor = Glass.FillSelected,
            )
        }
        when (state) {
            is UpdateState.Available -> FireButton("Download and install") { Updates.install(context, state.release) }
            is UpdateState.NeedsPermission -> FireButton("Allow installing apps") { Updates.openInstallPermission(context) }
            is UpdateState.Downloading -> GlassButton("Cancel") { Updates.cancel() }
            is UpdateState.Failed -> if (state.release != null) GlassButton("Get it from GitHub") { open(context, Updates.RELEASES_PAGE) }
            else -> Unit
        }
        if (release == null || state is UpdateState.Available) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(checkedLabel(checkedAt), color = Glass.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    "Check again", color = Glass.EmberText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { Updates.check(context) }.padding(vertical = 12.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

/** The banner on the Mix tab when a new version is out. */
@Composable
fun UpdateBanner(release: Release, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), Glass.FireSelected, SolidColor(Glass.Ember.copy(alpha = .75f)), 1.5.dp)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(Color.White.copy(alpha = .16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            DownloadGlyph(Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("FireTheDJ ${release.version} is out", color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(release.notes.firstOrNull() ?: "See what's new", color = Glass.Text.copy(alpha = .85f), fontSize = 13.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Box(
            Modifier.height(36.dp).background(Color.White, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("See it", color = Color(0xFF120A10), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss).semantics { contentDescription = "Dismiss" },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Glass.Muted, fontSize = 16.sp) }
    }
}

/**
 * Support over UPI: pick an amount (₹50 or more) and Android asks which UPI app to pay with.
 * The UPI ID can be copied for paying by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    var picked by rememberSaveable { mutableIntStateOf(100) }
    var other by rememberSaveable { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    val typed = other.toIntOrNull()
    val amount = if (other.isNotBlank()) typed else picked
    val valid = amount != null && amount >= Upi.MIN_RUPEES

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF241A20),
        contentColor = Glass.Text,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(Glass.Fire, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Favorite, null, tint = Color.White)
                }
                Text("Support FireTheDJ", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("FireTheDJ is free, with no ads. If it has played at your party, you can chip in over UPI.",
                color = Glass.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
            GlassHeading("Amount", Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Upi.AMOUNTS.forEach { a ->
                    val on = other.isBlank() && a == picked
                    Box(
                        Modifier.weight(1f).height(48.dp)
                            .glass(RoundedCornerShape(14.dp), if (on) Glass.FireSelected else Glass.Fill, if (on) SolidColor(Glass.Ember) else Glass.Edge, if (on) 1.5.dp else 1.dp)
                            .selectable(on, role = Role.RadioButton) { picked = a; other = ""; note = null },
                        contentAlignment = Alignment.Center,
                    ) { Text("₹$a", fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) }
                }
            }
            TextField(
                value = other,
                onValueChange = { v -> other = v.filter(Char::isDigit).take(6); note = null },
                label = { Text("Or your own amount (₹${Upi.MIN_RUPEES} or more)") },
                prefix = { Text("₹ ") },
                singleLine = true,
                isError = other.isNotBlank() && !valid,
                supportingText = if (other.isNotBlank() && !valid) {
                    { Text("The smallest amount is ₹${Upi.MIN_RUPEES}.") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Glass.Fill, unfocusedContainerColor = Glass.Fill, errorContainerColor = Glass.Fill,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
                    focusedTextColor = Glass.Text, unfocusedTextColor = Glass.Text, cursorColor = Glass.Ember,
                    focusedLabelColor = Glass.Muted, unfocusedLabelColor = Glass.Muted,
                    focusedPrefixColor = Glass.Muted, unfocusedPrefixColor = Glass.Muted,
                ),
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
            )
            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .then(if (valid) Modifier.background(Glass.Fire) else Modifier.glass(RoundedCornerShape(28.dp)))
                    .clickable(enabled = valid, role = Role.Button) { note = pay(context, amount!!) },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (valid) "Pay ₹$amount with a UPI app" else "Pick ₹${Upi.MIN_RUPEES} or more", color = if (valid) Color.White else Glass.Muted,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text(note ?: "Android asks which app: GPay, PhonePe, Paytm, BHIM or any UPI app.", color = if (note != null) Dj.Warn else Glass.Muted,
                fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Upi.NAME, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(Upi.ID, color = Glass.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Box(
                    Modifier.height(44.dp).glass(RoundedCornerShape(22.dp), Glass.FillStrong).clickable(role = Role.Button) { copyId(context) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Copy UPI ID", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Opens Android's "Pay with" chooser on the UPI link; returns a message if no UPI app can take it. */
private fun pay(context: Context, rupees: Int): String? {
    val view = Intent(Intent.ACTION_VIEW, Upi.link(rupees))
    if (view.resolveActivity(context.packageManager) == null) return "No UPI app found. Copy the UPI ID below and pay from any UPI app."
    return try {
        context.startActivity(Intent.createChooser(view, "Pay ₹$rupees with"))
        null
    } catch (_: ActivityNotFoundException) {
        "No UPI app found. Copy the UPI ID below and pay from any UPI app."
    }
}

private fun copyId(context: Context) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("UPI ID", Upi.ID))
    Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
}

/** Every change in the installed version, then earlier versions, each opened with a tap. */
@Composable
private fun WhatsNew() {
    val current = Changelog.of(Updates.current) ?: Changelog.versions.first()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    GlassHeading("What's new in ${current.version}", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(18.dp)) {
        VersionChanges(current)
    }
    val earlier = Changelog.versions.filter { it !== current }
    if (earlier.isEmpty()) return
    GlassHeading("Earlier versions", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp))) {
        earlier.forEach { v ->
            val expanded = open == v.version
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (expanded) null else v.version }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Version ${v.version}", color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(v.summary, color = Glass.Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = if (expanded) 3 else 1)
                }
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Glass.Muted)
            }
            if (expanded) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { VersionChanges(v, summary = false) }
        }
    }
}

@Composable
private fun VersionChanges(v: Changelog.Version, summary: Boolean = true) {
    if (summary) Text(v.summary, color = Glass.Text.copy(alpha = .88f), fontSize = 14.sp, lineHeight = 20.sp)
    v.sections.forEachIndexed { i, section ->
        Text(section.title.uppercase(), color = Glass.EmberText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp,
            modifier = Modifier.padding(top = if (i == 0 && !summary) 4.dp else 16.dp))
        section.changes.forEach { c ->
            Text(c.title, color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(c.detail, color = Glass.Text.copy(alpha = .78f), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Glass.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Glass.Muted, fontSize = 15.sp)
    }
}

@Composable
private fun FireButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 18.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Glass.Fire)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GlassButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp).glass(RoundedCornerShape(24.dp), Glass.FillStrong)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0)

private fun checkedLabel(at: Long): String {
    if (at == 0L) return "Not checked yet"
    val min = (System.currentTimeMillis() - at) / 60_000
    return when {
        min < 1 -> "Checked just now"
        min < 60 -> "Checked $min min ago"
        min < 24 * 60 -> "Checked ${min / 60} h ago"
        else -> "Checked ${min / (24 * 60)} days ago"
    }
}
