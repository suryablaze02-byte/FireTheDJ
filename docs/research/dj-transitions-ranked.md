# DJ transitions and sound effects, ranked for FireTheDJ

Research report, 2026-10-28. It compares what working DJs do with what FireTheDJ already does (`dj/MixRecipes.kt`, `dj/fx/FxPlan.kt`, `dj/fx/SfxSynth.kt`, `dj/SfxDressing.kt`, `dj/Performer.kt`, `dj/TransitionPlanner.kt`, `dj/SetPlanner.kt`, `analysis/Genres.kt`, `docs/HOW_IT_WORKS.md`). Recommendations only. No app code was changed.

**Scoring.** Each item gets 1 to 5 on Crowd impact (CI), Smoothness (Sm), Versatility (Ve), Popularity (Po) and Automatable (Au). Overall = CI x2 + Sm + Ve + Po + Au x2, out of 35. A † marks a score that rests on thin evidence: one source, vendor marketing, or my own judgement where I found no source.

**Status.** **Have** means the app does it. **Partial** means it exists but something is missing, and the row says what. **Missing** means the app doesn't do it.

---

## 1. Summary: top 10 things to add or improve

1. **Regional percussion-fill cut (Missing).** Telugu and other Indian party DJs often play a dappu, teenmaar or dhol fill and hard-cut into the next song on the one. The app has no Indian percussion and no "fill then cut" recipe, and this is the largest gap for its Telugu users.
2. **A "desi" sound palette for Indian tracks (Partial).** Every Bollywood, Tollywood, Telugu, Tamil and Punjabi tag maps to the Pop & Dance crate. `SfxDressing` treats Pop & Dance as *electronic*, so Indian tracks get EDM risers and snare rolls and never get the siren, horn or percussion dressing that matches how Indian DJs dress their sets. There is also a bug: a tag such as "Telangana folk" or "Telugu folk" lands in **Rock**, because `ROCK`'s "folk" keyword is checked before `POP`'s "telugu" keyword.
3. **A plain hard cut on the one (Partial).** This is the most-taught basic transition after the fade and the bass swap, and it is the norm in hip-hop, R&B and Indian remix sets. Every cut recipe in the app adds pre-effects and a one-bar crossfade, so the app has no clean, instant cut.
4. **Drop swap (Partial).** Play A's build-up, then land B's drop where A's drop would have been. It has the biggest payoff of any cut, and the app already knows where builds and drops are. `BASS_DROP` blends into B's drop, but nothing cuts from A's build into B's drop.
5. **A gap before the drop (Partial).** DJs and producers leave half a beat to a full beat of silence, or bass-only, before the hit. The evidence for this is from producers, not DJ guides (†). `BASS_DROP` already has a bar with no bass, but the build-and-hit dressing never leaves a gap.
6. **A slot for a DJ drop or vocal tag (Missing).** Hip-hop, Latin and Indian sets use name drops and shout-outs heavily. The app can't synthesize a voice, but it could play a tag the user records, at intros and outros only, which is the etiquette DJ TechTools recommends.
7. **Rewind / pull-up (Partial).** A tape-rewind sound, then the *same* track restarts from its drop. It gets the biggest crowd reaction in dancehall, jungle and UK garage, and the procession and roadshow styles use it as well (†). `BACKSPIN` already makes the sound, but nothing restarts the track.
8. **Brake as a transition (Partial).** `BRAKE` exists only as an in-track fill in `Performer`. A "brake exit" recipe (A stops on the last beat, B slams in on the one) is cheap to add and works in hip-hop, bass music and Indian sets.
9. **Energy-boost key moves (Partial).** `SetPlanner` avoids key clashes and allows a ±2 semitone shift, but it never steers the key upward on purpose to lift a set. Mixed In Key's "+2 energy boost" (and +1 for a gentler lift) used at energy peaks would give the set an arc.
10. **Breakdown swap (Missing).** Bring B's beat in during A's breakdown when A has 8 or more bars of space. One guide calls it "the most musical option when available". The app's analysis already finds breakdowns, but no recipe uses them.

---

## 2. Transitions, ranked

| # | Name | What it is | CI | Sm | Ve | Po | Au | Overall | Status | When to use |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Hard cut on the one | B starts on a downbeat and A is gone instantly, with no fade | 4 | 3 | 5 | 5 | 5 | **31** | Partial: every cut recipe adds pre-effects and a one-bar crossfade. Add a plain cut. | Hip-hop, R&B, pop and Indian remixes. Different BPMs or keys. Phrase end, with B on its first downbeat or its hook. |
| 2 | Bass-swap / long EQ blend | Overlap 8 to 32 bars, and swap the basslines in one move at the midpoint | 3 | 5 | 4 | 5 | 5 | **30** | Have (`BASS_SWAP_BLEND`) | House, techno, trance, matched keys. Over A's outro and B's intro. |
| 3 | Echo out | Echo A's last beat or bar, kill A on the one, and let the tail ring under B | 3 | 4 | 5 | 5 | 5 | **30** | Have (`ECHO_OUT`) | Any genre, and the r/Beatmatch "when unsure" default. Keep the tail short so it doesn't clutter B. |
| 4 | Harmonic mixing (with energy-boost moves) | Pick or pitch B so the keys sit together on the Camelot wheel, and step the key up (+1 or +2) to lift energy | 3 | 5 | 5 | 4 | 5 | **30** | Partial: clash-avoidance and a ±2 semitone shift exist. The upward key moves for energy don't. | Any genre with long overlaps. Use the boost at peaks only, not on every mix. |
| 5 | Drop swap | A's build-up plays, then B's drop lands in place of A's drop | 5 | 4 | 3 | 4† | 4 | **29** | Partial: `BASS_DROP` blends into B's drop. There is no cut from A's build. | EDM, bass, Indian remixes. On A's build bar 8 or 16, with B entering at its drop. |
| 6 | Loop roll / loop and build | Loop A, halve the loop (1 bar, ½, ¼, ⅛) under a rising filter, then drop B | 4 | 3 | 4 | 4 | 5 | **29** | Have (`LOOP_RAMP`, rolls in `ECHO_OUT` and `Performer`) | Peaks and genre resets. Match the tension to the payoff. |
| 7 | Slam (cut, then FX hit) | A cut with an impact, crash or riser on the switch | 5 | 2 | 4 | 3 | 5 | **29** | Have (Trickster: `TransitionPlanner.withSoundEffects`). The Smooth set gets none. | Genre or BPM jumps, and building tension. |
| 8 | Filter fade (HPF/LPF) | A sinks under a low-pass while B opens from a high-pass | 2 | 4 | 5 | 4 | 5 | **27** | Have (`FILTER_FADE`) | Different textures or tempos. Easy to overuse. |
| 9 | Percussion-fill cut (regional) | A live-drum fill (dappu, teenmaar, dhol, tasha), then a hard cut into B | 5 | 3 | 2 | 4† | 4 | **27** | Missing | Telugu, Tamil, Punjabi and Marathi party and procession sets. See section 4. |
| 10 | Siren into drop | A siren swells over A's last 2 to 4 bars, and B drops on the one | 4 | 3 | 3 | 3† | 5 | **27** | Partial: the siren build exists only for non-"electronic" crates, so Indian tracks (crated as Pop) never get it | Dancehall, reggaeton, hip-hop, Indian remixes |
| 11 | Backspin / spinback | Spin A's platter backwards to a stop, and drop B | 4 | 2 | 3 | 3 | 5 | **26** | Have (`BACKSPIN_DROP`) | Hip-hop, bass, DnB. Last bar of a phrase. |
| 12 | Brake / vinyl stop | A's platter slows to a halt, then B starts on the one | 4 | 2 | 3 | 3 | 5 | **26** | Partial: in-track fill only, with no transition recipe | Hip-hop, bass, Indian. The last beat of a phrase. |
| 13 | Breakdown swap | B's beat comes in under A's breakdown, which needs 8+ open bars | 3 | 5 | 3 | 3 | 4 | **25** | Missing | House, trance, progressive |
| 14 | Gate / beat-chop build | Gate A at 1/8 then 1/16 under a rising high-pass, then drop B | 3 | 3 | 3 | 3 | 5 | **25** | Have (`GATE_BUILD`) | Trance, techno |
| 15 | Reverb wash out | Throw A into a long reverb and kill the dry signal | 2 | 4 | 4 | 3 | 5 | **25** | Partial: only inside `FILTER_FADE`. There is no reverb-freeze exit of its own. | Ballads into uptempo, ends of sets, chill |
| 16 | Tempo ramp | Speed A (or a loop of A) up or down to B's BPM | 3 | 3 | 3 | 2 | 5 | **24** | Have (`LOOP_RAMP`, master-tempo glide) | Cross-genre BPM gaps |
| 17 | Half-time / double-time link | Mix at 2:1, for example 70 BPM to 140 BPM | 3 | 4 | 3 | 3 | 4 | **24** | Have (tempo multiples in the planner) | Hip-hop to DnB or dubstep, trap to house |
| 18 | Rewind / pull-up | A rewind sound, then restart the *same* track from its drop | 5 | 1 | 2 | 3† | 4 | **24** | Partial: the `BACKSPIN` sound exists, but nothing restarts the track | Dancehall, jungle, UK garage, Indian roadshow sets. Once or twice a set, on the biggest tune. |
| 19 | Acapella over instrumental / vocal swap | B's vocal sings over A's beat, then the beats swap | 4 | 4 | 3 | 3 | 3 | **24** | Partial: `VOCAL_SWAP` uses a mid/side and band heuristic, not stems (HOW_IT_WORKS lists stems as future work) | Pop, house, hip-hop. Keys and tempos must match. |
| 20 | Phaser / flanger blend | A blend with modulation on the outgoing track | 2 | 4 | 3 | 2 | 5 | **23** | Have | House, techno, chill |
| 21 | Word-play / lyric-cue | Cut on a lyric that "answers" the last song | 5 | 3 | 3 | 3 | 1 | **21** | Missing. It needs lyrics or ASR, so it's a poor fit for automation. | Open-format, hip-hop, weddings |
| 22 | Mashup layering | Run two tracks together for many bars | 4 | 3 | 2 | 3 | 2 | **20** | Missing | Open-format peaks. Needs key, tempo and arrangement compatibility. |
| 23 | Scratch-in | Scratch B's first beat in, then let it run | 3 | 2 | 2 | 2 | 4 | **20** | Partial: `SCRATCH_CUT` scratches A *out*, but nothing scratches B in | Hip-hop, turntablist styles |
| 24 | Lo-fi fade | A bit-crushes and darkens as B comes in | 2 | 3 | 2 | 1† | 5 | **20** | Have (`LOFI_FADE`) | Lo-fi, chill, hip-hop |

**Why the top items scored as they did:**
- **Hard cut:** universal and fully automatable from the beat grid. It loses only on smoothness.
- **Bass swap and echo out:** guides and Reddit consistently name these the workhorse and the go-to fallback.
- **Harmonic mixing:** invisible when it works, and makes every long overlap better.
- **Drop swap and slam:** the highest crowd impact, but they only fit tracks with clear drops.
- **Loop roll:** the app already does it well.
- **Regional fill cut:** huge in its context but narrow in scope. Its popularity score rests on news reports and track titles, not DJ tutorials.
- **Word-play and mashups:** effective, but an AI can't judge them reliably.

---

## 3. Sound effects, ranked

| # | Effect | CI | Sm | Ve | Po | Au | Overall | Status | Typical length | Where DJs fire it |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Riser / white-noise sweep | 4 | 4 | 5 | 5 | 5 | **32** | Have (`RISER`) | 2–8 bars, about 8 s | Across a build, peaking on the drop. Narrowing the stereo width as it rises makes the drop hit wider (†). |
| 2 | Crash on the one | 4 | 4 | 5 | 4 | 5 | **31** | Have (`CRASH`) | Rings 2–5 s | Downbeat of the drop or of B's entry |
| 3 | Sub drop / 808 boom | 4 | 4 | 4 | 4 | 5 | **30** | Have (`SUB_DROP`) | 1–3 s | Downbeat of the drop |
| 4 | Snare roll | 4 | 4 | 4 | 4 | 5 | **30** | Have (`SNARE_ROLL`: 1/4 → 1/8 → 1/16) | 1–8 bars | Last bars of a build |
| 5 | Reverse cymbal | 3 | 5 | 4 | 4 | 5 | **29** | Have (`REVERSE_CYMBAL`) | 1 bar | Sucks into the downbeat |
| 6 | Pre-drop silence ("drop mute") | 4 | 4 | 4 | 3† | 5 | **29** | Partial: only `BASS_DROP`'s no-bass bar. The build and hit dressing never leaves a gap. | ½–1 beat (or up to 2 bars) | Just before the drop |
| 7 | Dhol / dappu / tasha fill (regional) | 5 | 3 | 2 | 4† | 5 | **29** | Missing | 1–2 bars | The last bar of a phrase, then a cut. See section 4. |
| 8 | Impact / boom | 4 | 3 | 4 | 4 | 5 | **28** | Have (`IMPACT`) | 1–3 s | Downbeat |
| 9 | Air horn | 5 | 1 | 3 | 4 | 4 | **26** | Have (`HORN`, bwaa ×3 + long) | 1 bar | On the drop or a hook. Overuse annoys crowds (Digital DJ Tips). Cap it at one per few minutes. |
| 10 | Downlifter | 2 | 5 | 4 | 3 | 5 | **26** | Have | 1–2 bars | Just after the drop, or out of a peak |
| 11 | Rewind (tape-rewind sound) | 4 | 2 | 3 | 3 | 5 | **26** | Partial: the `BACKSPIN` DSP gives the sound, but there's no restart move (see transition 18) | ½–1 bar | Before restarting the drop |
| 12 | Whoosh | 2 | 4 | 4 | 3 | 5 | **25** | Have | 2–3 beats | Across a cut |
| 13 | Siren | 4 | 2 | 3 | 3 | 4 | **24** | Have (`SIREN`, 500–1000 Hz dub siren) | 2–4 bars | Builds in dancehall, hip-hop and Indian sets. Overuse is the classic complaint, such as a siren every 16 beats. |
| 14 | DJ drop / vocal tag / shout | 4 | 2 | 4 | 4 | 3 | **24** | Missing | 1–3 s | Intros and outros only, never right before the drop (DJ TechTools, Daily Bruin) |
| 15 | Crowd cheer / whistles | 3 | 3 | 3 | 2† | 5 | **24** | Missing | 2–4 s | On the drop, in festival-style edits |
| 16 | Nadaswaram / shehnai sting (regional) | 4 | 3 | 2 | 3† | 4 | **24** | Missing | 1–2 bars | Wedding sets, entrance songs (section 4) |
| 17 | Echo throw | 3 | 3 | 4 | 4 | 5 | **26** | Have (`Fill.ECHO_THROW`) | 1 beat in, about 2 bars of tail | The last word of a vocal line |
| 18 | Vinyl scratch | 3 | 2 | 2 | 3 | 4 | **21** | Have (`SCRATCH` DSP) | 1 bar | Phrase ends in hip-hop |
| 19 | Laser / zap | 3 | 2 | 2 | 2 | 5 | **22** | Have | 1–2 bars | Electro, bass, Indian roadshow remixes (†) |
| 20 | Gunshot ("pull up") | 4 | 1 | 2 | 2† | 4 | **21** | Have | 3 shots on the beats | Dancehall, UK, drill |

Note on the overall numbers: rows are grouped by role, so a few sit slightly out of score order. Echo throw scores 26 but is listed with the other performance FX.

**Synthesis gaps, if the missing SFX get built:**
- **Crowd cheer:** dense band-passed noise (500 Hz–3 kHz) with slow random amplitude movement, a vowel-like formant pair (roughly 700 and 1200 Hz) wobbling, and sparse transient "claps" made of short noise bursts with a 5–15 ms decay. This is my own design suggestion and is not sourced.
- **DJ drop / tag:** voice can't be synthesized convincingly with the app's oscillators. Let the user record their own tag in the app, and play it only at intros and outros. Don't ship any third-party tag.
- **Pre-drop silence:** for `SfxDressing.hit` drops, mute the deck (or kill everything except the riser tail) for the last ½ beat before the downbeat.

---

## 4. Telugu and regional sounds

**Crate fit (now).** All these tags (`telugu`, `tollywood`, `tamil`, `kollywood`, `bollywood`, `hindi`, `punjabi`, `bhangra`, `indian`, `filmi`) map to **Pop & Dance**. The exception is any tag containing "folk" (for example "Telangana folk", "Telugu folk", "Janapada folk"), which maps to **Rock**, because `ROCK` comes before `POP` in `Genres.tagKeywords`. In `SfxDressing`, both Pop and Rock count as `ELECTRONIC`, so Indian tracks get risers, snare rolls and reverse cymbals instead of the siren, horn and percussion palette the regional styles use.

**Recommendation.** Add an "Indian / Desi" family, or at least an `isIndian` flag on the existing families, that:
- picks the siren, horn and percussion dressing instead of the electronic one;
- enables the percussion-fill cut;
- keeps folk-tagged Indian tracks out of Rock.

The rhythm patterns below are described so they can be synthesized. Tempo figures marked † are inferences, not sourced. The app's own beat tracker should set the BPM at run time anyway.

### 4.1 Dappu / teenmaar fill (Telugu, Telangana), top pick

- **What it is.** The dappu is a large Telugu frame drum (hide over a tamarind or neem frame) associated with Telangana folk performance, and it "leads a procession". *Teenmaar* ("three beats") is the Telugu name for the dappankuthu-style dance beat that Telugu cinema took from Tamil. Wikipedia says Telugu teenmaar uses daf (frame) drums. Siasat lists "energetic Teenmaar beats" as a main reason Telangana folk songs are party staples.
- **How it's used in a set.** It's the backbone of Telangana folk DJ mixtapes played at baraats, Bonalu and Ganesh processions, political events and Hyderabad pubs (News Minute). DJs run the beat under a vocal hook and cut between songs on a fill. That last point comes from how procession sets are generally described and from track titles, not from a tutorial (†).
- **How to synthesize it.**
  - **Open stroke ("DHA"):** a membrane mode about 140–200 Hz with a quick downward pitch bend (about 15% over 40 ms), plus a bright noise slap band-passed around 1.5–3 kHz. Amplitude: about 1 ms attack, about 120 ms decay on the tone, about 30 ms on the slap.
  - **Closed or muted stroke ("ka"):** the same slap with no tonal tail, 20–40 ms. A frame drum has little sustain, so keep the decays short.
  - **Rhythm:** sources give 6/8 as the most common meter for dappankuthu, so feel it in compound (triplet) time. A usable 1-bar fill cell: `DHA . ka DHA . ka | DHA ka DHA ka DHA ka` (12 eighth-note triplet slots, density doubling in the second half). End with three accented unison hits on beats 1, 2 and 3 of the cut bar ("teen maar"), then go straight into B. This pattern is my own synthesis design (†). No source notates the exact pattern.
  - **Tempo:** follow the track's BPM. Procession DJ packs span about 80–160 BPM, and the fill should land on A's phrase end.
- **Popularity.** High in Telangana and Andhra by every news source. There's no quantitative measure.
- **Crate.** Pop & Dance today, and the proposed Indian family later. Folk-tagged tracks currently fall into Rock.

### 4.2 Hyderabadi marfa roll

- **What it is.** A fast Afro-Arab drum style brought to Hyderabad by the Siddi community. It uses the marfa (kettledrum, or a *handi* pot drum in the Siddi form), daff, dhol, sticks, steel pots and wooden *thapi* strips. Its three-beat cycle is itself called *teen maar taal*. It's at "almost every wedding in Hyderabad" (Siasat), and bookings soar for Ganesh immersion (Telangana Today). DJs sell "Marfa Hyderabadi" beat edits (Last.fm track list).
- **How it's used in a set.** It's a high-energy interlude or bridge at weddings, and the procession crowd's call to dance.
- **How to synthesize it.**
  - **Kettle or pot drum:** a low sine around 70–100 Hz with a slight pitch drop and about 250 ms decay.
  - **Daff:** the same slap as the dappu, a little higher, about 200–250 Hz.
  - **Steel pot or sticks:** short metallic clicks, using the existing `metal()` partials with a 20 ms decay.
  - **Rhythm:** a driving three-stroke cell, LOW–slap–slap, repeated on straight 8ths at high tempo (about 120–140 BPM†), with an accelerating "roll" of slaps into the cut.
  - Swap the metal clicks onto the off-beats for the steel-pot colour.
- **Crate.** Pop & Dance or the Indian family. It also suits the Latin crate's Afro feel.

### 4.3 Dhol chaal fill (Punjabi / bhangra, and pan-Indian weddings)

- **What it is.** *Chaal* ("movement") is the signature bhangra dhol rhythm: 4/4 with each beat split in three, accenting beats one and four (UK GCSE music teaching material). The dhol is a two-headed drum, with a deep bass side struck by a thick stick and a treble side struck by a thin cane.
- **How it's used in a set.** Indian wedding DJs blend Bollywood and bhangra and rely on dhol-driven fusion tracks, and baraat timing is a core skill. The dhol fill into a hard cut, or into the next track's first beat, is a fixture of these sets (†: the vendor pages describe it only loosely).
- **How to synthesize it.**
  - **Bass head ("dagga"):** a sine about 60–90 Hz bending down, with about 300 ms decay and a little saturation (`tanh`).
  - **Treble head ("tilli"):** a crisp crack. Noise band-passed around 2–4 kHz plus a 400–600 Hz ping, 30–60 ms.
  - **Chaal:** in triplet subdivision per beat (x = dagga, t = tilli), use `x t t | x . t | x t t | x t t`, with dagga on 1 and 4 accented. This is an approximation built from the "1 and 4 accented, triplet subdivision" description (†).
  - **Fill:** the last bar goes to all-tilli triplets that crescendo, ending in a unison dagga+tilli hit on the cut.
  - **Tempo:** bhangra commonly sits around 90–110 BPM (†, not sourced).
- **Crate.** Pop & Dance (the "punjabi" and "bhangra" tags).

### 4.4 Other regional sounds (lower priority)

| Sound | What it is | Use in a set | Synthesis sketch | Popularity / evidence | Crate |
|---|---|---|---|---|---|
| **Siren into drop (Telugu/Tamil roadshow style)** | A dub-style siren over the last bars before a remix's bass drop | Builds, and the "siren into a drop" cut | The app's `SIREN` already fits. Route Indian tracks to it (see Summary 2). | Common in remix titles and descriptions; no tutorial evidence (†) | Pop & Dance |
| **Nadaswaram / shehnai sting** | Loud double-reed wind instruments (nadaswaram in Tamil and Telugu temple and wedding music, sannai in Telugu, shehnai in the north), with the thavil drum | Wedding entrance edits and auspicious moments. Telugu Edurukolu welcomes the groom with Nadaswaram or "Sannai Melam". | Nasal reed: a sawtooth into two formant band-passes (about 1.1 kHz and 2.6 kHz, Q 5), heavy vibrato (5–6 Hz, ±30 cents) and gamaka-like pitch slides of 1–2 semitones into each note. A 1-bar ascending figure ending on a held note. Don't quote real melodies. | Ubiquitous at South Indian weddings | Pop & Dance, wedding use |
| **Thavil hits** | A barrel drum played with a stick and thimbled fingers. Loud, penetrating, high-pitched. | Pairs with the nadaswaram sting, and good as a "hit" on B's first beat | A high, hard crack (noise band-passed around 3 kHz, 20 ms) plus a short tone around 300–400 Hz, 80 ms | Strong (Esplanade, travel account) | Pop & Dance |
| **Tamil kuthu / thappattam (parai)** | Parai frame drum struck with sticks, often with the urumee drum. A dry snap over deeper barrel-drum strokes. Dappankuthu is mostly 6/8. | Kuthu DJ remixes: a fill-and-cut, like Telugu teenmaar | A drier, higher version of the dappu stroke (shorter tonal decay, about 60 ms). The urumee can be a friction-drum "groan": a sine sweeping 150 → 300 → 150 Hz over a beat. | Medium to high (Wikipedia; remix titles) | Pop & Dance |
| **Dhol-tasha (Marathi, Ganpati)** | Large dhol ensembles with tasha snare drums, zhanj cymbals and a tol gong | Ganesh procession rhythms ("taals") with named new compositions every year | Tasha: very fast high snare rolls (noise band-passed around 2.5 kHz, 15 ms hits). Dhol as in 4.3. Zhanj: the app's `metal()` with a 200 ms decay. | Strong news coverage. Details of the build-up are unverified. | Pop & Dance |
| **Chenda melam (Malayalam)** | A cylindrical stick drum ensemble. Very loud and sharp, with long accelerating cycles. | Festival and fusion use, now also in hip-hop (Kochuveettil Beats with Hanumankind) | A sharp stick crack (noise band-passed around 2 kHz) plus a 250 Hz tone, with rolls that speed up over 4–8 bars | Strong as a tradition; DJ use thin (†) | Pop & Dance / Hip-Hop |
| **Vocal shouts in regional remixes** | Short crowd-hype calls in the local language, and DJ name tags | Intros, outros, breaks | Don't synthesize. Offer a user-recorded tag slot. | High (†) | Any |

**How regional DJs transition.** The evidence is thin, but the sources (news reports, wedding-DJ vendors, remix-pack listings) point to a pattern:
- hard cuts, more than long blends;
- a percussion fill or siren build right before the cut;
- energy kept high throughout, since processions and baraats are dance-the-whole-time events.

The smooth long blend matters less here than in house or techno.

---

## 5. Sources

Transitions and FX:
- https://digitaldjpool.com/blog/dj-transitions-for-beginners/
- https://www.thomannmusic.com/blog/gear/the-5-most-important-dj-transitions/
- https://www.mixgraph.io/learn/dj-transition-techniques
- https://violetrecording.com/how-to-do-smooth-dj-transitions/
- https://www.londonsoundacademy.com/blog/10-hip-hop-dj-transitions
- https://virtualdj.com/articles/how-to-use-dj-effects
- https://www.digitaldjtips.com/?p=2799229 (The Only Five DJ Transitions You'll Ever Need)
- https://blog.native-instruments.com/dj-transitions/
- https://pirate.com/en/blog/beginner-dj-transitions
- https://blog.zzounds.com/2019/10/03/three-dj-transitions-to-try-between-songs/
- https://lr.sudovanilla.org/r/Beatmatch/comments/19942ck/djing_rule_of_thumbs/kibsgem/?context=3 (r/Beatmatch "DJing rule of thumbs" thread, Jan 2024, mirror)
- https://virtualdj.com/forums/182542/General_Discussion/Transitions.html
- https://mixedinkey.com/workflows/how-to-use-the-camelot-wheel/
- https://mixedinkey.com/workflows/change-energy-with-camelot-wheel/
- https://mixedinkey.com/?p=188 (Energy Boost Mixing)
- https://dj.studio/blog/compatible-keys
- https://www.zipdj.com/blog/camelot-wheel

Sound effects:
- https://daily.redbullmusicacademy.com/2013/06/in-search-of-the-air-horn
- https://cdm.link/meet-guy-can-blame-air-horn-sounds-everywhere/
- https://www.digitaldjtips.com/?p=10899 (Why Samples Ruin DJ Sets)
- https://www.npr.org/2011/10/11/141235449/new-exhibit-traces-history-of-reggaes-sound-system
- https://djtechtools.com/2012/08/22/dj-name-drops-cheese-or-choice/
- https://dailybruin.com/2016/02/26/love-hate-do-dj-shoutouts-build-hype-or-are-they-a-tired-stereotype
- https://producer.musicradiocreative.com/now-hold-the-classics
- https://violetrecording.com/how-to-make-risers-and-sweeps/
- https://blog.faderpro.com/techniques/3-tips-for-better-edm-drops/
- https://dnbforum.com/threads/the-drop.101135/
- https://morphic.com/fr/resources/sounds/dj-sound-effects

Telugu and regional:
- https://en.wikipedia.org/wiki/Teenmaar
- https://en.wikipedia.org/wiki/Dappankuthu
- https://en.wikipedia.org/wiki/Marfa_(music)
- https://en.wikipedia.org/wiki/Baraat
- https://en.wikipedia.org/wiki/Chenda
- https://en.wikipedia.org/wiki/Melam
- https://www.thenewsminute.com/telangana/telangana-folk-songs-dj-mixtapes-why-traditional-artists-are-not-happy-145753
- https://www.thenewsminute.com/telangana/revive-dappu-s-rhythmic-drum-beats-telangana-training-more-artists-146016
- https://www.siasat.com/top-5-telangana-folk-songs-you-cant-miss-at-any-function-3307974/
- https://www.siasat.com/watch-nagarjuna-naga-chaitanya-akhil-enjoy-hyderabadi-marfa-3230674/
- https://telanganatoday.com/hyderabads-marfa-bands-see-bookings-soar-ahead-of-ganesh-immersion
- https://www.last.fm/zh/music/Dj+Nikhil+Martyn/+tracks (track titles: "Marfa Hyderabadi", "Teenmaar Dappulu", Bonalu beats)
- https://superprofile.bio/vp/6aa56367b9181300133a7ae0 (Ganeshotsav 2026 DJ pack, 80–160 BPM range)
- https://www.siasat.com/hyderabad-cp-warns-over-ganesh-songs-brs-calls-it-hitler-rule-3547413/
- https://dailypioneer.com/news/ganesh-deaths-loud-djs-firecrackers
- https://www.esplanade.com/whats-on/festivals-and-series/festivals/2023/a-tapestry-of-sacred-music/events/mangala-vadyam-auspicious-instruments
- https://arizonahandbook.com/KL_Chennai_2024-25_10.html
- https://organology.net/?p=2064
- https://elizabethcollege.fireflycloud.net/music/gcse-music/topic-pages/aos3-rhythms-of-the-world/indian-musical-traditions/bhangra
- https://thenational.academy/pupils/programmes/music-secondary-year-9-l/units/fusions-b6e5/lessons/to-be-able-to-perform-and-improvise-around-a-chaal-c4w36r
- https://www.myweddingsongs.com/weddingblog/top-indian-wedding-songs/
- https://partyam.com/choosing-the-perfect-brides-entrance-indian-wedding-songs-2026/
- https://www.punekarnews.in/?p=245674
- https://www.outlooktraveller.com/amp/story/News/destinations/india/echoes-of-bappa-how-ganesh-chaturthi-in-maharashtra-resounds-with-music-and-faith
- https://acm.edu/features/feature/431/
- https://www.hollywoodreporterindia.com/features/interviews/kochuveettil-beats-x-hanumankind-how-the-sound-of-kerala-took-over-coachella
- https://suno.com/music-library/instruments/dappu (AI-music library page, used only for the dappu's general description; low weight)

### Could not verify

- **Notated dappu, teenmaar and marfa patterns.** No source writes them down. The patterns in section 4 are synthesis designs built from the sourced facts (frame drum, 6/8 or triplet feel, "three beats").
- **Typical BPMs for Telugu DJ remixes, marfa and bhangra.** The only source is a procession DJ pack listing 80–160 BPM. The other tempo figures are marked †.
- **Regional transition habits.** That Telugu and Tamil DJs hard-cut on percussion fills or use siren-into-drop comes from track titles and general descriptions. I found no DJ tutorial or interview that says so.
- **Kannada tamate.** I found no reliable source on it in DJ use.
- **Popularity ranks of individual effects.** These come from guides and one Reddit thread, not a survey.

## User notes

(Nothing here yet. Notes added under this heading are kept when the report is regenerated.)
