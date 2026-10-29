"""Measure DJ transitions in reference clips (reels, set recordings).

Usage: py research/analyze_mix.py research/adi_wav

For every audio or video file in the folder it writes, into <folder>/analysis/:
  <name>.png             overview: spectrogram, bass/mid/high energy, tempo, key, brightness,
                         with the detected transitions marked
  <name>_tN.png          close-up of each transition (+-10 s)
  <name>.md              per-section tempo and key, and for each transition what the
                         numbers say happened (bass swap, filter sweep, dip, cut on the beat)
  summary.md             all clips together
Nothing here judges taste; it turns audio into numbers and pictures that can be read.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import librosa
import librosa.display
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

SR = 22050
HOP = 512
MEDIA = {".mp4", ".mov", ".m4a", ".mp3", ".wav", ".aac", ".webm", ".ogg", ".flac", ".mkv"}
NOTES = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
# Krumhansl-Kessler key profiles.
MAJOR = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
MINOR = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])


def load(path):
    """Decode anything ffmpeg reads to mono 22.05 kHz."""
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "a.wav"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-vn", "-ac", "1",
                        "-ar", str(SR), str(wav)], check=True)
        y, _ = librosa.load(wav, sr=SR, mono=True)
    return y


def key_of(chroma):
    c = chroma.mean(axis=1)
    if c.sum() <= 0:
        return "?", 0.0
    best, score = "?", -2.0
    for i in range(12):
        for prof, mode in ((MAJOR, ""), (MINOR, "m")):
            r = np.corrcoef(c, np.roll(prof, i))[0, 1]
            if r > score:
                best, score = NOTES[i] + mode, r
    return best, float(score)


def tempo_of(onset, start, end):
    seg = onset[start:end]
    if len(seg) < 8:
        return 0.0
    return float(np.atleast_1d(librosa.feature.tempo(onset_envelope=seg, sr=SR, hop_length=HOP))[0])


def novelty(feat, half):
    """Checkerboard-kernel novelty over a self-similarity matrix (Foote)."""
    f = feat / (np.linalg.norm(feat, axis=0, keepdims=True) + 1e-9)
    S = f.T @ f
    n = S.shape[0]
    k = np.ones((2 * half, 2 * half))
    k[:half, half:] = k[half:, :half] = -1
    g = np.exp(-0.5 * ((np.arange(2 * half) - half + 0.5) / (half / 2)) ** 2)
    k *= np.outer(g, g)
    out = np.zeros(n)
    for i in range(half, n - half):
        out[i] = (S[i - half:i + half, i - half:i + half] * k).sum()
    return np.maximum(out, 0)


def smooth(x, n):
    return np.convolve(x, np.ones(n) / n, mode="same") if n > 1 else x


def analyse(path, out_dir):
    y = load(path)
    dur = len(y) / SR
    S = np.abs(librosa.stft(y, n_fft=2048, hop_length=HOP))
    freqs = librosa.fft_frequencies(sr=SR, n_fft=2048)
    times = librosa.frames_to_time(np.arange(S.shape[1]), sr=SR, hop_length=HOP)
    fps = SR / HOP

    def band(lo, hi):
        e = (S[(freqs >= lo) & (freqs < hi)] ** 2).sum(axis=0)
        return librosa.power_to_db(smooth(e, int(fps / 4)) + 1e-10, ref=1.0)

    low, mid, high = band(20, 150), band(150, 2000), band(2000, SR / 2)
    rms = librosa.amplitude_to_db(smooth(librosa.feature.rms(S=S)[0], int(fps / 4)) + 1e-9)
    centroid = smooth(librosa.feature.spectral_centroid(S=S, sr=SR)[0], int(fps / 2))
    onset = librosa.onset.onset_strength(S=librosa.amplitude_to_db(S), sr=SR)
    chroma = librosa.feature.chroma_stft(S=S ** 2, sr=SR, hop_length=HOP)
    mfcc = librosa.feature.mfcc(S=librosa.power_to_db(librosa.feature.melspectrogram(S=S ** 2, sr=SR)), n_mfcc=13)
    _, beats = librosa.beat.beat_track(onset_envelope=onset, sr=SR, hop_length=HOP)
    beat_t = librosa.frames_to_time(beats, sr=SR, hop_length=HOP)

    # Local tempo curve (8 s windows).
    win = int(8 * fps)
    tempo_curve = librosa.feature.tempo(onset_envelope=onset, sr=SR, hop_length=HOP,
                                        aggregate=None, ac_size=8.0)

    # Transitions: novelty peaks on timbre + harmony, at 0.5 s resolution.
    step = int(fps / 2)
    agg = lambda m: np.stack([m[:, i:i + step].mean(axis=1) for i in range(0, m.shape[1] - step + 1, step)], axis=1)
    feat = np.vstack([librosa.util.normalize(agg(mfcc[1:]), axis=1), agg(chroma) * 2])
    half = 8 if dur > 30 else 5  # 4 s or 2.5 s each side
    nov = novelty(feat, half) if feat.shape[1] > 2 * half + 2 else np.zeros(feat.shape[1])
    nov_t = np.arange(len(nov)) * step / fps
    peaks = []
    if nov.max() > 0:
        thr = nov.mean() + 1.0 * nov.std()
        cand = [i for i in range(1, len(nov) - 1) if nov[i] >= nov[i - 1] and nov[i] >= nov[i + 1] and nov[i] > thr]
        for i in sorted(cand, key=lambda i: -nov[i]):
            if all(abs(nov_t[i] - nov_t[j]) >= 8 for j in peaks) and 4 < nov_t[i] < dur - 4:
                peaks.append(i)
        peaks = sorted(peaks)[:8]
    bounds = [0.0] + [float(nov_t[i]) for i in peaks] + [dur]

    def fr(t):
        return int(np.clip(t * fps, 0, S.shape[1] - 1))

    sections = []
    for a, b in zip(bounds[:-1], bounds[1:]):
        k, kr = key_of(chroma[:, fr(a):fr(b)])
        sections.append({"start": round(a, 1), "end": round(b, 1),
                         "bpm": round(tempo_of(onset, fr(a), fr(b)), 1), "key": k, "key_conf": round(kr, 2),
                         "loudness_db": round(float(np.median(rms[fr(a):fr(b)])), 1)})

    transitions = []
    for n, i in enumerate(peaks):
        t = float(nov_t[i])
        pre, post = sections[n], sections[n + 1]
        w = (fr(t - 8), fr(t), fr(t + 8))
        lo_pre, lo_post = low[w[0]:w[1]], low[w[1]:w[2]]
        lo_window = low[fr(t - 4):fr(t + 4)]
        lo_dip = float(np.median(np.r_[lo_pre, lo_post]) - lo_window.min()) if len(lo_window) else 0.0
        # How long the bass sat >6 dB under its surroundings: a bass swap / kill shows up here.
        base = float(np.median(np.r_[lo_pre, lo_post])) if len(lo_pre) + len(lo_post) else 0.0
        killed = float((low[fr(t - 6):fr(t + 6)] < base - 6).sum() / fps)
        cen = centroid[fr(t - 8):fr(t + 8)]
        sweep = float(cen.max() - cen.min()) if len(cen) else 0.0
        sweep_dir = "up" if len(cen) and np.argmax(cen) > np.argmin(cen) else "down"
        r = rms[fr(t - 4):fr(t + 4)]
        dip = float(np.median(rms[w[0]:w[2]]) - r.min()) if len(r) else 0.0
        hi_pre = float(np.median(high[fr(t - 4):fr(t)])) if fr(t) > fr(t - 4) else 0.0
        hi_post = float(np.median(high[fr(t):fr(t + 4)])) if fr(t + 4) > fr(t) else 0.0
        # Handover length: per 0.25 s, how far the sound has moved from song A's average to song B's,
        # rescaled so the steady parts either side read 0 and 1; the time spent climbing is the blend.
        q = int(fps / 4)
        hv = np.vstack([chroma, np.vstack([low, mid, high]) / 20])
        hv = np.stack([hv[:, i:i + q].mean(axis=1) for i in range(0, hv.shape[1] - q + 1, q)], axis=1)
        qi = lambda s: int(np.clip(s * 4, 0, hv.shape[1] - 1))
        ref_a = hv[:, qi(max(pre["start"], t - 10)):qi(t - 4) + 1].mean(axis=1)
        ref_b = hv[:, qi(t + 4):qi(min(post["end"], t + 10)) + 1].mean(axis=1)
        span = hv[:, qi(t - 8):qi(t + 8)]
        da = np.linalg.norm(span - ref_a[:, None], axis=0)
        db = np.linalg.norm(span - ref_b[:, None], axis=0)
        mix = smooth(da / (da + db + 1e-9), 2)[8:-8]  # drop the smoothing edges: now t-6 .. t+6
        lo, hi = np.median(mix[:12]), np.median(mix[-12:])
        blend = 0.0
        if len(mix) > 24 and hi - lo > 0.1:
            n_ = (mix - lo) / (hi - lo)
            top = int(np.argmax(n_ >= 0.9)) if (n_ >= 0.9).any() else len(n_) - 1
            below = np.where(n_[:top] <= 0.1)[0]
            blend = (top - (below[-1] if len(below) else 0)) / 4
        nb = float(beat_t[np.argmin(np.abs(beat_t - t))]) if len(beat_t) else t
        bpm_ratio = post["bpm"] / pre["bpm"] if pre["bpm"] else 0
        transitions.append({
            "time": round(t, 1),
            "from": f'{pre["bpm"]} BPM {pre["key"]}', "to": f'{post["bpm"]} BPM {post["key"]}',
            "bpm_ratio": round(bpm_ratio, 2),
            "style": "cut" if blend <= 1 else ("quick blend" if blend < 4 else "long blend"),
            "blend_s": blend,
            "on_beat_ms": round((t - nb) * 1000),
            "bass_dip_db": round(lo_dip, 1), "bass_killed_s": round(killed, 1),
            "filter_sweep_hz": round(sweep), "sweep_dir": sweep_dir,
            "loudness_dip_db": round(dip, 1),
            "highs_change_db": round(hi_post - hi_pre, 1),
            "loudness_change_db": round(post["loudness_db"] - pre["loudness_db"], 1),
        })

    name = path.stem
    plot(name, out_dir / f"{name}.png", y, times, low, mid, high, rms, centroid, tempo_curve, chroma,
         [tr["time"] for tr in transitions], 0, dur, beat_t)
    for n, tr in enumerate(transitions, 1):
        plot(f'{name} transition {n} @ {tr["time"]}s', out_dir / f"{name}_t{n}.png", y, times, low, mid, high,
             rms, centroid, tempo_curve, chroma, [tr["time"]], max(0, tr["time"] - 10), min(dur, tr["time"] + 10),
             beat_t)
    result = {"file": path.name, "duration_s": round(dur, 1), "overall_bpm": round(tempo_of(onset, 0, len(onset)), 1),
              "sections": sections, "transitions": transitions}
    (out_dir / f"{name}.json").write_text(json.dumps(result, indent=2))
    (out_dir / f"{name}.md").write_text(report(result))
    return result


def plot(title, out, y, times, low, mid, high, rms, centroid, tempo_curve, chroma, marks, t0, t1, beat_t):
    fig, ax = plt.subplots(5, 1, figsize=(14, 12), sharex=True,
                           gridspec_kw={"height_ratios": [3, 2, 1.2, 1.2, 1.5]})
    i0, i1 = np.searchsorted(times, t0), np.searchsorted(times, t1)
    M = librosa.power_to_db(librosa.feature.melspectrogram(y=y[int(t0 * SR):int(t1 * SR)], sr=SR, hop_length=HOP,
                                                           n_mels=128), ref=np.max)
    librosa.display.specshow(M, sr=SR, hop_length=HOP, x_axis="time", y_axis="mel", ax=ax[0],
                             x_coords=np.linspace(t0, t1, M.shape[1]), cmap="magma")
    ax[0].set_title(title)
    ax[1].plot(times[i0:i1], low[i0:i1], label="bass <150 Hz", color="#d62728")
    ax[1].plot(times[i0:i1], mid[i0:i1], label="mids", color="#2ca02c")
    ax[1].plot(times[i0:i1], high[i0:i1], label="highs >2 kHz", color="#1f77b4")
    ax[1].plot(times[i0:i1], rms[i0:i1] + 40, label="loudness (+40)", color="k", lw=0.8)
    ax[1].set_ylabel("dB")
    ax[1].legend(loc="upper right", fontsize=8)
    ax[2].plot(times[i0:i1], centroid[i0:i1], color="#9467bd")
    ax[2].set_ylabel("brightness Hz")
    tt = librosa.frames_to_time(np.arange(len(tempo_curve)), sr=SR, hop_length=HOP)
    j0, j1 = np.searchsorted(tt, t0), np.searchsorted(tt, t1)
    ax[3].plot(tt[j0:j1], tempo_curve[j0:j1], color="#ff7f0e")
    ax[3].set_ylabel("BPM")
    librosa.display.specshow(chroma[:, i0:i1], y_axis="chroma", x_axis="time", ax=ax[4], sr=SR, hop_length=HOP,
                             x_coords=times[i0:i1], cmap="Greys")
    ax[4].set_ylabel("pitch class")
    for a in ax:
        for m in marks:
            a.axvline(m, color="cyan" if a is ax[0] else "r", ls="--", lw=1.2)
    if t1 - t0 <= 25:
        for b in beat_t[(beat_t >= t0) & (beat_t <= t1)]:
            ax[1].axvline(b, color="grey", lw=0.3)
    ax[-1].set_xlabel("seconds")
    fig.tight_layout()
    fig.savefig(out, dpi=90)
    plt.close(fig)


def report(r):
    lines = [f'# {r["file"]}', "", f'{r["duration_s"]} s, overall about {r["overall_bpm"]} BPM.', "",
             "## Sections", "", "| from s | to s | BPM | key (confidence) | loudness dB |", "|---|---|---|---|---|"]
    for s in r["sections"]:
        lines.append(f'| {s["start"]} | {s["end"]} | {s["bpm"]} | {s["key"]} ({s["key_conf"]}) | {s["loudness_db"]} |')
    lines += ["", "## Transitions", ""]
    if not r["transitions"]:
        lines.append("No clear transition detected (single song, or a very long blend).")
    for n, t in enumerate(r["transitions"], 1):
        notes = []
        if t["bass_killed_s"] >= 1:
            notes.append(f'bass held down {t["bass_killed_s"]} s (bass swap or bass kill)')
        if t["filter_sweep_hz"] > 1500:
            notes.append(f'brightness swept {t["sweep_dir"]} by {t["filter_sweep_hz"]} Hz (filter sweep or riser)')
        if t["loudness_dip_db"] > 6:
            notes.append(f'loudness dipped {t["loudness_dip_db"]} dB (break, brake, echo-out or silence)')
        if r_ := t["bpm_ratio"]:
            if abs(r_ - 1) < 0.04:
                notes.append("same tempo")
            elif abs(r_ - 0.5) < 0.04 or abs(r_ - 2) < 0.08:
                notes.append("half/double-time link")
            else:
                notes.append(f"tempo jump x{r_}")
        lines += [f'### {n}. at {t["time"]} s: {t["from"]} -> {t["to"]}', "",
                  f'- Style: **{t["style"]}** (handover about {t["blend_s"]} s; 1 s or less is a cut)',
                  f'- Off the nearest beat by {t["on_beat_ms"]} ms',
                  f'- Bass dip {t["bass_dip_db"]} dB; highs change {t["highs_change_db"]} dB; '
                  f'loudness change {t["loudness_change_db"]} dB',
                  f'- Reads as: {"; ".join(notes) or "nothing unusual"}', ""]
    lines += ["Numbers are estimates: BPM can read half or double, and key confidence under 0.6 is weak.", ""]
    return "\n".join(lines)


def main():
    folder = Path(sys.argv[1] if len(sys.argv) > 1 else "research/adi_wav")
    out = folder / "analysis"
    out.mkdir(parents=True, exist_ok=True)
    files = sorted(p for p in folder.iterdir() if p.suffix.lower() in MEDIA)
    if not files:
        sys.exit(f"No audio or video files in {folder}")
    summary = ["# Transition analysis", ""]
    for p in files:
        print("analysing", p.name, flush=True)
        try:
            r = analyse(p, out)
        except Exception as e:  # keep going on one bad file
            print("  failed:", e)
            summary.append(f"- {p.name}: failed ({e})")
            continue
        summary.append(f'- [{p.name}]({p.stem}.md): {r["duration_s"]} s, {len(r["transitions"])} transitions')
        for t in r["transitions"]:
            summary.append(f'  - {t["time"]} s {t["style"]}: {t["from"]} -> {t["to"]}, bass held down '
                           f'{t["bass_killed_s"]} s, sweep {t["filter_sweep_hz"]} Hz, dip {t["loudness_dip_db"]} dB')
    (out / "summary.md").write_text("\n".join(summary) + "\n")
    print("wrote", out)


if __name__ == "__main__":
    main()
