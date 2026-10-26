package com.autodj.cast

import android.content.Context
import com.autodj.BuildConfig
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Which receiver app the TV runs: FireTheDJ's own (with visualizers) once registered, else Google's. */
object CastReceiver {
    val appId: String get() = BuildConfig.CAST_APP_ID.ifBlank { CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID }

    /** True when the TV runs FireTheDJ's receiver page (receiver/index.html), which draws the visualizers. */
    val custom: Boolean get() = BuildConfig.CAST_APP_ID.isNotBlank()

    /** Messages between the phone and FireTheDJ's receiver page. */
    const val NAMESPACE = "urn:x-cast:com.autodj.mix"
}

/** Read by the Cast framework through the manifest. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastReceiver.appId)
            // Stopping the cast closes FireTheDJ on the TV too, rather than leaving a dead stream up.
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
