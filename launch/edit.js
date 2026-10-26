// Cuts the FireTheDJ launch video, on the beat, in both shapes: 1080x1920 (Reels/Shorts) and
// 1920x1080 (YouTube/GitHub). Every scene lasts whole bars of the 124 BPM beat.
// Usage: node edit.js <ffmpeg.exe>      Output: launch/FireTheDJ-launch-9x16.mp4, -16x9.mp4
"use strict";
const { spawnSync } = require("child_process");
const path = require("path");

const FF = process.argv[2];
const D = __dirname, C = p => path.join(D, "clips", p), K = p => path.join(D, "cards", p);
const BAR = 60 / 124 * 4, TWO = 2 * BAR, TOTAL = 32.0;

// Each scene: the clip, where in it to start, how long, and its caption (or none).
// Visualizer scenes use a portrait recording for 9:16 and a landscape one for 16:9.
const SCENES = [
  { clip: "splash.mp4", ss: 0.9, dur: TWO },
  { clip: "library.mp4", ss: 0.6, dur: TWO, cap: "crates" },
  { clip: "nowplaying.mp4", ss: 0.6, dur: TWO, cap: "set" },
  // The mixing view is on screen for 2.83 s of a real transition: slowed to fill its two bars.
  { clip: "mixing.mp4", ss: 0.86, dur: TWO, speed: 2.82 / TWO, cap: "mix" },
  { vis: "club", ss: 0.5, dur: BAR, cap: "vis" },
  { vis: "tunnel", ss: 0.5, dur: BAR, cap: "vis" },
  { vis: "haze", ss: 0.5, dur: BAR, cap: "tv" },
  { vis: "led", ss: 0.5, dur: BAR, cap: "tv" },
  { clip: "ytmusic.mp4", ss: 0.8, dur: TWO, cap: "sources" },
  { end: true, dur: TOTAL - (5 * TWO + 4 * BAR) },
];

const LAYOUT = {
  v: { W: 1080, H: 1920, phone: { w: 724, h: 1460, x: 178, y: 400 } },
  h: { W: 1920, H: 1080, phone: { w: 486, h: 980, x: 1270, y: 50 } },
};

function build(shape) {
  const { W, H, phone } = LAYOUT[shape];
  const args = ["-hide_banner", "-loglevel", "error", "-y"];
  const graph = [];
  let n = 0;
  const input = (file, extra = []) => { args.push(...extra, "-i", file); return n++; };
  const bg = input(K(`${shape}_bg.png`), ["-loop", "1", "-t", String(TOTAL)]);
  const mask = input(K(`${shape}_mask.png`), ["-loop", "1", "-t", String(TOTAL)]);
  graph.push(`[${bg}:v]split=${SCENES.length}${SCENES.map((_, i) => `[bg${i}]`).join("")}`);
  graph.push(`[${mask}:v]format=gray,split=${SCENES.length}${SCENES.map((_, i) => `[m${i}]`).join("")}`);

  SCENES.forEach((s, i) => {
    const out = `s${i}`;
    if (s.end) {
      const e = input(K(`${shape}_end.png`), ["-loop", "1", "-t", s.dur.toFixed(3)]);
      graph.push(`[${e}:v]format=rgba,fps=30,fade=in:st=0:d=0.35,fade=out:st=${(s.dur - 0.8).toFixed(3)}:d=0.8,setsar=1[${out}]`);
      graph.push(`[bg${i}]nullsink`); graph.push(`[m${i}]nullsink`);
      return;
    }
    const file = s.vis ? C(`vis_${s.vis}_${shape}.mp4`) : C(s.clip);
    const speed = s.speed || 1;
    const v = input(file, ["-ss", String(s.ss), "-t", (s.dur * speed).toFixed(3)]);
    let chain;
    if (s.vis) {
      // Full-bleed visualizer: fill the frame, crop the overflow.
      chain = `[${v}:v]scale=${W}:${H}:force_original_aspect_ratio=increase,crop=${W}:${H},fps=30,setsar=1[v${i}]`;
      graph.push(chain);
      graph.push(`[bg${i}]nullsink`); graph.push(`[m${i}]nullsink`);
    } else {
      // The phone's screen without its status and navigation bars, rounded, on the warm ground.
      graph.push(`[${v}:v]setpts=(PTS-STARTPTS)/${speed.toFixed(4)},crop=1080:2180:0:100,scale=${phone.w}:${phone.h},fps=30,format=rgba[c${i}]`);
      graph.push(`[c${i}][m${i}]alphamerge[r${i}]`);
      graph.push(`[bg${i}]trim=duration=${s.dur.toFixed(3)},fps=30,setpts=PTS-STARTPTS[g${i}]`);
      graph.push(`[g${i}][r${i}]overlay=${phone.x}:${phone.y}:shortest=1,setsar=1[v${i}]`);
    }
    if (s.cap) {
      const capFile = s.vis && shape === "h" ? `hv_${s.cap}` : `${shape}_${s.cap}`;
      const c = input(K(`${capFile}.png`), ["-loop", "1", "-t", s.dur.toFixed(3)]);
      // A caption fades in only when it first appears, not again on each visualizer cut.
      const fresh = i === 0 || SCENES[i - 1].cap !== s.cap;
      graph.push(`[${c}:v]format=rgba,fps=30${fresh ? ",fade=in:st=0:d=0.3:alpha=1" : ""}[k${i}]`);
      graph.push(`[v${i}][k${i}]overlay=0:0:shortest=1,format=yuv420p[${out}]`);
    } else {
      graph.push(`[v${i}]format=yuv420p[${out}]`);
    }
  });
  graph.push(`${SCENES.map((_, i) => `[s${i}]`).join("")}concat=n=${SCENES.length}:v=1:a=0,fade=in:st=0:d=0.4,format=yuv420p[vout]`);
  const a = input(path.join(D, "beat.wav"));
  graph.push(`[${a}:a]atrim=0:${TOTAL},afade=out:st=${TOTAL - 1}:d=1,alimiter=limit=0.89:level=false[aout]`);

  const out = path.join(D, `FireTheDJ-launch-${shape === "v" ? "9x16" : "16x9"}.mp4`);
  args.push("-filter_complex", graph.join(";"), "-map", "[vout]", "-map", "[aout]",
    "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", "30",
    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-t", String(TOTAL), out);
  const r = spawnSync(FF, args, { stdio: "inherit" });
  if (r.status !== 0) process.exit(r.status || 1);
  console.log("wrote", out);
}

for (const shape of (process.argv[3] || "v,h").split(",")) build(shape);
