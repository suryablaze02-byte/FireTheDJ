"""Speaks each scene of script.json with Kokoro (open-source, offline) into wav/<id>.wav.

Usage: py -3 voice.py [voice] [speed]     e.g. py -3 voice.py af_heart 1.0
Prints each scene's spoken length, which the edit uses to time the scenes.
"""
import json
import os
import sys

import soundfile as sf
from kokoro_onnx import Kokoro

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = os.path.join(HERE, "..", "voice")
VOICE = sys.argv[1] if len(sys.argv) > 1 else "af_heart"
SPEED = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0

kokoro = Kokoro(os.path.join(MODEL, "kokoro-v1.0.onnx"), os.path.join(MODEL, "voices-v1.0.bin"))
os.makedirs(os.path.join(HERE, "wav"), exist_ok=True)
lengths = {}
for scene in json.load(open(os.path.join(HERE, "script.json"), encoding="utf-8")):
    samples, rate = kokoro.create(scene["text"], voice=VOICE, speed=SPEED, lang="en-us")
    sf.write(os.path.join(HERE, "wav", scene["id"] + ".wav"), samples, rate)
    lengths[scene["id"]] = round(len(samples) / rate, 3)
    print(f"{scene['id']:10s} {lengths[scene['id']]:6.2f} s")
json.dump(lengths, open(os.path.join(HERE, "wav", "lengths.json"), "w"), indent=1)
print(f"total {sum(lengths.values()):.1f} s")
