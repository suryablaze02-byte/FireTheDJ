# How FireTheDJ works

The technical tour: how a track is understood, how the next track and the transition are chosen, and the DSP behind every effect. Paths are under `app/src/main/java/com/autodj/`.

## The pipeline

| Stage | Where | What it does |
|---|---|---|
| Decode | `analysis/PcmDecoder.kt` | Any Android-playable file → mono PCM at ~11 kHz |
| Analyse | `analysis/BeatAnalyzer.kt` | Spectral-flux onsets → tempo (autocorrelation + comb search to 0.01 BPM), beat phase from the kick band, downbeat, 8-bar phrases, per-bar energy, intro / breakdowns / outro, plus rhythm and timbre features (kick pattern, off-beat hats, bass share, brightness, onset density, dynamics) |
| Genre | `analysis/GenreClassifier.kt`, `analysis/Genres.kt` | Fuzzy genre-family profiles over tempo + features, fused with the file's genre tag. Low confidence → *Unclassified*, which is never mixed |
| Artwork | `analysis/ArtworkRepository.kt` | Embedded cover → iTunes Search API by artist + title (artist must match) → generated cover |
| Order | `dj/SetPlanner.kt` | Within one crate: greedy + 2-opt over tempo distance, sub-genre, feature distance and energy jumps |
| Decide | `dj/MixRecipes.kt` (`DecisionEngine`) | Scores 9 transition recipes on genre, tempo match, B's intro, A's outro, and recent history (no repeats) |
| Plan | `dj/TransitionPlanner.kt` | Phrase-aligned exit and entry; A plays to its outro, or is cut at a breakdown if it would run past 5 min; builds both decks' FX automation |
| Effects | `dj/fx/FxChain.kt`, `dj/fx/FxPlan.kt`, `dj/FxProcessor.kt` | Sample-accurate DSP inside each deck's ExoPlayer pipeline (before the time-stretcher), driven by automation in track time |
| Play | `dj/MixEngine.kt`, `dj/Deck.kt` | Two decks; tempo change without pitch change; phase-lock loop that nudges B's speed |
| Background | `dj/MixService.kt`, `dj/EnginePlayer.kt` | Foreground MediaSession service; play/pause/stop only |

## Understanding the music

- **Key:** chromagram (130 Hz–2 kHz, per-frame normalised) matched to Krumhansl profiles, shown on the Camelot wheel. Confidence measures the margin over keys that would *clash*, since relative and neighbouring keys mix fine anyway.
- **Vocals:** per bar, how much of the energy sits in the 250 Hz–3.5 kHz voice band and how harmonic (peaky) that band is, relative to the rest of the track. A heuristic: lead synths can read as vocal.
- **Hook:** the loud 8-bar phrase whose chord/melody pattern repeats most often.
- **Drops:** phrase starts where energy jumps after a quieter run-up.

## Performing inside a track (`dj/Performer.kt`)

- **Set tempo arc:** after a mix, the track glides to the set's master tempo, which climbs 0.6 BPM per track through the first 70% of the set. Tracks no longer snap back to their own BPM.
- **Tempo push:** +2.5% (key-locked) across the first full main section, to lift energy.
- **Loop-roll build:** the 2 bars before a drop become a roll tightening 1 → ½ → ¼ → ⅛ beat under a rising high-pass, released on the drop.
- **Hook replay:** the same track is cued at the hook on the spare deck; on the jump bar the decks crossfade on the beat (with an echo across the cut), so the hook plays twice.
- **Key matching:** for overlaps, the incoming track is pitched up to ±2 semitones (tempo unchanged) so the keys sit together on the Camelot wheel.

## Recipes

| Recipe | Used for | What happens |
|---|---|---|
| Bass-swap blend | House, techno, trance, pop | B fades in through a high-pass with its bass cut; bass lines swap mid-mix; A fades out (constant power) |
| Phaser blend | House, chill | Bass-swap blend with a 4-stage phaser and tremolo on A's way out |
| Flanger blend | Techno | Bass-swap blend with a flanger on A |
| Filter fade | Tempos too far apart | A sinks under a low-pass into reverb; B opens from a high-pass |
| Lo-fi fade | Chill, hip-hop | A bit-crushes and decimates into a low-pass while B comes in |
| Echo-out | DnB, pop, trance | A's last bar rolls 1 → ½ beat into a ¾-beat echo; A cuts on the one, B drops, echoes ring out |
| Gate build & drop | Trance, techno | 1/8 then 1/16 square gate with a rising high-pass, reverb wash, B drops |
| Backspin drop | Dubstep, DnB, hip-hop | A spins backwards to a stop; B drops straight into its first big section |
| Scratch cut | Hip-hop | A beat is caught and baby-scratched for the rest of the bar, then cut to B |
| Vocal swap | Beat-matched, keys fit, B has a sung phrase | A's vocal is cut (stereo centre above 180 Hz removed, bass kept) while B's vocal (centre only, 250 Hz–5 kHz) sings over A's beat for 8 bars; then the bass lines swap, B opens to full range and A fades |
| Loop & ramp | Cross-genre / tempo gaps up to ±45% | A's bar is looped while the deck speeds up (key-locked) to B's tempo over 4–8 bars, the loop halving 1 bar → ½ beat under a rising high-pass; B drops on the loop's downbeat |

Cross-genre sets move between neighbouring families (house ↔ techno ↔ trance, hip-hop ↔ drum &
bass at half/double time, hip-hop ↔ Latin, …), stay in a genre while it has good matches,
and favour cut-style transitions over long blends when the genre changes.

## Effects (DSP)

- **Crossfades:** linear, constant-power (sin/cos) and power (x²) gain laws.
- **EQ/filters:** RBJ biquads (low shelf for bass swaps, high-/low-pass) with exponential cutoff mapping.
- **Sync:** rate ratio r, phase error modulo the beat period, pitch shown as 12·log₂ r (key lock on).
- **Delay/reverb:** beat-synced echo (feedback g, repeats decay as g^k), and a Schroeder reverb (4 combs + 2 all-passes, comb g from RT60).
- **Modulation:** flanger (time-varying delay D(n)), 4-stage all-pass phaser, tremolo (amplitude modulation).
- **Rhythmic:** square-wave gate and loop roll from the playhead modulo the beat grid; scratch and backspin from captured audio.
- **Lo-fi:** bit-depth quantisation, sample-and-hold decimation, tanh waveshaping.

## Sources beyond local files

| Source | Where | How it plays |
|---|---|---|
| YouTube Music curated playlists | `remote/YtMusicBrowse.kt`, `ui/YtMusicScreen.kt` | Moods & genres browsed from YouTube Music's web API; "RDCLAK…" playlists read from the playlist page |
| YouTube / YouTube Music links | `remote/YouTubeMusic.kt`, `remote/YouTubeMedia.kt` | NewPipeExtractor resolves a playable AAC stream when a track is analysed or played; read in 1 MB ranges; never stored |
| Audius, Jamendo, Internet Archive, M3U | `remote/Providers.kt`, `remote/ArchiveProviders.kt` | Public APIs; stored copies only where the service allows downloads |

## Casting and the car

| Feature | Where | What it does |
|---|---|---|
| Wireless display (Miracast, Smart View) | `cast/TvPresentation.kt` | The TV becomes a second screen showing the chosen visualizer in landscape; Android carries the audio |
| Chromecast / Google TV | `cast/CastControl.kt`, `cast/MixStreamer.kt`, `cast/CastStreamService.kt` | The app's own audio is captured, encoded to AAC and served on the Wi-Fi as a live stream the TV plays |
| TV receiver page | `receiver/index.html` | Optional custom Cast receiver: plays the stream and draws the visualizers from it (see [CASTING.md](CASTING.md)) |
| Android Auto | `dj/MixService.kt`, `dj/CrateLibrary.kt` | Media library with one playable item per genre crate; the engine holds audio focus for both decks (`dj/MixFocus.kt`) |

## Limits and roadmap

- Genre detection uses hand-tuned profiles, not a trained model. It separates the common
  electronic families and hip-hop well on clear-cut tracks; pop, rock and Latin mostly
  rely on tags. Next step: an on-device classifier (TFLite) trained on a licensed dataset.
- True stem separation (an on-device Spleeter/Demucs-class model) for clean acapellas and
  instrumentals; the current vocal tools are mid/side + EQ approximations, so centred drums bleed.
- Variable-tempo beat grids for live-drummed tracks.
- Native (Oboe) output for tighter start sync between decks.
