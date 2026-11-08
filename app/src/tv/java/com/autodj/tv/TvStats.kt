package com.autodj.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autodj.dj.ListeningStats
import com.autodj.dj.PlayHistory
import com.autodj.dj.formatHeard
import com.autodj.dj.listeningStats
import com.autodj.ui.Glass
import com.autodj.ui.StatsCover
import com.autodj.ui.StatsRange
import com.autodj.ui.drawRecord
import com.autodj.ui.glass
import com.autodj.ui.sinceMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Your stats on one screen with no scrolling; Back closes it. */
@Composable
fun TvStatsScreen(artwork: Map<String, String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var range by remember { mutableStateOf(StatsRange.MONTH) }
    val first = remember { FocusRequester() }
    val stats by produceState<ListeningStats?>(null, range) {
        value = withContext(Dispatchers.IO) { listeningStats(PlayHistory(context.applicationContext).all(), range.sinceMs()) }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Glass.Ground).padding(horizontal = 36.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Your stats", color = Glass.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                StatsRange.entries.forEachIndexed { i, r ->
                    TvCard(if (r == StatsRange.MONTH) "Last 30 days" else r.label, null, Modifier.width(170.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier), selected = r == range) { range = r }
                }
            }
            val s = stats
            if (s != null && s.sets == 0) {
                Text("Nothing here yet. Start a mix and your stats build up as you play.", color = Glass.Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 24.dp))
            } else if (s != null) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    TvHero(s, Modifier.width(340.dp).fillMaxHeight())
                    TvArtists(s, Modifier.weight(1f).fillMaxHeight())
                    TvSongs(s, artwork, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TvHero(s: ListeningStats, modifier: Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.clip(shape).glass(shape, Glass.Fill)
            .drawBehind { drawRecord(Offset(size.width - 30.dp.toPx(), 30.dp.toPx()), 110.dp.toPx(), Glass.Ember, Color(0xFF1B1B1F), Color(0x331B1B1F)) }
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            val hours = s.heardSec / 3600
            Text(if (hours > 0) "$hours h" else formatHeard(s.heardSec), color = Glass.Text, fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp, maxLines = 1)
            Text("mixed", color = Glass.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            Column {
                Text("${s.sets}", color = Glass.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (s.sets == 1) "set played" else "sets played", color = Glass.Muted, fontSize = 15.sp)
            }
            Column {
                Text(formatHeard(s.longestSec), color = Glass.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("longest set", color = Glass.Muted, fontSize = 15.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Crate" to s.crate, "Genre" to s.genre, "Language" to s.language).forEach { (label, value) ->
                if (value != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label, color = Glass.Muted, fontSize = 16.sp)
                    Text(value, color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TvArtists(s: ListeningStats, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Top artists", color = Glass.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        val top = (s.artists.firstOrNull()?.plays ?: 1).toFloat()
        s.artists.forEachIndexed { i, a ->
            Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(Glass.Fill)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(a.plays / top).background(Glass.Ember.copy(alpha = 0.45f), RoundedCornerShape(14.dp)))
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(a.name, color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${a.plays}", color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TvSongs(s: ListeningStats, artwork: Map<String, String>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Most-played songs", color = Glass.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        s.songs.forEach { song ->
            Row(Modifier.height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCover(artwork[song.uri], 46.dp, RoundedCornerShape(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.name, color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Glass.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${song.plays}", color = Glass.Muted, fontSize = 17.sp)
            }
        }
    }
}
