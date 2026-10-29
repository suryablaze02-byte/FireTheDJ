// FireTheDJ 1.3 explainer: renders each scene's backdrop (headline + device bezel) with headless
// Edge, masks the app footage into the device screen, crossfades the scenes and lays the Kokoro
// narration (voice.py) over a quiet bed of the launch beat. Both shapes: 16:9 and 9:16.
// Usage: node build.js <ffmpeg.exe> <msedge.exe>    Output: launch/FireTheDJ-1.3-explainer-16x9.mp4, -9x16.mp4
"use strict";
const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const [FF, EDGE] = [process.argv[2], process.argv[3]];
const D = __dirname;
const L = path.join(D, "..");
const OUT = path.join(D, "render");
fs.mkdirSync(OUT, { recursive: true });
const script = JSON.parse(fs.readFileSync(path.join(D, "script.json"), "utf8"));
const spoken = JSON.parse(fs.readFileSync(path.join(D, "wav", "lengths.json"), "utf8"));

const FPS = 30, XF = 0.5, LEAD = 0.35, GAP = 0.75, TAIL = 1.6;
// Each scene lasts its narration plus a breath; the last holds a little longer.
const dur = script.map((s, i) => LEAD + spoken[s.id] + (i === script.length - 1 ? TAIL : GAP));
const start = dur.map((_, i) => dur.slice(0, i).reduce((a, b) => a + b, 0));
const TOTAL = dur.reduce((a, b) => a + b, 0);

const FONT = "'Segoe UI Variable Display','Segoe UI',system-ui,sans-serif";
const GLOW = "radial-gradient(ellipse 60% 55% at 62% 52%,rgba(255,90,31,.22),transparent 70%)," +
  "radial-gradient(ellipse 45% 45% at 85% 20%,rgba(194,24,91,.16),transparent 70%)," +
  "radial-gradient(ellipse 45% 45% at 15% 85%,rgba(91,43,214,.18),transparent 70%),#07050A";

// Where the device screen sits, per shape and kind: x, y, w, h, corner radius.
function screen(shape, kind) {
  if (shape === "h") {
    if (kind === "phone" || kind === "title") return { x: 1260, y: 110, w: 387, h: 860, r: 46 };
    if (kind === "tv") return { x: 400, y: 330, w: 1120, h: 630, r: 14 };
  } else {
    if (kind === "phone" || kind === "title") return { x: 262, y: 600, w: 556, h: 1236, r: 64 };
    if (kind === "tv") return { x: 60, y: 760, w: 960, h: 540, r: 14 };
  }
  return null;
}

function page(shape, s) {
  const [W, H] = shape === "h" ? [1920, 1080] : [1080, 1920];
  const sc = screen(shape, s.kind);
  const h = shape === "h";
  let text;
  if (s.kind === "card") {
    text = `<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:${h ? 34 : 48}px;text-align:center;padding:0 80px">
      <div style="font-size:${h ? 132 : 112}px;font-weight:800;letter-spacing:-2px;line-height:1.05">${s.head}</div>
      <div style="font-size:${h ? 50 : 46}px;line-height:1.5;color:#D9D2DC">${s.sub}</div></div>`;
  } else if (h && (s.kind === "phone" || s.kind === "title")) {
    text = `<div style="position:absolute;left:150px;top:0;bottom:0;width:960px;display:flex;flex-direction:column;justify-content:center;gap:26px">
      <div style="font-size:${s.kind === "title" ? 120 : 100}px;font-weight:800;letter-spacing:-2px;line-height:1.05">${s.head}</div>
      <div style="font-size:46px;color:#D9D2DC;line-height:1.35">${s.sub}</div></div>`;
  } else if (h) {
    text = `<div style="position:absolute;left:0;right:0;top:70px;text-align:center">
      <div style="font-size:88px;font-weight:800;letter-spacing:-1.5px">${s.head}</div>
      <div style="font-size:42px;color:#D9D2DC;margin-top:8px">${s.sub}</div></div>`;
  } else {
    text = `<div style="position:absolute;left:70px;right:70px;top:${s.kind === "tv" ? 300 : 170}px;text-align:center">
      <div style="font-size:${s.kind === "title" ? 104 : 92}px;font-weight:800;letter-spacing:-1.5px;line-height:1.08">${s.head}</div>
      <div style="font-size:44px;color:#D9D2DC;margin-top:18px;line-height:1.35">${s.sub}</div></div>`;
  }
  const bezel = sc ? `<div style="position:absolute;left:${sc.x - 12}px;top:${sc.y - 12}px;width:${sc.w + 24}px;height:${sc.h + 24}px;border-radius:${sc.r + 12}px;background:#0E0E11;box-shadow:0 0 0 2px #3A3A42,0 50px 120px rgba(0,0,0,.7)"></div>` : "";
  return `<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;width:${W}px;height:${H}px;overflow:hidden}
body{font-family:${FONT};color:#F5F5F7;background:${GLOW};-webkit-font-smoothing:antialiased}
.fire{background:linear-gradient(90deg,#FF2D55,#FF6A1A 55%,#FFC24A);-webkit-background-clip:text;background-clip:text;color:transparent}
b{color:#FFB27A}
</style></head><body>${bezel}${text}</body></html>`;
}

/** The screen's rounded corners as a white-on-black matte (too narrow a window for Edge, so drawn by ffmpeg). */
function mask(sc, file) {
  const { w, h, r } = sc;
  const dx = `max(max(${r}-X,X-${w - 1 - r}),0)`;
  const dy = `max(max(${r}-Y,Y-${h - 1 - r}),0)`;
  run(["-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", `color=black:s=${w}x${h}`, "-frames:v", "1",
    "-vf", `format=gray,geq=lum='255*lte(pow(${dx},2)+pow(${dy},2),${r * r})'`, file]);
}

function shot(html, file, w, h) {
  const src = file.replace(/\.png$/, ".html");
  fs.writeFileSync(src, html);
  // Edge sometimes is still closing the last page's profile: wait and try again.
  for (let attempt = 0; attempt < 5 && !fs.existsSync(file); attempt++) {
    if (attempt > 0) Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1500);
    spawnSync(EDGE, ["--headless=new", "--disable-gpu", "--hide-scrollbars", `--user-data-dir=${path.join(OUT, "edge")}`,
      `--window-size=${w},${h}`, "--virtual-time-budget=3000", `--screenshot=${file}`, "file:///" + src.replace(/\\/g, "/")]);
  }
  if (!fs.existsSync(file)) throw new Error("render failed: " + file);
}

function run(args) {
  const r = spawnSync(FF, args, { stdio: "inherit" });
  if (r.status !== 0) throw new Error("ffmpeg failed");
}

// The narration: each scene's line starts a moment after its scene does.
function narration() {
  const file = path.join(OUT, "voice.wav");
  const args = ["-hide_banner", "-loglevel", "error", "-y"];
  script.forEach(s => args.push("-i", path.join(D, "wav", s.id + ".wav")));
  const parts = script.map((s, i) => `[${i}]adelay=${Math.round((start[i] + LEAD) * 1000)}:all=1[v${i}]`);
  const mix = script.map((_, i) => `[v${i}]`).join("") + `amix=inputs=${script.length}:normalize=0,apad=whole_dur=${TOTAL.toFixed(3)}[voice]`;
  args.push("-filter_complex", parts.concat(mix).join(";"), "-map", "[voice]", "-ar", "48000", "-ac", "2", file);
  run(args);
  return file;
}

function build(shape, voice) {
  const [W, H] = shape === "h" ? [1920, 1080] : [1080, 1920];
  const args = ["-hide_banner", "-loglevel", "error", "-y"];
  const graph = [];
  let n = 0;
  const input = (...a) => { args.push(...a); return n++; };
  const scenes = script.map((s, i) => {
    const len = dur[i] + (i < script.length - 1 ? XF : 0);
    const bgFile = path.join(OUT, `${shape}_${s.id}.png`);
    shot(page(shape, s), bgFile, W, H);
    const bg = input("-loop", "1", "-framerate", String(FPS), "-t", len.toFixed(3), "-i", bgFile);
    const sc = screen(shape, s.kind);
    let label = `[${bg}:v]`;
    if (sc && s.visual) {
      const mFile = path.join(OUT, `mask_${sc.w}x${sc.h}.png`);
      if (!fs.existsSync(mFile)) mask(sc, mFile);
      const media = path.join(L, s.visual);
      const still = /\.png$/.test(media);
      const m = still ? input("-loop", "1", "-framerate", String(FPS), "-t", len.toFixed(3), "-i", media)
        : input("-stream_loop", "-1", "-t", len.toFixed(3), "-i", media);
      const k = input("-loop", "1", "-framerate", String(FPS), "-t", len.toFixed(3), "-i", mFile);
      // Wide visualizer footage is cropped to the TV's 16:9 before it is scaled into the screen.
      const fit = `scale=${sc.w}:${sc.h}:force_original_aspect_ratio=increase,crop=${sc.w}:${sc.h}`;
      graph.push(`[${m}:v]fps=${FPS},${fit},format=rgba[m${i}]`, `[${k}:v]format=gray[k${i}]`, `[m${i}][k${i}]alphamerge[r${i}]`,
        `[${bg}:v][r${i}]overlay=${sc.x}:${sc.y}:shortest=1[s${i}]`);
      label = `[s${i}]`;
    }
    graph.push(`${label}fps=${FPS},format=yuv420p,setsar=1[c${i}]`);
    return `[c${i}]`;
  });
  // Crossfade scene to scene.
  let cur = scenes[0];
  for (let i = 1; i < scenes.length; i++) {
    const out = `[x${i}]`;
    graph.push(`${cur}${scenes[i]}xfade=transition=fade:duration=${XF}:offset=${(start[i]).toFixed(3)}${out}`);
    cur = out;
  }
  graph.push(`${cur}fade=t=in:st=0:d=0.6,fade=t=out:st=${(TOTAL - 0.8).toFixed(3)}:d=0.8[vid]`);
  const v = input("-i", voice);
  const beat = input("-stream_loop", "-1", "-i", path.join(L, "beat-1.2.wav"));
  graph.push(`[${beat}:a]atrim=0:${TOTAL.toFixed(3)},volume=0.12,afade=t=in:d=1,afade=t=out:st=${(TOTAL - 2.5).toFixed(3)}:d=2.5[bed]`,
    `[${v}:a][bed]amix=inputs=2:normalize=0,alimiter=limit=0.95[aud]`);
  const file = path.join(L, `FireTheDJ-1.3-explainer-${shape === "h" ? "16x9" : "9x16"}.mp4`);
  args.push("-filter_complex", graph.join(";"), "-map", "[vid]", "-map", "[aud]", "-t", TOTAL.toFixed(3),
    "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", file);
  run(args);
  console.log("wrote", file, TOTAL.toFixed(1) + " s");
}

const voice = narration();
for (const shape of (process.argv[4] || "h,v").split(",")) build(shape, voice);
