package com.autodj.dj

import android.content.Context
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.MainScope

/**
 * One engine per process, shared by the UI and the playback service, so a set keeps going
 * when the activity is destroyed (screen off, app swiped away from recents while playing).
 */
@UnstableApi
object EngineHolder {
    private var engine: MixEngine? = null

    fun get(context: Context): MixEngine =
        engine ?: MixEngine(context.applicationContext, MainScope()).also { engine = it }
}
