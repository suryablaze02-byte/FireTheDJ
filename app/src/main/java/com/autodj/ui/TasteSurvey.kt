package com.autodj.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodj.analysis.Genres
import com.autodj.dj.Taste
import com.autodj.remote.YouTubeStreams
import com.autodj.remote.YtMusicSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The first-launch survey, three steps: languages, genres, artists. Its answers build the
 * listener's first set ("Made for you"). Settings › Your taste opens it again with the answers
 * filled in. Liquid glass on the app's ground, light or dark.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasteSurvey(initial: Taste.Survey?, onSkip: () -> Unit, onDone: (Taste.Survey) -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    val languages = remember { mutableStateListOf<String>().apply { addAll(initial?.languages ?: listOfNotNull(phoneLanguage())) } }
    val genres = remember { mutableStateListOf<String>().apply { addAll(initial?.genres.orEmpty()) } }
    val artists = remember { mutableStateListOf<Taste.Artist>().apply { addAll(initial?.artists.orEmpty()) } }
    BackHandler { if (step > 0) step-- else onSkip() }

    Box(Modifier.fillMaxSize().background(Glass.Ground)) {
        AmbientLight(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    GlassIconButton("Back", onClick = { step-- }) { Icon(Icons.Filled.KeyboardArrowLeft, null, tint = Glass.Text) }
                } else Spacer(Modifier.size(44.dp))
                Row(
                    Modifier.weight(1f).semantics { contentDescription = "Step ${step + 1} of 3" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(3) { i ->
                        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (i <= step) Glass.Ember else Glass.FillStrong))
                    }
                }
                Box(
                    Modifier.height(44.dp).floatingGlass(RoundedCornerShape(22.dp), Glass.Fill, elevation = 4.dp)
                        .clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Skip", color = Glass.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }

            val (title, sub) = when (step) {
                0 -> "Which languages do you party in?" to "Pick as many as you like. The DJ builds your first set from these."
                1 -> "What gets you dancing?" to "The same crates you mix from later."
                else -> "Who do you love?" to artistsSub(languages)
            }
            Text("STEP ${step + 1} OF 3", color = Glass.EmberText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 22.dp))
            Text(title, color = Glass.Text, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
            Text(sub, color = Glass.Muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LANGUAGES.forEach { l -> LanguageChip(l, l in languages) { if (l in languages) languages.remove(l) else languages.add(l) } }
                        }
                        Text("Your phone's language is picked for you to start.", color = Glass.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                    }
                    1 -> GENRES.chunked(2).forEach { row ->
                        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (g, detail) ->
                                GenreTile(g, detail, g in genres, Modifier.weight(1f)) { if (g in genres) genres.remove(g) else genres.add(g) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    else -> ArtistStep(languages, genres, artists)
                }
            }

            val label = when (step) {
                0 -> "Next" + count(languages.size, "language")
                1 -> "Next" + count(genres.size, "genre")
                else -> "Build my first set" + count(artists.size, "artist")
            }
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Glass.Fire)
                    .clickable(role = Role.Button) {
                        if (step < 2) step++ else onDone(Taste.Survey(languages.toList(), genres.toList(), artists.toList()))
                    },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Glass.OnAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun count(n: Int, what: String) = if (n == 0) "" else " · $n $what${if (n == 1) "" else "s"}"

private fun artistsSub(languages: List<String>): String {
    if (languages.isEmpty()) return "Search for anyone you like to dance to."
    val names = if (languages.size <= 2) languages.joinToString(" and ")
        else languages.take(languages.size - 1).joinToString(", ") + " and " + languages.last()
    return "Picked for $names. Search for anyone else."
}

@Composable
private fun LanguageChip(name: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.height(44.dp)
            .then(if (on) Modifier.clip(RoundedCornerShape(22.dp)).background(Glass.Ember) else Modifier.floatingGlass(RoundedCornerShape(22.dp), Glass.Fill, elevation = 2.dp))
            .toggleable(on, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = Glass.OnAccent, modifier = Modifier.size(16.dp))
        Text(name, color = if (on) Glass.OnAccent else Glass.Text, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun GenreTile(name: String, detail: String, on: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(84.dp)
            .floatingGlass(shape, if (on) Glass.FireSelected else Glass.Fill, elevation = 3.dp)
            .then(if (on) Modifier.border(2.dp, Glass.Ember, shape) else Modifier)
            .clip(shape)
            .toggleable(on, role = Role.Checkbox) { onToggle() }
            .padding(14.dp),
    ) {
        if (on) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(26.dp).background(Glass.Ember, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Glass.OnAccent, modifier = Modifier.size(15.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(name, color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Artists suggested for the picked languages and genres, the ones already picked, and a search for anyone else. */
@Composable
private fun ArtistStep(languages: List<String>, genres: List<String>, picked: MutableList<Taste.Artist>) {
    var query by rememberSaveable { mutableStateOf("") }
    val online = remember { YouTubeStreams.supported() }
    val results by produceState<List<Taste.Artist>?>(null, query) {
        val q = query.trim()
        if (q.length < 2 || !online) { value = null; return@produceState }
        delay(450)
        value = withContext(Dispatchers.IO) {
            runCatching { YtMusicSearch.artists(q, 9).map { Taste.Artist(it.name, it.photo) } }.getOrDefault(emptyList())
        }
    }
    Row(
        Modifier.fillMaxWidth().height(48.dp).floatingGlass(RoundedCornerShape(24.dp), Glass.Fill, elevation = 2.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = Glass.Muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search any artist", color = Glass.Muted, fontSize = 15.sp)
            BasicTextField(
                query, { query = it }, singleLine = true,
                textStyle = TextStyle(color = Glass.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(Glass.Ember),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search any artist" },
            )
        }
    }
    val shown = remember(languages, genres, results, picked.toList()) {
        val base = results ?: suggestedArtists(languages, genres).map { Taste.Artist(it, null) }
        val names = base.map { it.name.lowercase() }.toSet()
        // What's picked stays in view, even when a search shows others.
        (picked.filter { it.name.lowercase() !in names } + base).distinctBy { it.name.lowercase() }
    }
    if (results != null && results!!.isEmpty()) {
        Text("No artists found for \"${query.trim()}\".", color = Glass.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
    }
    shown.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            row.forEach { a ->
                val on = picked.any { it.name.equals(a.name, ignoreCase = true) }
                // Each circle belongs to its artist, so a new search never shows the last one's picture.
                key(a.name) {
                    ArtistCircle(a, on, online, Modifier.weight(1f)) { photo ->
                        if (on) picked.removeAll { it.name.equals(a.name, ignoreCase = true) } else picked.add(Taste.Artist(a.name, photo ?: a.photo))
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ArtistCircle(artist: Taste.Artist, on: Boolean, online: Boolean, modifier: Modifier, onToggle: (String?) -> Unit) {
    val photoUrl by produceState(artist.photo ?: photos[artist.name], artist.name, artist.photo) {
        value = artist.photo ?: photos[artist.name]
        if (value != null || !online) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                // The artist with this exact name, when the search has one; else its best match.
                val found = YtMusicSearch.artists(artist.name, 5)
                (found.firstOrNull { it.name.equals(artist.name, ignoreCase = true) } ?: found.firstOrNull())?.photo
            }.getOrNull()
        }?.also { photos[artist.name] = it }
    }
    val image = rememberRemoteImage(photoUrl)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).toggleable(on, role = Role.Checkbox) { onToggle(photoUrl) }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(Glass.FillStrong)
                    .then(if (on) Modifier.border(3.dp, Glass.Ember, CircleShape) else Modifier.border(1.dp, Glass.Edge, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(if (on) 3.dp else 0.dp).clip(CircleShape))
                else Text(initials(artist.name), color = Glass.Muted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (on) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).background(Glass.Ground, CircleShape).padding(2.dp).background(Glass.Ember, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = Glass.OnAccent, modifier = Modifier.size(14.dp)) }
            }
        }
        Text(artist.name, color = Glass.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).width(100.dp))
    }
}

private fun initials(name: String) = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/** Artist pictures looked up this session, so going back and forth doesn't search again. */
private val photos = ConcurrentHashMap<String, String>()

private val LANGUAGES = listOf(
    "Telugu", "English", "Hindi", "Tamil", "Kannada", "Malayalam", "Punjabi", "Bengali", "Marathi", "Gujarati",
    "Bhojpuri", "Spanish", "Portuguese", "French", "Arabic", "Korean", "Japanese",
)

private val GENRES = listOf(
    Genres.POP to "Tollywood, Bollywood, pop",
    Genres.HIPHOP to "Rap, R&B, trap, drill",
    Genres.HOUSE to "House, garage, disco",
    Genres.TECHNO to "Techno, minimal",
    Genres.TRANCE to "Trance, psy",
    Genres.LATIN to "Reggaeton, afro, amapiano",
    Genres.DNB to "DnB, jungle",
    Genres.BASS to "Dubstep, future bass",
    Genres.CHILL to "Lo-fi, lounge, ambient",
    Genres.ROCK to "Rock, indie, metal",
)

private val BY_LANGUAGE = mapOf(
    "Telugu" to listOf("Devi Sri Prasad", "Thaman S", "Sid Sriram", "Mangli", "Rahul Sipligunj", "Anurag Kulkarni"),
    "Tamil" to listOf("Anirudh Ravichander", "A.R. Rahman", "Yuvan Shankar Raja", "G. V. Prakash Kumar", "Sid Sriram"),
    "Hindi" to listOf("Arijit Singh", "Badshah", "Pritam", "Shreya Ghoshal", "Neha Kakkar", "Vishal-Shekhar"),
    "Punjabi" to listOf("Diljit Dosanjh", "AP Dhillon", "Karan Aujla", "Sidhu Moose Wala", "Guru Randhawa"),
    "English" to listOf("Dua Lipa", "The Weeknd", "Bruno Mars", "Calvin Harris", "David Guetta", "Taylor Swift"),
    "Kannada" to listOf("Arjun Janya", "Vijay Prakash", "Sanjith Hegde", "Charan Raj"),
    "Malayalam" to listOf("Sushin Shyam", "Vineeth Sreenivasan", "Shaan Rahman", "Dabzee"),
    "Bengali" to listOf("Anupam Roy", "Jeet Gannguli", "Shreya Ghoshal", "Arijit Singh"),
    "Marathi" to listOf("Ajay-Atul", "Adarsh Shinde"),
    "Gujarati" to listOf("Kinjal Dave", "Geeta Rabari"),
    "Bhojpuri" to listOf("Pawan Singh", "Khesari Lal Yadav"),
    "Spanish" to listOf("Bad Bunny", "Karol G", "J Balvin", "Shakira"),
    "Portuguese" to listOf("Anitta", "Alok", "Ludmilla"),
    "French" to listOf("DJ Snake", "Aya Nakamura", "Stromae"),
    "Arabic" to listOf("Amr Diab", "Mohamed Ramadan", "Elissa"),
    "Korean" to listOf("BTS", "BLACKPINK", "NewJeans", "Stray Kids"),
    "Japanese" to listOf("YOASOBI", "Kenshi Yonezu", "Ado"),
)

private val BY_GENRE = mapOf(
    Genres.HOUSE to listOf("Fisher", "Peggy Gou"),
    Genres.TECHNO to listOf("Charlotte de Witte", "Amelie Lens"),
    Genres.TRANCE to listOf("Armin van Buuren", "Above & Beyond"),
    Genres.DNB to listOf("Chase & Status", "Sub Focus"),
    Genres.BASS to listOf("Skrillex", "Illenium"),
    Genres.HIPHOP to listOf("Drake", "Travis Scott"),
    Genres.LATIN to listOf("Bad Bunny", "Daddy Yankee"),
    Genres.CHILL to listOf("Prateek Kuhad", "Lauv"),
    Genres.ROCK to listOf("Linkin Park", "Imagine Dragons"),
    Genres.POP to listOf("Dua Lipa", "Martin Garrix"),
)

/** About a dozen names: the picked languages' artists in turn, then a couple per genre. */
private fun suggestedArtists(languages: List<String>, genres: List<String>): List<String> {
    val lists = languages.mapNotNull { BY_LANGUAGE[it] }.ifEmpty { listOf(BY_LANGUAGE.getValue("English")) }
    val out = linkedSetOf<String>()
    var i = 0
    while (out.size < 12 && lists.any { i < it.size }) {
        lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        i++
    }
    genres.forEach { g -> BY_GENRE[g]?.let(out::addAll) }
    return out.take(15)
}

/** The phone's language, when it is one the app knows. */
private fun phoneLanguage(): String? = when (Locale.getDefault().language) {
    "te" -> "Telugu"; "ta" -> "Tamil"; "hi" -> "Hindi"; "kn" -> "Kannada"; "ml" -> "Malayalam"
    "bn" -> "Bengali"; "pa" -> "Punjabi"; "mr" -> "Marathi"; "gu" -> "Gujarati"; "es" -> "Spanish"
    "pt" -> "Portuguese"; "fr" -> "French"; "ar" -> "Arabic"; "ko" -> "Korean"; "ja" -> "Japanese"
    "en" -> "English"; else -> null
}
