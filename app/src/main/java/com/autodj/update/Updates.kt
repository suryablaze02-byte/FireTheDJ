package com.autodj.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.autodj.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A FireTheDJ release on GitHub that this app can update to. */
data class Release(val version: String, val notes: List<String>, val apkUrl: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    /** Android must first let FireTheDJ install apps ("Install unknown apps"). */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Downloading(val release: Release, val read: Long) : UpdateState
    /** Handed to Android's installer, which asks the user to confirm. */
    data class Installing(val release: Release) : UpdateState
    data class Failed(val reason: String, val release: Release?) : UpdateState
}

/**
 * In-app updates from the GitHub releases page: finds the latest release, picks this app's APK
 * (phone or TV) from it, downloads it straight into Android's package installer and lets the user
 * confirm. Only copies signed with the release key can update this way, as Android requires.
 */
object Updates {
    private const val REPO = "suryablaze02-byte/FireTheDJ"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    private const val PREFS = "updates"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** The version the user chose to install before Android asked for the install permission. */
    private const val PENDING = "pendingInstall"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private var download: Job? = null

    /** When the last check finished, in ms; 0 = never. */
    private val _lastChecked = MutableStateFlow(0L)
    val lastChecked: StateFlow<Long> = _lastChecked

    val current: String get() = BuildConfig.VERSION_NAME

    /** The APK in each release for this app: FireTheDJ-v1.2.0.apk or FireTheDJ-TV-v1.2.0.apk. */
    private val apkPrefix get() = if (BuildConfig.FLAVOR == "tv") "FireTheDJ-TV-v" else "FireTheDJ-v"

    fun autoCheckEnabled(context: Context) = prefs(context).getBoolean("auto", true)
    fun setAutoCheck(context: Context, on: Boolean) = prefs(context).edit().putBoolean("auto", on).apply()

    /** The version whose banner the user dismissed; it isn't shown again. */
    fun dismissed(context: Context): String? = prefs(context).getString("dismissed", null)
    fun dismiss(context: Context, version: String) = prefs(context).edit().putString("dismissed", version).apply()

    /** On launch: checks at most once a day, if automatic checks are on. */
    fun autoCheck(context: Context) {
        val p = prefs(context)
        _lastChecked.value = p.getLong("lastChecked", 0L)
        if (!autoCheckEnabled(context) || System.currentTimeMillis() - _lastChecked.value < DAY_MS) {
            // A known newer release from an earlier check is still worth offering.
            p.getString("latest", null)?.let { json -> runCatching { parse(JSONObject(json)) }.getOrNull() }
                ?.takeIf { newer(it.version, current) }?.let { if (_state.value == UpdateState.Idle) _state.value = UpdateState.Available(it) }
            resume(context)
            return
        }
        check(context)
    }

    fun check(context: Context) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        val app = context.applicationContext
        _state.value = UpdateState.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
            val now = System.currentTimeMillis()
            prefs(app).edit().putLong("lastChecked", now).apply()
            _lastChecked.value = now
            _state.value = result.fold(
                onSuccess = { (release, json) ->
                    prefs(app).edit().putString("latest", json).apply()
                    when {
                        release == null -> UpdateState.Failed("The latest release has no download for this app.", null)
                        newer(release.version, current) -> UpdateState.Available(release)
                        else -> UpdateState.UpToDate
                    }
                },
                onFailure = {
                    Log.w("AutoDJ", "update check failed", it)
                    UpdateState.Failed("Couldn't reach GitHub. Check the connection and try again.", null)
                },
            )
            resume(app)
        }
    }

    private fun canInstall(context: Context) =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /**
     * Carries on with an update that was waiting for the install permission, once it's allowed:
     * called when the app comes back to the front (from Android's permission screen) and when a
     * check finds the release again (Android may have restarted the app when the switch changed).
     */
    fun resume(context: Context) {
        val app = context.applicationContext
        if (!canInstall(app)) return
        val s = _state.value
        val waiting = (s as? UpdateState.NeedsPermission)?.release
            ?: (s as? UpdateState.Available)?.release?.takeIf { it.version == prefs(app).getString(PENDING, null) }
        if (waiting != null) install(app, waiting)
    }

    /** Downloads [release] into the installer and asks Android to install it. */
    fun install(context: Context, release: Release) {
        val app = context.applicationContext
        if (!canInstall(app)) {
            // Remembered in case Android restarts the app when the permission is switched on.
            prefs(app).edit().putString(PENDING, release.version).apply()
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        download?.cancel()
        prefs(app).edit().remove(PENDING).apply()
        _state.value = UpdateState.Downloading(release, 0)
        download = scope.launch {
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(app.packageName)
                if (release.sizeBytes > 0) setSize(release.sizeBytes)
            }
            val id = installer.createSession(params)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    installer.openSession(id).use { session ->
                        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 30_000
                            instanceFollowRedirects = true
                        }
                        conn.inputStream.use { input ->
                            session.openWrite("FireTheDJ.apk", 0, release.sizeBytes.takeIf { it > 0 } ?: -1).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var read = 0L
                                var lastShown = 0L
                                while (isActive) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    read += n
                                    if (read - lastShown > 256 * 1024) {
                                        lastShown = read
                                        _state.value = UpdateState.Downloading(release, read)
                                    }
                                }
                                session.fsync(out)
                            }
                        }
                        if (!isActive) return@runCatching false
                        val done = Intent(app, UpdateInstallReceiver::class.java)
                        // Mutable: the installer adds the status and, if needed, its confirm screen.
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                        session.commit(PendingIntent.getBroadcast(app, id, done, flags).intentSender)
                        true
                    }
                }.onFailure { Log.w("AutoDJ", "update download failed", it) }.getOrDefault(false)
            }
            if (ok) {
                _state.value = UpdateState.Installing(release)
            } else {
                runCatching { installer.abandonSession(id) }
                if (_state.value is UpdateState.Downloading) {
                    _state.value = UpdateState.Failed("The download didn't finish. Try again.", release)
                }
            }
        }
    }

    fun cancel() {
        download?.cancel()
        (_state.value as? UpdateState.Downloading)?.let { _state.value = UpdateState.Available(it.release) }
    }

    /** Android's "Install unknown apps" switch for FireTheDJ. */
    fun openInstallPermission(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        for (i in intents) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    /** The installer's verdict, from [UpdateInstallReceiver]. */
    internal fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate
            PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed(
                    "This copy of FireTheDJ wasn't installed from GitHub, so Android won't let it update itself. " +
                        "Uninstall it, then install the latest version from GitHub.",
                    release,
                )
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Not enough storage to install the update.", release)
            else -> UpdateState.Failed("Android couldn't install the update${message?.let { ": $it" } ?: "."}", release)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, 0)

    /** The latest release and its raw JSON (kept for offering it again without a network call). */
    private fun fetchLatest(): Pair<Release?, String> {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return parse(JSONObject(body)) to body
    }

    private fun parse(json: JSONObject): Release? {
        val version = json.getString("tag_name").removePrefix("v")
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").startsWith(apkPrefix) && it.getString("name").endsWith(".apk") } ?: return null
        return Release(version, notes(json.optString("body")), apk.getString("browser_download_url"), apk.optLong("size"))
    }

    /**
     * "What's new": the release notes' bullet points, without their Markdown. Notes pasted as
     * plain text have no bullets; then it's their summary, the first line after the title.
     */
    internal fun notes(body: String): List<String> {
        val lines = body.lines().map { it.trim() }
        val bullets = lines
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { line ->
                line.drop(2).replace("**", "").replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").substringBefore(": ").trim()
            }
            .filter { it.isNotBlank() }
            .take(6)
        if (bullets.isNotEmpty()) return bullets
        return listOfNotNull(lines.filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).firstOrNull())
    }

    /** Whether version [a] is newer than [b] ("1.10.0" > "1.9.2"). */
    internal fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
