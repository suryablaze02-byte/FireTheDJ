// Cuts the FireTheDJ 1.3 → 1.5.1 explainer reel (1080x1920, for Instagram Reels and YouTube
// Shorts) from the phone's screen recordings, the voiceover reel-voice.py made and the music bed
// reel-beat.js made. Each scene's footage runs exactly as long as its narration; the phone's
// screen sits below a header band with the feature's name, and the narration is captioned.
// Usage: node reel-edit.js <ffmpeg.exe> <work dir> <out.mp4>
//   The work dir holds rec_*.mp4, voice.wav, timing.json, beat.wav, bg.png, end.png and the fonts
//   fb.ttf (bold) and fs.ttf (semibold); captions are written there as text files.
"use strict";
const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const [FF, DIR, OUT] = process.argv.slice(2);
const W = 1080, H = 1920, FPS = 30;
const LEAD = 0.5, CUT_EARLY = 0.1, HOLD = 2.6;
const SCREEN_Y = 420, SCREEN_H = 1500; // where a phone recording sits, and how much of it shows
const CAPTION_Y = 1230;

const timing = JSON.parse(fs.readFileSync(path.join(DIR, "timing.json"), "utf8"));
const voiceEnd = timing[timing.length - 1].end;
const TOTAL = LEAD + voiceEnd + HOLD;

// Footage for each scene: [recording, from (s), share of the scene, source y of the 1500 px window].
// "pan" footage is a sideways recording, panned across from deck A to deck B.
const SHOTS = {
  hook: [["acid", 0.5, 1, 450]],
  lyrics: [["beatpop", 1, 1, 700], ["karaoke", 8, 1, 800]],
  wordplay: [["np", 2, 1, 600]],
  trickster: [["word", 18.6, 1, 750]],
  regional: [["spotify", 6.4, 1, 500]],
  skip: [["skip", 3.6, 1, 600]],
  decks: [["fxview", 4, 1, "pan"]],
  acid: [["acid", 11, 1, 450]],
  stream: [["search", 5.2, 0.55, 200], ["spotify", 0, 0.45, 150]],
  talk: [["music", 4.4, 1, 500]],
  analysis: [["settings", 2, 1, 500]],
  updates: [["whatsnew", 3, 1, 800]],
  end: [["END"]],
};
const KICKER = { hook: "FIRETHEDJ · 1.3 → 1.5.1", end: "" };
// Big words on screen (lyrics, effect names) sit above the caption; sideways footage sits higher, its caption below it.
const CAPTION_AT = { decks: 1420 };
const PAN_Y = SCREEN_Y + 50;

// Scene spans: each cuts in just before its narration starts.
const scenes = timing.map((s, i) => ({
  ...s,
  from: i === 0 ? 0 : LEAD + s.start - CUT_EARLY,
  to: i === timing.length - 1 ? TOTAL : LEAD + timing[i + 1].start - CUT_EARLY,
}));

const args = ["-hide_banner", "-loglevel", "error", "-y"];
const graph = [];
let inputs = 0;
const input = (...a) => { args.push(...a); return inputs++; };

const bg = input("-loop", "1", "-framerate", String(FPS), "-t", TOTAL.toFixed(3), "-i", "bg.png");
const end = input("-loop", "1", "-framerate", String(FPS), "-t", TOTAL.toFixed(3), "-i", "end.png");
const pieces = [];
scenes.forEach(sc => {
  const shots = SHOTS[sc.id];
  const shares = shots.reduce((n, s) => n + (s[2] || 1), 0);
  let left = sc.to - sc.from;
  shots.forEach((shot, k) => {
    const dur = k === shots.length - 1 ? left : (sc.to - sc.from) * shot[2] / shares;
    left -= dur;
    const label = `p${pieces.length}`;
    const d = dur.toFixed(3);
    if (shot[0] === "END") {
      graph.push(`[${end}:v]trim=duration=${d},setpts=PTS-STARTPTS,fps=${FPS},format=yuv420p,fade=in:st=0:d=0.35[${label}]`);
    } else {
      const v = input("-ss", String(shot[1]), "-i", `rec_${shot[0]}.mp4`);
      // Screen recordings stop when the screen does: hold the last frame for the rest.
      const src = `[${v}:v]fps=${FPS},tpad=stop_mode=clone:stop_duration=30,trim=duration=${d},setpts=PTS-STARTPTS`;
      const back = `[${bg}:v]trim=duration=${d},setpts=PTS-STARTPTS[b${label}]`;
      graph.push(back);
      if (shot[3] === "pan") {
        graph.push(`${src},scale=2000:900,crop=1080:900:'(iw-1080)*t/${d}':0[s${label}]`);
        graph.push(`[b${label}][s${label}]overlay=0:${PAN_Y},format=yuv420p${k ? "" : ",fade=in:st=0:d=0.15"}[${label}]`);
      } else {
        graph.push(`${src},crop=1080:${SCREEN_H}:0:${shot[3]}[s${label}]`);
        graph.push(`[b${label}][s${label}]overlay=0:${SCREEN_Y},format=yuv420p${k ? "" : ",fade=in:st=0:d=0.15"}[${label}]`);
      }
    }
    pieces.push(label);
  });
});
graph.push(`${pieces.map(p => `[${p}]`).join("")}concat=n=${pieces.length}:v=1:a=0[cut]`);

// Titles and captions, drawn over the whole cut by time. Texts go through files: no escaping.
let n = 0;
const textFile = t => {
  const f = `t${n++}.txt`;
  fs.writeFileSync(path.join(DIR, f), t, "utf8");
  return f;
};
const draws = [];
for (const sc of scenes) {
  if (sc.id === "end") continue;
  const on = `enable='between(t,${sc.from.toFixed(3)},${(sc.to - 0.02).toFixed(3)})'`;
  const fadeIn = `alpha='min(1,(t-${sc.from.toFixed(3)})/0.25)'`;
  const kicker = KICKER[sc.id] ?? `NEW IN ${sc.kicker}`;
  draws.push(`drawtext=fontfile=fs.ttf:textfile=${textFile(kicker)}:fontsize=36:fontcolor=0xFFC98A:x=(w-tw)/2:y=170:${fadeIn}:${on}`);
  draws.push(`drawtext=fontfile=fb.ttf:textfile=${textFile(sc.headline)}:fontsize=96:fontcolor=white:x=(w-tw)/2:y=228:${fadeIn}:${on}`);
}
// Captions: each sentence in short chunks, timed by their share of its letters. The end card says it already.
for (const sc of scenes) {
  if (sc.id === "end") continue;
  for (const s of sc.sentences) {
    const chunks = chunk(s.text);
    const letters = chunks.reduce((a, c) => a + c.length, 0);
    let t = LEAD + s.start;
    const y = CAPTION_AT[sc.id] ?? CAPTION_Y;
    for (const c of chunks) {
      const d = (s.end - s.start) * c.length / letters;
      draws.push(
        `drawtext=fontfile=fb.ttf:textfile=${textFile(c)}:fontsize=62:fontcolor=white:box=1:boxcolor=0x0B0710@0.72:boxborderw=26:` +
        `x=(w-tw)/2:y=${y}:enable='between(t,${t.toFixed(3)},${(t + d).toFixed(3)})'`,
      );
      t += d;
    }
  }
}
graph.push(`[cut]${draws.join(",")},format=yuv420p[vout]`);

// Sound: the voice from LEAD, the music under it, ducking while she speaks.
const voice = input("-i", "voice.wav");
const music = input("-i", "beat.wav");
graph.push(`[${voice}:a]aresample=48000,adelay=${LEAD * 1000}:all=1,apad,atrim=0:${TOTAL.toFixed(3)},asplit=2[vo][key]`);
graph.push(`[${music}:a]aresample=48000,atrim=0:${TOTAL.toFixed(3)},volume=0.55,afade=in:d=0.4,afade=out:st=${(TOTAL - 2.2).toFixed(3)}:d=2.2[mu]`);
graph.push(`[mu][key]sidechaincompress=threshold=0.02:ratio=6:attack=15:release=350:makeup=1[ducked]`);
graph.push(`[vo]aformat=channel_layouts=stereo[vos];[ducked]aformat=channel_layouts=stereo[dus];[vos][dus]amix=inputs=2:duration=first:normalize=0,aformat=channel_layouts=stereo,loudnorm=I=-14:TP=-1.5:LRA=11[aout]`);

fs.writeFileSync(path.join(DIR, "graph.txt"), graph.join(";\n"));
args.push("-/filter_complex", "graph.txt", "-map", "[vout]", "-map", "[aout]",
  "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", String(FPS),
  "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-movflags", "+faststart", "-t", TOTAL.toFixed(3), OUT);
const r = spawnSync(FF, args, { cwd: DIR, stdio: "inherit" });
if (r.status !== 0) process.exit(r.status || 1);
console.log(`wrote ${OUT}: ${TOTAL.toFixed(2)} s, ${scenes.length} scenes, ${pieces.length} shots`);

/** A sentence in caption-sized pieces: up to about 24 letters, broken after punctuation where it can. */
function chunk(text) {
  const words = text.split(/\s+/);
  const out = [];
  let cur = "";
  for (const w of words) {
    const next = cur ? `${cur} ${w}` : w;
    if (cur && next.length > 24) {
      out.push(cur);
      cur = w;
    } else {
      cur = next;
    }
    if (/[.,?!]$/.test(w) && cur.length >= 10) {
      out.push(cur);
      cur = "";
    }
  }
  if (cur) out.push(cur);
  return out;
}
