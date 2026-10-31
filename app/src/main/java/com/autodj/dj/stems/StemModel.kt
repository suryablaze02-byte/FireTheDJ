package com.autodj.dj.stems

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The separation model: Demucs htdemucs (MIT), exported for 3.9 s chunks. It isn't in the app;
 * it's downloaded once, on request, from FireTheDJ's GitHub release, checked against its
 * checksum, and kept in the app's own storage, where updates leave it alone.
 */
object StemModel {
    const val FILE = "htdemucs-short-v1.onnx"
    const val URL = "https://github.com/suryablaze02-byte/FireTheDJ/releases/download/stems-v1/$FILE"
    const val SIZE = 171_416_005L
    private const val SHA256 = "151345d2459470b41f0b252b59cd06e6a355b7eb14b038ad4dec1fdc95de064e"

    sealed interface State {
        data object Absent : State
        data class Downloading(val bytes: Long) : State
        data object Ready : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Absent)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun file(context: Context) = File(context.getExternalFilesDir("models") ?: File(context.filesDir, "models"), FILE)

    /** Brings [state] in line with what's on disk (call when the screen showing it opens). */
    fun refresh(context: Context) {
        if (_state.value is State.Downloading) return
        _state.value = if (file(context).exists()) State.Ready else State.Absent
    }

    /** On mobile data (or a metered hotspot): the screen says so before a 170 MB download. */
    fun metered(context: Context) = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

    fun download(context: Context) {
        if (job?.isActive == true) return
        val target = file(context)
        job = scope.launch {
            val part = File(target.parentFile, "$FILE.part")
            try {
                target.parentFile?.mkdirs()
                _state.value = State.Downloading(0)
                val digest = MessageDigest.getInstance("SHA-256")
                var conn = URL(URL).openConnection() as HttpURLConnection
                // GitHub sends release files from another host; follow it (and only https).
                var hops = 0
                while (conn.responseCode in 300..399 && hops++ < 5) {
                    val next = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    val url = URL(URL(URL), next)
                    if (url.protocol != "https") throw IOException("refused a non-https redirect")
                    conn = url.openConnection() as HttpURLConnection
                }
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                var got = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            got += n
                            _state.value = State.Downloading(got)
                        }
                    }
                }
                conn.disconnect()
                if (!isActive) { part.delete(); _state.value = State.Absent; return@launch }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (sha != SHA256) throw IOException("the download was damaged (checksum mismatch); try again")
                if (!part.renameTo(target)) throw IOException("couldn't save the model")
                // It was downloaded to be used: stems go on.
                Stems.setOn(context, true)
                _state.value = State.Ready
            } catch (e: Exception) {
                part.delete()
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Frees the space: the model goes, and the switch with it. */
    fun delete(context: Context) {
        cancel()
        file(context).delete()
        File(file(context).parentFile, "$FILE.part").delete()
        Stems.setOn(context, false)
        _state.value = State.Absent
    }
}
