package com.autodj.remote

/**
 * One track from a remote service, as much as the mixer needs before analysis: the audio
 * comes from [streamUrl], which must stay valid across launches (an API endpoint that
 * redirects to fresh signed audio, not the signed link itself).
 */
data class RemoteTrack(
    val title: String,
    val artist: String,
    val streamUrl: String,
    val providerId: String,
    /** The service's own id for the track. */
    val providerTrackId: String,
    val artworkUrl: String? = null,
    val durationSec: Double? = null,
    /** The service's genre text ("Hip-Hop/Rap"); fused with the audio like a file's genre tag. */
    val genre: String? = null,
    /**
     * Whether the service lets apps keep a copy. If not, the track is never stored: it is
     * streamed for analysis and again each time it plays.
     */
    val storable: Boolean = true,
)

/** A playlist fetched from a provider: becomes a source to mix from, like a folder. */
data class RemotePlaylist(
    /** The link the user pasted, normalised; identifies the playlist. */
    val link: String,
    val providerId: String,
    val playlistId: String,
    val name: String,
    val tracks: List<RemoteTrack>,
)

/** A validated link: which provider handles it and the playlist id it names. */
data class PlaylistRef(val providerId: String, val playlistId: String, val link: String)

/**
 * How a provider recognises its playlist links. The id comes from a query parameter
 * ([idParam], e.g. `?list=…`) or, failing that, from the path ([pathPattern]'s first group).
 */
class PlaylistUrlRule(
    /** Accepted hosts; a leading "www." on the link is ignored. Empty = any host. */
    val hosts: Set<String>,
    val idParam: String? = null,
    val pathPattern: Regex? = null,
    /** What a valid id looks like, so junk never reaches the provider's API. */
    val idPattern: Regex = Regex("""[A-Za-z0-9_\-.~%/]{1,512}"""),
)

/**
 * A third-party catalogue AutoDJ can mix from. Implement this and register it in
 * [StreamProviders] to support another service.
 */
interface StreamProvider {
    val id: String
    val displayName: String
    val urlRule: PlaylistUrlRule

    /** Blocking network call; run off the main thread. Throws on network or API errors. */
    fun fetchPlaylist(ref: PlaylistRef): RemotePlaylist
}

/** A plain GET returning the body; swappable in tests. */
fun interface HttpGet {
    fun get(url: String): String
}
