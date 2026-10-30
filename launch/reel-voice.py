"""FireTheDJ 1.3 → 1.5.1 explainer reel: the voiceover, one sentence at a time, with Kokoro.

Each sentence is spoken on its own, so its start and end are known exactly: the edit times its
captions and cuts from them. Sentences are written twice where the voice needs help: as shown
on screen, and as spoken ("FireTheDJ" → "Fire the D J").
Usage: py -3 reel-voice.py <kokoro.onnx> <voices.bin> <out dir>   → voice.wav, timing.json
"""
import json
import sys
import wave

import numpy as np
from kokoro_onnx import Kokoro

VOICE, SPEED = "af_heart", 1.08
SENTENCE_GAP, SCENE_GAP = 0.22, 0.42

# (scene id, headline, kicker, sentences); a sentence is "shown" or ("shown", "spoken").
SCENES = [
    ("hook", "Everything new", "1.3 → 1.5.1", [
        ("This is FireTheDJ, an AI DJ that mixes your music on its own.", "This is Fire the D J, an A I DJ that mixes your music on its own."),
        ("Here's everything new, from 1.3 to 1.5.1.", "Here's everything new, from one point three, to one point five point one."),
    ]),
    ("lyrics", "Lyric visualizers", "1.3", [
        "Beat Pop builds every line on the beat.",
        "Karaoke lights up each word as it's sung.",
    ]),
    ("wordplay", "Word play mixes", "1.3", [
        "When two songs share a word, it mixes right on it.",
        "The next song answers the last one.",
    ]),
    ("trickster", "Trickster, rebuilt", "1.4", [
        "Echo throws, bass kills, rising rolls and tape stops, stacked into the drops.",
    ]),
    ("regional", "Sounds by language", "1.4", [
        "Telugu and Tamil songs get dappu and whistles. Hindi and Punjabi get dhol.",
        "Latin and Arabic songs get their own drums.",
    ]),
    ("skip", "Skip button", "1.4", [
        "Tap skip, and it mixes out at the next phrase.",
    ]),
    ("decks", "Two decks, sideways", "1.5", [
        "Turn your phone sideways for two decks, the crossfader, and every effect as it plays.",
    ]),
    ("acid", "Acid Trip", "1.5", [
        "And a new visualizer, Acid Trip, that bursts on every drop.",
    ]),
    ("stream", "Spotify playlists", "1.5.1", [
        ("Got a Spotify playlist? FireTheDJ plays it from YouTube Music.", "Got a Spotify playlist? Fire the D J plays it from YouTube Music."),
        "No Spotify account needed. Search both in one place.",
    ]),
    ("talk", "Skips the talk", "1.5.1", [
        "Music videos with talking intros? It skips the talk and mixes only the music.",
    ]),
    ("analysis", "Ready to mix", "1.4", [
        "Songs are analysed in the background, lyrics and all.",
    ]),
    ("updates", "Updates in the app", "1.5.1", [
        "And new versions arrive right in the app.",
    ]),
    ("end", "FireTheDJ", "Free · open source", [
        ("FireTheDJ. Free and open source. Link in bio.", "Fire the D J. Free, and open source. Link in bio."),
    ]),
]


def main():
    k = Kokoro(sys.argv[1], sys.argv[2])
    out = sys.argv[3]
    sr = 24000
    audio, timing, t = [], [], 0.0
    for si, (sid, headline, kicker, sentences) in enumerate(SCENES):
        if si:
            audio.append(np.zeros(int(SCENE_GAP * sr), np.float32))
            t += SCENE_GAP
        scene = {"id": sid, "headline": headline, "kicker": kicker, "start": t, "sentences": []}
        for i, s in enumerate(sentences):
            shown, spoken = (s, s) if isinstance(s, str) else s
            if i:
                audio.append(np.zeros(int(SENTENCE_GAP * sr), np.float32))
                t += SENTENCE_GAP
            samples, sr = k.create(spoken, voice=VOICE, speed=SPEED, lang="en-us")
            # Trim the model's own leading and trailing silence, so the gaps above are the pauses.
            loud = np.where(np.abs(samples) > 0.01)[0]
            samples = samples[max(0, loud[0] - 240):loud[-1] + 2400] if len(loud) else samples
            audio.append(samples.astype(np.float32))
            scene["sentences"].append({"text": shown, "start": t, "end": t + len(samples) / sr})
            t += len(samples) / sr
        scene["end"] = t
        timing.append(scene)
        print(f"{sid:10s} {scene['start']:6.2f} - {scene['end']:6.2f}")
    pcm = (np.clip(np.concatenate(audio), -1, 1) * 32767).astype(np.int16)
    with wave.open(out + "/voice.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())
    json.dump(timing, open(out + "/timing.json", "w"), indent=1)
    print(f"voice {t:.2f} s")


main()
