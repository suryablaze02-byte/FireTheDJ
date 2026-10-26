// Cuts the FireTheDJ 1.1 launch video (the TV app and what's new), on the beat, in both shapes:
// 1080x1920 (Reels/Shorts) and 1920x1080 (YouTube/GitHub). Same beat and look as edit.js.
// Usage: node edit-1.1.js <ffmpeg.exe> [v,h]    Output: launch/FireTheDJ-1.1-9x16.mp4, -16x9.mp4
"use strict";
const { spawnSync } = require("child_process");
const path = require("path");

const FF = process.argv[2];
const D = __dirname, C = p => path.join(D, "clips", p), K = p => path.join(D, "cards", p);
const BAR = 60 / 124 * 4, TWO = 2 * BAR, TOTAL = 32.0;

// Scenes: a TV recording on the TV, a phone recording on the phone, or a full-screen visualizer
// (portrait recording for 9:16, landscape for 16:9). Captions are cards/<shape>_<cap>.png.
const SCENES = [
  { tv: "tv_splash.mp4", ss: 2.2, dur: TWO, cap: "tvnew" },
  { tv: "tv_home.mp4", ss: 0.6, dur: TWO, cap: "tvhome" },
  { vis: "club", ss: 0.5, dur: BAR, cap: "tvvis" },
  { vis: "tunnel", ss: 0.5, dur: BAR, cap: "tvvis" },
  { vis: "haze", ss: 0.5, dur: BAR, cap: "tvrot" },
  { vis: "led", ss: 0.5, dur: BAR, cap: "tvrot" },
  { tv: "tv_ctrl.mp4", ss: 0.1, dur: TWO, cap: "tvctrl" },
  { phone: "tour.mp4", ss: 4.2, dur: TWO, cap: "tour" },
  { phone: "nowplaying.mp4", ss: 0.6, dur: TWO, cap: "live" },
  { end: true, dur: TOTAL - (5 * TWO + 4 * BAR) },
];

// Kept in step with cards.ps1 (the masks and the TV grounds).
const LAYOUT = {
  v: { W: 1080, H: 1920, phone: { w: 724, h: 1460, x: 178, y: 400 }, tv: { w: 1000, h: 562, x: 40, y: 760 } },
  h: { W: 1920, H: 1080, phone: { w: 486, h: 980, x: 1270, y: 50 }, tv: { w: 1520, h: 855, x: 200, y: 50 } },
};

function build(shape) {
  const { W, H, phone, tv } = LAYOUT[shape];
  const args = ["-hide_banner", "-loglevel", "error", "-y"];
  const graph = [];
  let n = 0;
  const input = (file, extra = []) => { args.push(...extra, "-i", file); return n++; };
  const still = (file, dur) => input(K(file), ["-loop", "1", "-t", dur.toFixed(3)]);

  SCENES.forEach((s, i) => {
    const out = `s${i}`;
    if (s.end) {
      const e = still(`${shape}_end11.png`, s.dur);
      graph.push(`[${e}:v]format=rgba,fps=30,fade=in:st=0:d=0.35,fade=out:st=${(s.dur - 0.8).toFixed(3)}:d=0.8,setsar=1,format=yuv420p[${out}]`);
      return;
    }
    const file = s.vis ? C(`vis_${s.vis}_${shape}.mp4`) : C(s.tv || s.phone);
    const v = input(file, ["-ss", String(s.ss), "-t", s.dur.toFixed(3)]);
    if (s.vis) {
      // Full-bleed visualizer: fill the frame, crop the overflow.
      graph.push(`[${v}:v]scale=${W}:${H}:force_original_aspect_ratio=increase,crop=${W}:${H},fps=30,setsar=1[v${i}]`);
    } else if (s.tv) {
      const bg = still(`${shape}_tvbg.png`, s.dur);
      graph.push(`[${v}:v]setpts=PTS-STARTPTS,scale=${tv.w}:${tv.h}:flags=lanczos,fps=30[c${i}]`);
      graph.push(`[${bg}:v]fps=30[g${i}]`);
      graph.push(`[g${i}][c${i}]overlay=${tv.x}:${tv.y}:shortest=1,setsar=1[v${i}]`);
    } else {
      // The phone's screen without its status and navigation bars, rounded, on the warm ground.
      const bg = still(`${shape}_bg.png`, s.dur), mask = still(`${shape}_mask.png`, s.dur);
      graph.push(`[${v}:v]setpts=PTS-STARTPTS,crop=1080:2180:0:100,scale=${phone.w}:${phone.h},fps=30,format=rgba[c${i}]`);
      graph.push(`[${mask}:v]format=gray,fps=30[m${i}]`);
      graph.push(`[c${i}][m${i}]alphamerge[r${i}]`);
      graph.push(`[${bg}:v]fps=30[g${i}]`);
      graph.push(`[g${i}][r${i}]overlay=${phone.x}:${phone.y}:shortest=1,setsar=1[v${i}]`);
    }
    // Landscape captions sit beside the phone, and low and centred over a TV or a visualizer.
    const capFile = shape === "h" && !s.phone ? `hv_${s.cap}` : `${shape}_${s.cap}`;
    const c = still(`${capFile}.png`, s.dur);
    // A caption fades in only when it first appears, not again on each cut under it.
    const fresh = i === 0 || SCENES[i - 1].cap !== s.cap;
    graph.push(`[${c}:v]format=rgba,fps=30${fresh ? ",fade=in:st=0:d=0.3:alpha=1" : ""}[k${i}]`);
    graph.push(`[v${i}][k${i}]overlay=0:0:shortest=1,format=yuv420p[${out}]`);
  });
  graph.push(`${SCENES.map((_, i) => `[s${i}]`).join("")}concat=n=${SCENES.length}:v=1:a=0,fade=in:st=0:d=0.4,format=yuv420p[vout]`);
  const a = input(path.join(D, "beat.wav"));
  graph.push(`[${a}:a]atrim=0:${TOTAL},afade=out:st=${TOTAL - 1}:d=1,alimiter=limit=0.89:level=false[aout]`);

  const out = path.join(D, `FireTheDJ-1.1-${shape === "v" ? "9x16" : "16x9"}.mp4`);
  args.push("-filter_complex", graph.join(";"), "-map", "[vout]", "-map", "[aout]",
    "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", "30",
    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-t", String(TOTAL), out);
  const r = spawnSync(FF, args, { stdio: "inherit" });
  if (r.status !== 0) process.exit(r.status || 1);
  console.log("wrote", out);
}

for (const shape of (process.argv[3] || "v,h").split(",")) build(shape);
