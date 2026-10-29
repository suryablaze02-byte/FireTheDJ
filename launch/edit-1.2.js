// Cuts the FireTheDJ 1.2 launch film from the scenes film-1.2.py builds (rendered to PNG at 2x),
// on the bars of beat-1.2.wav, in both shapes: 1920x1080 (YouTube/X) and 1080x1920 (Reels/Shorts).
// Devices push in slowly, headlines settle, captions fade up and rise into place.
// Usage: node edit-1.2.js <ffmpeg.exe> <png dir> [h,v]   Output: launch/FireTheDJ-1.2-film-16x9.mp4, -9x16.mp4
"use strict";
const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const [FF, PNG] = [process.argv[2], process.argv[3]];
const D = __dirname;
const FPS = 30, BAR = 60 / 112 * 4, TAIL = 2.5;
const plan = JSON.parse(fs.readFileSync(path.join(PNG, "..", "html", "plan.json"), "utf8"));

// Scene lengths in frames, cut on the bars (rounded from the running total, so nothing drifts).
let bars = 0;
const scenes = plan.map((s, i) => {
  const from = Math.round(bars * BAR * FPS);
  bars += s.bars;
  let to = Math.round(bars * BAR * FPS);
  if (i === plan.length - 1) to += Math.round(TAIL * FPS);
  return { ...s, frames: to - from };
});
const TOTAL = scenes.reduce((n, s) => n + s.frames, 0) / FPS;

// How each kind moves: zoom from z0 to z1 over the scene.
const MOVE = { devices: [1.0, 1.07], headline: [1.05, 1.0], title: [1.08, 1.0], end: [1.0, 1.035] };

function build(shape) {
  const [W, H] = shape === "h" ? [1920, 1080] : [1080, 1920];
  const args = ["-hide_banner", "-loglevel", "error", "-y"];
  const graph = [];
  let n = 0;
  const still = (file, frames) => { args.push("-loop", "1", "-framerate", String(FPS), "-t", (frames / FPS).toFixed(4), "-i", file); return n++; };

  scenes.forEach((s, i) => {
    const [z0, z1] = MOVE[s.kind];
    const v = still(path.join(PNG, `${shape}_${s.name}.png`), s.frames);
    const z = `${z0}+(${z1 - z0})*on/${s.frames}`;
    // zoompan on the 2x render keeps the slow push smooth; it outputs at the final size.
    const fadeIn = i === 0 ? "fade=in:st=0:d=0.8" : "fade=in:st=0:d=0.18";
    const fadeOut = i === scenes.length - 1 ? `,fade=out:st=${(s.frames / FPS - 1.4).toFixed(3)}:d=1.4` : "";
    graph.push(`[${v}:v]zoompan=z='${z}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=${W}x${H}:fps=${FPS},${fadeIn}${fadeOut},setsar=1[z${i}]`);
    const cap = path.join(PNG, `${shape}_${s.name}_cap.png`);
    if (fs.existsSync(cap)) {
      const c = still(cap, s.frames);
      graph.push(`[${c}:v]format=rgba,fade=in:st=0.35:d=0.5:alpha=1[k${i}]`);
      graph.push(`[z${i}][k${i}]overlay=x=0:y='26*max(0,1-(t-0.35)/0.7)':eval=frame:shortest=1,format=yuv420p[s${i}]`);
    } else {
      graph.push(`[z${i}]format=yuv420p[s${i}]`);
    }
  });
  graph.push(`${scenes.map((_, i) => `[s${i}]`).join("")}concat=n=${scenes.length}:v=1:a=0[vout]`);
  args.push("-i", path.join(D, "beat-1.2.wav"));
  graph.push(`[${n}:a]atrim=0:${TOTAL.toFixed(3)},afade=out:st=${(TOTAL - 1.6).toFixed(3)}:d=1.6[aout]`);

  const out = path.join(D, `FireTheDJ-1.2-film-${shape === "h" ? "16x9" : "9x16"}.mp4`);
  args.push("-filter_complex", graph.join(";"), "-map", "[vout]", "-map", "[aout]",
    "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p", "-r", String(FPS),
    "-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart", "-t", TOTAL.toFixed(3), out);
  const r = spawnSync(FF, args, { stdio: "inherit" });
  if (r.status !== 0) process.exit(r.status || 1);
  console.log("wrote", out, TOTAL.toFixed(2), "s");
}

for (const shape of (process.argv[4] || "h,v").split(",")) build(shape);
