package com.autodj.dj

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Audio focus for the whole mix. Two decks play at once, so neither player handles focus
 * itself (the second would take it from the first); the engine holds it for both.
 *
 * Holding focus is what tells the system the mix is the media playing now: Android Auto routes
 * the focus holder to the car, and players without focus are muted when another app takes it.
 * Ducking under navigation prompts is left to the system.
 */
class MixFocus(context: Context, private val listener: Listener) {
    interface Listener {
        /** Another app took the audio: pause. [transient] means it should come back by itself. */
        fun onFocusLost(transient: Boolean)
        fun onFocusRegained()
    }

    private val audio = context.getSystemService(AudioManager::class.java)
    private var held = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(ATTRIBUTES)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(::onChange, Handler(Looper.getMainLooper()))
        .build()

    /** True when the mix may play now; false while a call or another app keeps the audio. */
    fun request(): Boolean {
        val result = audio.requestAudioFocus(request)
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i("AutoDJ", "audio focus requested: ${if (held) "granted" else "refused ($result)"}")
        return held
    }

    fun abandon() {
        if (!held) return
        audio.abandonAudioFocusRequest(request)
        held = false
    }

    private fun onChange(change: Int) {
        Log.i("AutoDJ", "audio focus change: $change")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                held = true
                listener.onFocusRegained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false
                listener.onFocusLost(transient = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> listener.onFocusLost(transient = true)
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: the system lowers the mix itself.
        }
    }

    companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
