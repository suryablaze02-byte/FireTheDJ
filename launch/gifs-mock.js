// "Party GIFs" visualizer mockup: a wall of dancing GIF tiles that dance on the beat.
// The tiles hold original cartoon dancers standing in for real GIFs; each one's dance loop is
// timed to the beat (as the real look would retime a GIF's loop), the tiles pump on every kick,
// and the wall changes layout on every bar. 10 s at 1280x720 over beat-1.2.wav from bar 8.
// Usage: node gifs-mock.js <ffmpeg.exe> <overlay.png> <beat-1.2.wav> <out.mp4>
// With GIFS=<songs.json> (from fetch-giphy.js) it mocks a set instead: songs in several languages and tempos,
// each showing one GIPHY GIF at a time in its language, the ones whose moves best fit its beat, streamed
// and never saved. OVERLAY is then a folder for the per-bar overlays.
"use strict";
const { spawn, spawnSync } = require("child_process");
const path = require("path");

const [FF, OVERLAY, WAV, OUT] = process.argv.slice(2);
const W = 1280, H = 720, FPS = 30, DUR = 10, BEAT = 60 / 112, START = 32 * BEAT;
const px = new Float32Array(W * H * 3);
const rgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16) / 255);
const mul = (c, k) => c.map(v => Math.min(1, v * k));
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));

// ---- a tiny SDF rasteriser -------------------------------------------------------------------
function roundRect(x, y, w, h, r) {
  const cx = x + w / 2, cy = y + h / 2, hw = w / 2 - r, hh = h / 2 - r;
  return (X, Y) => {
    const qx = Math.abs(X - cx) - hw, qy = Math.abs(Y - cy) - hh;
    return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
  };
}
function ellipse(cx, cy, rx, ry, rot = 0) {
  const c = Math.cos(rot), s = Math.sin(rot), m = Math.min(rx, ry);
  return (X, Y) => {
    const dx = X - cx, dy = Y - cy, lx = dx * c + dy * s, ly = -dx * s + dy * c;
    return (Math.hypot(lx / rx, ly / ry) - 1) * m;
  };
}
/** A tapered capsule from a (radius r1) to b (radius r2): iq's round cone, in 2D. */
function cone(ax, ay, bx, by, r1, r2) {
  const bax = bx - ax, bay = by - ay, l2 = bax * bax + bay * bay || 1e-6, rr = r1 - r2, a2 = l2 - rr * rr, il2 = 1 / l2;
  return (X, Y) => {
    const pax = X - ax, pay = Y - ay, y = pax * bax + pay * bay, z = y - l2;
    const qx = pax * l2 - bax * y, qy = pay * l2 - bay * y, x2 = qx * qx + qy * qy, y2 = y * y * l2, z2 = z * z * l2;
    const k = Math.sign(rr) * rr * rr * x2;
    if (Math.sign(z) * a2 * z2 > k) return Math.sqrt(x2 + z2) * il2 - r2;
    if (Math.sign(y) * a2 * y2 < k) return Math.sqrt(x2 + y2) * il2 - r1;
    return (Math.sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;
  };
}
/** Fills a shape (with an optional cartoon outline), clipped to a tile. */
function fill(box, sdf, color, o = {}) {
  const ow = o.ow || 0, alpha = o.alpha ?? 1, clip = o.clip;
  let [x0, y0, x1, y1] = box.map(Math.round);
  const pad = Math.ceil(ow) + 2;
  x0 -= pad; y0 -= pad; x1 += pad; y1 += pad;
  if (clip) { x0 = Math.max(x0, clip.x | 0); y0 = Math.max(y0, clip.y | 0); x1 = Math.min(x1, Math.ceil(clip.x + clip.w)); y1 = Math.min(y1, Math.ceil(clip.y + clip.h)); }
  x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(W - 1, x1); y1 = Math.min(H - 1, y1);
  const fn = typeof color === "function";
  for (let Y = y0; Y <= y1; Y++) for (let X = x0; X <= x1; X++) {
    const d = sdf(X + .5, Y + .5), outer = clamp(.5 - (d - ow));
    if (outer <= 0) continue;
    let a = outer * alpha;
    if (clip) { a *= clamp(.5 - clip.sdf(X + .5, Y + .5)); if (a <= 0) continue; }
    let c = fn ? color(X, Y) : color;
    if (ow) c = mix(o.outline, c, clamp(.5 - d) / outer);
    const i = (Y * W + X) * 3;
    px[i] += (c[0] - px[i]) * a; px[i + 1] += (c[1] - px[i + 1]) * a; px[i + 2] += (c[2] - px[i + 2]) * a;
  }
}
const circle = (cx, cy, r) => [[cx - r, cy - r, cx + r, cy + r], ellipse(cx, cy, r, r)];

// ---- dances: a pose for every beat position. Angles are from straight down, + is outward. ----
const sq = x => Math.tanh(3 * x);
const pos = Math.max.bind(null, 0);
const HIPS = [.9, -1.9];
const MOVES = {
  wave(b) { const s = Math.sin(Math.PI * b); return { bounce: .04 * Math.abs(s), tilt: .08 * s, aL: [.35 + 2.3 * pos(s), .4], aR: [.35 + 2.3 * pos(-s), .4], lL: [.12, 0], lR: [.12, 0], look: s }; },
  twist(b) {
    const s = Math.sin(Math.PI * b);
    return { bounce: .03 * Math.abs(Math.cos(Math.PI * b)), tilt: .22 * s, aL: [.9 + .5 * s, 1.5], aR: [.9 - .5 * s, 1.5], lL: [.16 + .1 * s, .25], lR: [.16 - .1 * s, .25], look: -s };
  },
  disco(b) { const s = sq(Math.sin(Math.PI * b)); return { bounce: .03 * Math.abs(s), tilt: .12 * s, aR: [1.6 + 1.1 * s, .05], aL: HIPS, lL: [.12 + .12 * pos(-s), 0], lR: [.2, 0], look: s }; },
  jacks(b) { const o = (1 + Math.cos(2 * Math.PI * b)) / 2; return { bounce: .1 * (1 - o), tilt: 0, aL: [.25 + 2.5 * o, .1 * o], aR: [.25 + 2.5 * o, .1 * o], lL: [.06 + .3 * o, 0], lR: [.06 + .3 * o, 0], look: 0 }; },
  robot(b) {
    const P = [[[1.57, 1.57], [.3, 0], -1], [[.3, 0], [1.57, -1.57], 1], [[1.57, -1.57], [1.57, 1.57], -1], [[2.9, 0], [.3, 0], 1]];
    const [aL, aR, look] = P[Math.floor(b * 2) % 4];
    return { bounce: .02 * Math.exp(-(b * 2 % 1) * 6), tilt: 0, aL, aR, lL: [.14, 0], lR: [.14, 0], look };
  },
  kick(b) { const k = Math.sin(Math.PI * b); return { bounce: .05 * Math.abs(k), tilt: -.1 * k, aL: HIPS, aR: HIPS, lL: [.1 + 1.1 * pos(k), .3 * pos(k)], lR: [.1 + 1.1 * pos(-k), .3 * pos(-k)], look: k }; },
  shuffle(b) {
    const s = Math.sin(Math.PI * b);
    return { bounce: .05 * Math.abs(Math.sin(2 * Math.PI * b)), tilt: .06 * s, aL: [.5 + .5 * s, 1.8], aR: [.5 - .5 * s, 1.8], lL: [.1 + .3 * pos(s), .2 * pos(s)], lR: [.1 + .3 * pos(-s), .2 * pos(-s)], look: s };
  },
};

// ---- the dancers ----------------------------------------------------------------------------
const INK = rgb("#1A0F1E"), GLOVE = rgb("#FFF6EE"), SHOE = rgb("#2A1F33"), MOUTH = rgb("#5A0F22"), CHEEK = rgb("#FF6F91");
const CAST = [
  { kind: "flame", move: "disco" },
  { kind: "blob", color: "#7CF2FF", move: "wave" },
  { kind: "bean", color: "#FFE14D", move: "twist" },
  { kind: "blob", color: "#FF9BE0", move: "jacks" },
  { kind: "bean", color: "#B6FF6B", move: "robot" },
  { kind: "blob", color: "#FFB07A", move: "kick" },
  { kind: "flame", move: "shuffle" },
  { kind: "bean", color: "#D2B8FF", move: "wave" },
];
const BODY = { // body shape, face height, shoulder spot (relative to the hips)
  blob: { face: -.27, sh: [.2, -.3] },
  bean: { face: -.38, sh: [.15, -.36] },
  flame: { face: -.22, sh: [.17, -.24] },
};

function dancer(c, b, t, fx, fy, u, clip, move) {
  const p = MOVES[move || c.move](b), ph = ((b % 1) + 1) % 1, hit = Math.exp(-ph * 6);
  const ow = Math.max(2, u * .016), o = { ow, outline: INK, clip };
  const base = c.kind === "flame" ? rgb("#FF8A2A") : rgb(c.color), limb = mul(base, .8);
  const hx = fx, hy = fy - (.3 + p.bounce) * u;
  const ct = Math.cos(p.tilt), st = Math.sin(p.tilt);
  const R = (lx, ly) => [hx + (lx * ct - ly * st) * u, hy + (lx * st + ly * ct) * u]; // body frame, turned by the tilt
  const box = (...pts) => { const xs = pts.filter((_, i) => i % 2 === 0), ys = pts.filter((_, i) => i % 2); return [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)]; };
  const seg = (ax, ay, bx, by, r1, r2, col) => fill([...box(ax, ay, bx, by).slice(0, 2).map(v => v - r1 * u), ...box(ax, ay, bx, by).slice(2).map(v => v + r1 * u)], cone(ax, ay, bx, by, r1 * u, r2 * u), col, o);

  // shadow
  fill([fx - .3 * u, fy - .06 * u, fx + .3 * u, fy + .06 * u], ellipse(fx, fy + .01 * u, .22 * u * (1 - p.bounce * 2), .035 * u), [0, 0, 0], { alpha: .28, clip });
  // legs (not tilted: they stand on the floor)
  for (const [side, [a, k]] of [[-1, p.lL], [1, p.lR]]) {
    const jx = hx + side * .08 * u, kx = jx + side * Math.sin(a) * .16 * u, ky = hy + Math.cos(a) * .16 * u;
    const ax = kx + side * Math.sin(a + k) * .15 * u, ay = ky + Math.cos(a + k) * .15 * u;
    seg(jx, hy, kx, ky, .05, .045, limb); seg(kx, ky, ax, ay, .045, .04, limb);
    fill([ax - .12 * u, ay - .06 * u, ax + .12 * u, ay + .08 * u], ellipse(ax + side * .03 * u, ay + .02 * u, .075 * u, .042 * u), SHOE, o);
  }
  // body
  const shade = (k0, k1) => (X, Y) => mul(base, k0 + (k1 - k0) * clamp((Y - (hy - .6 * u)) / (.6 * u)));
  if (c.kind === "blob") { const [x, y] = R(0, -.22); fill([x - .3 * u, y - .32 * u, x + .3 * u, y + .32 * u], ellipse(x, y, .24 * u, .26 * u, p.tilt), shade(1.15, .85), o); }
  if (c.kind === "bean") { const [x, y] = R(0, -.28); fill([x - .3 * u, y - .4 * u, x + .3 * u, y + .4 * u], ellipse(x, y, .17 * u, .32 * u, p.tilt), shade(1.15, .85), o); }
  if (c.kind === "flame") {
    const wob = Math.sin(t * 9) * .04, fire = (X, Y) => mix(rgb("#FFD23A"), rgb("#FF3D2E"), clamp((hy - Y) / (.75 * u) - .05));
    const parts = [[-.12, -.2, -.24 + wob, -.52, .1, .02], [.12, -.2, .22 + wob, -.46, .09, .02], [0, -.17, wob * 1.5, -.7, .22, .03]];
    for (const [ax0, ay0, bx0, by0, r1, r2] of parts) { const [ax, ay] = R(ax0, ay0), [bx, by] = R(bx0, by0); seg(ax, ay, bx, by, r1, r2, fire); }
  }
  // arms, in front of the body
  const [sx0, sy0] = BODY[c.kind].sh;
  for (const [side, [a, e]] of [[-1, p.aL], [1, p.aR]]) {
    const [sx, sy] = R(side * sx0, sy0);
    const d1 = [side * Math.sin(a), Math.cos(a)], d2 = [side * Math.sin(a + e), Math.cos(a + e)];
    const rot = ([x, y]) => [x * ct - y * st, x * st + y * ct];
    const [ux, uy] = rot(d1), [vx, vy] = rot(d2);
    const ex = sx + ux * .17 * u, ey = sy + uy * .17 * u, wx = ex + vx * .16 * u, wy = ey + vy * .16 * u;
    seg(sx, sy, ex, ey, .045, .04, limb); seg(ex, ey, wx, wy, .04, .035, limb);
    fill(...circle(wx, wy, .055 * u), GLOVE, o);
  }
  // face: eyes looking with the dance, a mouth that sings on the beat, a little nod
  const [fx0, fy0] = R(0, BODY[c.kind].face + .015 * hit);
  const look = (p.look || 0) * .022 * u;
  for (const side of [-1, 1]) {
    const ex = fx0 + side * .075 * u, ey = fy0 - .03 * u;
    fill(...circle(ex, ey, .058 * u), [1, 1, 1], { ow: ow * .8, outline: INK, clip });
    fill(...circle(ex + look, ey + .005 * u, .03 * u), INK, { clip });
    fill(...circle(ex + look + .01 * u, ey - .008 * u, .009 * u), [1, 1, 1], { clip });
    fill(...circle(fx0 + side * .14 * u, fy0 + .04 * u, .035 * u), CHEEK, { alpha: .45, clip });
  }
  const mh = .018 + .03 * hit;
  fill([fx0 - .08 * u, fy0, fx0 + .08 * u, fy0 + .14 * u], ellipse(fx0, fy0 + .07 * u, .048 * u, mh * u), MOUTH, { ow: ow * .7, outline: INK, clip });
}

// ---- real GIFs: streamed, never saved; picked by the song's language and how well they fit its beat ----
const fs = require("fs");
const GH = 400, GIF_SECONDS = 5, PEEK = 120;

/** Streams a GIF's MP4 straight from its URL into memory, h pixels tall. Nothing is written to disk. */
function decode(g, h, seconds) {
  const w = Math.round(g.w / g.h * h / 2) * 2;
  const r = spawnSync(FF, ["-hide_banner", "-t", String(seconds), "-i", g.mp4, "-vf", `scale=${w}:${h}`, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], { maxBuffer: 1 << 30 });
  const size = w * h * 3, n = Math.floor(r.stdout.length / size);
  if (!n) return null;
  const fps = +((r.stderr.toString().match(/([\d.]+) fps/) || [])[1] || 15);
  return { w, h, n, fps, size, frames: Array.from({ length: n }, (_, i) => r.stdout.subarray(i * size, (i + 1) * size)) };
}

/** How much moves between frames, and its strongest repeat (one dance move), with how clear that repeat is. */
function rhythm({ frames, n, size }) {
  const m = frames.map((f, i) => { if (!i) return 0; let s = 0; const p = frames[i - 1]; for (let k = 0; k < size; k += 97) s += Math.abs(f[k] - p[k]); return s; });
  m[0] = m[1] || 0;
  const mean = m.reduce((a, v) => a + v, 0) / n, z = m.map(v => v - mean);
  let lag = 0, clarity = 0;
  for (let L = 3; L <= n / 2; L++) {
    let s = 0, a = 0, b = 0;
    for (let i = 0; i + L < n; i++) { s += z[i] * z[i + L]; a += z[i] * z[i]; b += z[i + L] * z[i + L]; }
    const c = s / Math.sqrt(a * b || 1);
    if (c > clarity) { clarity = c; lag = L; }
  }
  return { m, lag, clarity };
}

/** How well a GIF's moves fit a tempo: 1 when they already land on the beat (or every other beat, or twice a beat). */
function fit(R, fps, bpm) {
  if (!R.lag || R.clarity < .2) return { tempo: 0, score: 0 };
  let ratio = 60 * fps / R.lag / bpm;
  while (ratio > Math.SQRT2) ratio /= 2;
  while (ratio < Math.SQRT1_2) ratio *= 2;
  const tempo = 1 - 2 * Math.abs(Math.log2(ratio));
  return { tempo, score: tempo * R.clarity };
}

/** Streams a chosen GIF at full size and retimes its loop so its moves land on the song's beat. */
function load(g, beat) {
  const D = decode(g, GH, GIF_SECONDS);
  if (!D) throw new Error(`couldn't stream ${g.id}`);
  const { w, h, n, fps, frames } = D, { m, lag, clarity } = rhythm(D);
  // Beats per loop: keep it near its natural speed, in whole dance moves when it has a clear rhythm.
  const natural = n / fps / beat, moves = clarity > .3 ? Math.max(1, Math.round(n / lag)) : 1;
  // Whole moves per beat or whole beats per move, whichever keeps it nearest its own speed.
  const fits = [];
  for (let k = 1; k <= moves; k++) if (moves % k === 0) fits.push(k);
  for (let k = 2; k <= 8; k++) fits.push(moves * k);
  let beats = fits.reduce((a, k) => Math.abs(Math.log(k / natural)) < Math.abs(Math.log(a / natural)) ? k : a, fits[0]);
  if (Math.abs(Math.log(beats / natural)) > Math.log(1.35)) beats = Math.max(1, Math.round(natural)); // no clean fit: just loop on the bar
  // Start the loop where a move hits, so the hits fall on the beat.
  const step = n / beats;
  let off = 0, top = -1;
  for (let o = 0; o < Math.max(1, Math.round(step)); o++) {
    let s = 0; for (let j = 0; j < beats; j++) s += m[Math.round(o + j * step) % n];
    if (s > top) { top = s; off = o; }
  }
  // A tiny copy of every frame: stretched up, it's the blurred backdrop.
  const B = 14, sw = Math.max(2, Math.round(w / B)), sh = Math.max(2, Math.round(h / B));
  const small = frames.map(f => {
    const o = new Uint8Array(sw * sh * 3);
    for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) for (let k = 0; k < 3; k++) {
      let s = 0, c = 0;
      for (let yy = y * B; yy < Math.min(h, (y + 1) * B); yy += 2) for (let xx = x * B; xx < Math.min(w, (x + 1) * B); xx += 2) { s += f[(yy * w + xx) * 3 + k]; c++; }
      o[(y * sw + x) * 3 + k] = s / (c || 1);
    }
    return o;
  });
  return { ...g, w, h, frames, small, sw, sh, beats, off, n, speed: beats * beat / (n / fps) };
}

// The set: songs in turn, two bars each. For each song, peek at every candidate in its language
// (small and short), rank them by how well their moves fit its tempo, and play the best two.
const SONGS = process.env.GIFS ? JSON.parse(fs.readFileSync(process.env.GIFS, "utf8")) : null;
if (SONGS) {
  let start = 0;
  for (const s of SONGS) {
    s.beat = 60 / s.bpm; s.start = start; s.len = 8 * s.beat; start += s.len;
    const ranked = s.candidates.map(g => {
      const D = decode(g, PEEK, 4);
      return D && { g, ...fit(rhythm(D), D.fps, s.bpm) };
    }).filter(Boolean).sort((a, b) => b.score - a.score);
    s.gifs = ranked.slice(0, 2).map(r => ({ ...load(r.g, s.beat), match: r.tempo }));
    console.log(`${s.lang}, ${s.bpm} BPM (${ranked.length} streamed): ` +
      s.gifs.map(G => `"${G.title || G.id}" ${Math.round(G.match * 100)}% match, ${G.speed.toFixed(2)}x`).join("; "));
  }
}
/** Draws a GIF frame into a rect: cover-fit, or (small) its blurred copy, dimmed. */
function drawGif(G, b, clip, kick, blur = false, dim = 1) {
  const fi = Math.floor(G.off + ((b % G.beats) / G.beats) * G.n) % G.n;
  const f = blur ? G.small[fi] : G.frames[fi], gw = blur ? G.sw : G.w, gh = blur ? G.sh : G.h;
  const { x, y, w, h, sdf } = clip, s = Math.max(w / gw, h / gh), cx = x + w / 2, cy = y + h / 2, lift = (1 + .08 * kick) * dim;
  G = { w: gw, h: gh };
  for (let Y = Math.max(0, y | 0); Y < Math.min(H, Math.ceil(y + h)); Y++) for (let X = Math.max(0, x | 0); X < Math.min(W, Math.ceil(x + w)); X++) {
    const a = sdf ? clamp(.5 - sdf(X + .5, Y + .5)) : 1; if (a <= 0) continue;
    // bilinear, cover-fit
    const u = clamp((X + .5 - cx) / s + G.w / 2 - .5, 0, G.w - 1.001), v = clamp((Y + .5 - cy) / s + G.h / 2 - .5, 0, G.h - 1.001);
    const u0 = u | 0, v0 = v | 0, fu = u - u0, fv = v - v0, i00 = (v0 * G.w + u0) * 3, i10 = i00 + 3, i01 = i00 + G.w * 3, i11 = i01 + 3;
    const o = (Y * W + X) * 3;
    for (let k = 0; k < 3; k++) {
      const c = ((f[i00 + k] * (1 - fu) + f[i10 + k] * fu) * (1 - fv) + (f[i01 + k] * (1 - fu) + f[i11 + k] * fu) * fv) / 255;
      px[o + k] += (Math.min(1, c * lift) - px[o + k]) * a;
    }
  }
}

// ---- the wall: a new layout every bar --------------------------------------------------------
const PAL = [["#FF5A1F", "#FFB43A"], ["#7B2FF7", "#F107A3"], ["#00C2FF", "#3D5AFE"], ["#00E5A0", "#00A3FF"],
  ["#FF2D55", "#FF9F0A"], ["#FFD60A", "#FF6A00"], ["#5E5CE6", "#BF5AF2"], ["#30D158", "#64D2FF"]].map(p => p.map(rgb));
const AX = 28, AY = 28, AW = W - 56, AH = 584, GAP = 14;
const grid = (cols, rows, x = AX, y = AY, w = AW, h = AH) => {
  const cw = (w - GAP * (cols - 1)) / cols, rh = (h - GAP * (rows - 1)) / rows, out = [];
  for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++) out.push([x + c * (cw + GAP), y + r * (rh + GAP), cw, rh]);
  return out;
};
function layout(bar, beatInBar) {
  const tiles = (rects, cast, pal = cast) => rects.map((r, i) => ({ r, chars: [cast[i]], pal: pal[i] }));
  switch (bar % 5) {
    case 0: return tiles(grid(3, 2), [0, 1, 2, 3, 4, 5]);
    case 1: {
      const hw = (AW - GAP) * .58;
      return tiles([[AX, AY, hw, AH], ...grid(2, 2, AX + hw + GAP, AY, AW - hw - GAP, AH)], [6, 7, 2, 5, 3]);
    }
    case 2: return tiles(grid(4, 2), [0, 1, 2, 3, 4, 5, 6, 7]);
    case 3: return [{ r: [AX, AY, AW, AH], chars: [3, 0, 2, 1, 5], pal: 1, move: "disco", lineup: true }];
    default: return tiles(grid(3, 2), [0, 1, 2, 3, 4, 5].map(i => (i * 3 + beatInBar) % 8)); // cuts on every beat
  }
}

/** One GIF at a time: whole, in a glass frame, over a blurred, dimmed copy of itself. */
function single(G, b, kick, barT) {
  drawGif(G, b, { x: -40, y: -40, w: W + 80, h: H + 80 }, kick, true, .42 + .12 * kick);
  const pop = 1 - Math.exp(-barT * 10) * Math.cos(barT * 18) * .35, s = Math.min(AW / G.w, AH / G.h) * pop * (1 + .025 * kick);
  const w = G.w * s, h = G.h * s, x = AX + (AW - w) / 2, y = AY + (AH - h) / 2;
  const sdf = roundRect(x, y, w, h, 26);
  fill([x - 30, y - 10, x + w + 30, y + h + 50], roundRect(x + 4, y + 22, w - 8, h, 30), [0, 0, 0], { alpha: .35 }); // shadow
  drawGif(G, b, { x, y, w, h, sdf }, kick);
  fill([x, y, x + w, y + h], (X, Y) => Math.abs(sdf(X, Y) + 1) - .75, [1, 1, 1], { alpha: .4 });
}

function frame(t) {
  if (SONGS) return songFrame(t);
  const b = t / BEAT + 32, ph = b % 1, kick = Math.exp(-ph * 7);
  const bar = Math.floor((b - 32) / 4), beatInBar = Math.floor(b - 32) % 4, barT = t - bar * 4 * BEAT, beatT = ph * BEAT;
  // stage: dark, with coloured pools that swell on the kick
  const pools = [[.25 + .08 * Math.sin(t * .7), .8, rgb("#FF5A1F")], [.78, .3 + .06 * Math.cos(t * .5), rgb("#C2185B")], [.5, .5 + .1 * Math.sin(t * .4), rgb("#5B2BD6")]];
  const g = .32 + .25 * kick, bg = rgb("#0B0710");
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y * W + x) * 3; let r = bg[0], gg = bg[1], bb = bg[2];
    for (const [cx, cy, c] of pools) { const dx = x / W - cx, dy = (y / H - cy) * .6, k = g * Math.exp(-(dx * dx + dy * dy) * 9); r += c[0] * k; gg += c[1] * k; bb += c[2] * k; }
    px[i] = r; px[i + 1] = gg; px[i + 2] = bb;
  }
  const tiles = layout(bar, beatInBar);
  tiles.forEach((tile, i) => {
    // pop in on the bar (staggered), a smaller pop on every cut, a pump on every kick
    const born = bar % 5 === 4 ? beatT : barT - i * .045;
    if (born < 0) return;
    const pop = bar % 5 === 4 && beatInBar > 0 ? 1 - .1 * Math.exp(-born * 12) : 1 - Math.exp(-born * 9) * Math.cos(born * 16);
    const s = pop * (1 + .03 * kick);
    const [x0, y0, w0, h0] = tile.r, w = w0 * s, h = h0 * s, x = x0 + (w0 - w) / 2, y = y0 + (h0 - h) / 2;
    if (w < 4) return;
    const rad = Math.min(26, h * .09), sdf = roundRect(x, y, w, h, rad), clip = { x, y, w, h, sdf };
    const [c1, c2] = PAL[tile.pal % PAL.length], scx = x + w / 2, scy = y + h * .62, spin = t * .5 + i;
    fill([x, y, x + w, y + h], sdf, (X, Y) => {
      const c = mix(c1, c2, (Y - y) / h), ray = Math.cos(Math.atan2(Y - scy, X - scx) * 9 + spin) > 0 ? .09 : 0;
      return c.map(v => Math.min(1, v * (1 + .14 * kick) + ray));
    });
    const n = tile.chars.length, u = tile.lineup ? Math.min(h * .62, w / n * .85) :Math.min(h * .78, w * 1.1);
    tile.chars.forEach((ci, k) => dancer(CAST[ci], b, t, x + w * (k + .5) / n, y + h * .9, u, clip, tile.move));
    // a glass edge
    fill([x, y, x + w, y + h], (X, Y) => Math.abs(sdf(X, Y) + 1) - .75, [1, 1, 1], { alpha: .35 });
  });
  return finish(barT);
}

/** A soft flash on each new bar, then the frame as bytes. */
function finish(barT) {
  const flash = .22 * Math.exp(-barT * 8);
  if (flash > .01) for (let i = 0; i < px.length; i++) px[i] += (1 - px[i]) * flash;
  const out = Buffer.alloc(W * H * 3);
  for (let i = 0; i < px.length; i++) out[i] = Math.round(clamp(px[i]) * 255);
  return out;
}

/** A frame of the set: the song playing, and one of its GIFs, changing on every bar. */
function songFrame(t) {
  const s = SONGS.filter(s => t >= s.start).pop(), bl = (t - s.start) / s.beat, kick = Math.exp(-(bl % 1) * 7);
  const bar = Math.floor(bl / 4), barT = (bl - bar * 4) * s.beat;
  single(s.gifs[bar % s.gifs.length], bl, kick, barT);
  return finish(barT);
}

/** The glass overlay for one bar of the set: the song, why this GIF, and GIPHY's credit. */
function overlayPage(s, G, next) {
  return `<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;width:1280px;height:720px;background:transparent;font-family:'Segoe UI Variable Display','Segoe UI',sans-serif;color:#F5F5F7;-webkit-font-smoothing:antialiased}
.g{position:absolute;background:rgba(20,12,24,.55);border:1px solid rgba(255,255,255,.2);backdrop-filter:blur(20px)}
.card{left:28px;top:628px;height:64px;width:520px;border-radius:22px;display:flex;align-items:center;gap:14px;padding:0 18px;box-sizing:border-box}
.art{width:44px;height:44px;border-radius:50%;background:conic-gradient(#FF2D55,#FF6A1A,#FFC24A,#FF2D55);box-shadow:0 0 0 3px rgba(255,255,255,.15)}
.t{font-weight:650;font-size:19px}.s{font-size:14px;color:rgba(245,245,247,.72);margin-top:1px}
.why{left:566px;top:634px;height:52px;border-radius:999px;display:flex;align-items:center;gap:10px;padding:0 20px;font-weight:600;font-size:16px}
.look{right:28px;top:628px;height:48px;border-radius:999px;display:flex;align-items:center;gap:10px;padding:0 20px;font-weight:650;font-size:17px}
.dot{width:9px;height:9px;border-radius:50%;background:#FFB43A;box-shadow:0 0 10px #FFB43A}
.by{position:absolute;right:34px;top:684px;font-size:13px;color:rgba(245,245,247,.6)}
</style></head><body>
<div class="g card"><div class="art"></div><div><div class="t">${s.lang} song</div><div class="s">${s.bpm} BPM${next ? ` &middot; Up next: ${next.lang} song` : ""}</div></div></div>
<div class="g why"><span class="dot"></span>${s.lang} &middot; beat match ${Math.round(G.match * 100)}%</div>
<div class="g look"><span class="dot"></span>Party GIFs</div>
<div class="by">Powered by GIPHY</div>
</body></html>`;
}

// STILL=<seconds> writes just that frame as raw RGB to OUT, for a quick look.
if (process.env.STILL) { fs.writeFileSync(OUT, frame(+process.env.STILL)); process.exit(0); }

const TOTAL = SONGS ? SONGS.reduce((a, s) => a + s.len, 0) : DUR;
const inputs = ["-f", "rawvideo", "-pix_fmt", "rgb24", "-s", `${W}x${H}`, "-r", String(FPS), "-i", "-"];
let graph;
if (SONGS) {
  // OVERLAY is a folder here: one overlay per bar, rendered by Edge, shown during its bar.
  const EDGE = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe", shows = [];
  SONGS.forEach((s, i) => s.gifs.forEach((G, k) => {
    const name = path.join(OVERLAY, `bar${i}_${k}`);
    fs.writeFileSync(name + ".html", overlayPage(s, G, SONGS[i + 1]));
    spawnSync(EDGE, ["--headless", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000", "--window-size=1280,720",
      `--screenshot=${name}.png`, "file:///" + (name + ".html").replace(/\\/g, "/")], { stdio: "ignore" });
    inputs.push("-i", name + ".png");
    const a = s.start + k * 4 * s.beat;
    shows.push([a, Math.min(s.start + s.len, a + 4 * s.beat)]);
  }));
  graph = shows.map(([a, b], i) => `${i ? `[v${i}]` : "[0:v]"}[${i + 1}:v]overlay=0:0:enable='between(t,${a.toFixed(3)},${(b - .001).toFixed(3)})'[v${i + 1}]`);
  graph.push(`[v${shows.length}]format=yuv420p[v]`);
  // Each song's two bars of beat-1.2.wav, retimed to its tempo, one after another.
  const A = shows.length + 1, SRC_BAR = 4 * 60 / 112;
  inputs.push("-i", WAV);
  graph.push(`[${A}:a]asplit=${SONGS.length}${SONGS.map((_, i) => `[s${i}]`).join("")}`);
  SONGS.forEach((s, i) => graph.push(`[s${i}]atrim=${(s.bar * SRC_BAR).toFixed(4)}:${((s.bar + 2) * SRC_BAR).toFixed(4)},asetpts=PTS-STARTPTS,` +
    `atempo=${(s.bpm / 112).toFixed(5)},afade=t=in:d=0.015,afade=t=out:st=${(s.len - .03).toFixed(3)}:d=0.03[a${i}]`));
  graph.push(`${SONGS.map((_, i) => `[a${i}]`).join("")}concat=n=${SONGS.length}:v=0:a=1,afade=out:st=${(TOTAL - .8).toFixed(3)}:d=0.8[a]`);
} else {
  inputs.push("-i", OVERLAY, "-ss", START.toFixed(4), "-t", String(DUR), "-i", WAV);
  graph = [`[0:v][1:v]overlay=0:0,format=yuv420p[v]`, `[2:a]afade=out:st=${DUR - .8}:d=0.8[a]`];
}
const ff = spawn(FF, ["-hide_banner", "-loglevel", "error", "-y", ...inputs,
  "-filter_complex", graph.join(";"),
  "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-c:a", "aac", "-b:a", "256k",
  "-movflags", "+faststart", "-t", TOTAL.toFixed(3), OUT], { stdio: ["pipe", "inherit", "inherit"] });
(async () => {
  for (let f = 0; f < Math.round(FPS * TOTAL); f++) {
    if (!ff.stdin.write(frame(f / FPS))) await new Promise(r => ff.stdin.once("drain", r));
    if (f % 30 === 0) process.stdout.write(`${f / 30}s `);
  }
  ff.stdin.end();
  ff.on("close", code => { console.log("\nwrote", OUT, code); process.exit(code); });
})();
