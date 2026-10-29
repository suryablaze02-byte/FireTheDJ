// FireTheDJ 1.2 launch track: an original, bright 112 BPM pop-electronic piece in D major
// (D - A - Bm - G), 28 bars, synthesised from scratch (no samples). Plucks, soft keys and glassy
// bells over a clean groove; it goes silent for one bar (bar 24) where the film says
// "the music stops", then comes back for the end card.
// Usage: node beat-1.2.js <out.wav>
"use strict";
const fs = require("fs");

const SR = 44100, BPM = 112, BEAT = 60 / BPM, BAR = BEAT * 4, BARS = 28, TAIL = 2.5;
const N = Math.ceil((BAR * BARS + TAIL) * SR);
const L = new Float32Array(N), R = new Float32Array(N);

// ---- tiny DSP kit (as in beat.js) --------------------------------------------------------
let seed = 11;
const noise = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 2147483648 - 1; };
const midi = m => 440 * Math.pow(2, (m - 69) / 12);
function biquad(type, f, q) {
  const w = 2 * Math.PI * f / SR, a = Math.sin(w) / (2 * q), c = Math.cos(w);
  let b0, b1, b2, a0 = 1 + a, a1 = -2 * c, a2 = 1 - a;
  if (type === "lp") { b0 = (1 - c) / 2; b1 = 1 - c; b2 = b0; }
  else if (type === "hp") { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = b0; }
  else { b0 = a; b1 = 0; b2 = -a; }
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  return x => { const y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0; x2 = x1; x1 = x; y2 = y1; y1 = y; return y; };
}
function add(t0, len, gain, pan, fn) {
  const s0 = Math.round(t0 * SR), n = Math.round(len * SR);
  const gl = gain * Math.cos((pan + 1) * Math.PI / 4), gr = gain * Math.sin((pan + 1) * Math.PI / 4);
  for (let i = 0; i < n && s0 + i < N; i++) { const v = fn(i, i / SR); L[s0 + i] += v * gl; R[s0 + i] += v * gr; }
}
const at = (bar, beat = 0) => bar * BAR + beat * BEAT;

// ---- instruments ---------------------------------------------------------------------------
function kick(t, g = 1, lp = 0) {
  const f = lp ? biquad("lp", lp, .7) : x => x;
  let ph = 0;
  add(t, .38, g, 0, (i, s) => { ph += 2 * Math.PI * (46 + 95 * Math.exp(-s * 42)) / SR; return f(Math.sin(ph) * Math.exp(-s * 8.5)); });
}
/** A finger snap: a short bright click and a little body. */
function snap(t, g = .42) {
  const bp = biquad("bp", 2300, 1.4);
  add(t, .16, g, -.08, (i, s) => bp(noise()) * Math.exp(-s * 38) * 2 + Math.sin(2 * Math.PI * 1800 * s) * Math.exp(-s * 90) * .3);
}
function shaker(t, g = .09, pan = .3) {
  const hp = biquad("hp", 6500, .8);
  add(t, .09, g, pan, (i, s) => hp(noise()) * Math.sin(Math.PI * Math.min(1, s / .09)));
}
function sub(t, note, len, g = .42) {
  let ph = 0; const f = midi(note);
  add(t, len, g, 0, (i, s) => { ph += 2 * Math.PI * f / SR; return Math.sin(ph) * Math.min(1, s * 120) * Math.min(1, (len - s) * 30); });
}
/** A plucked, slightly woody note: triangle and sine, quick decay. */
function pluck(t, note, g = .13, pan = 0, decay = 7) {
  const f = midi(note); const lp = biquad("lp", 3200, .8);
  add(t, .7, g, pan, (i, s) => {
    const p = (f * s) % 1, tri = 1 - 4 * Math.abs(p - .5);
    return lp(tri * .6 + Math.sin(2 * Math.PI * f * s) * .6) * Math.min(1, s * 900) * Math.exp(-s * decay);
  });
}
/** Soft electric-piano chord: a few sine partials, gentle attack. */
function keys(t, notes, len, g = .06) {
  for (const [k, n] of notes.entries()) {
    const f = midi(n);
    add(t, len, g, (k - 1) * .3, (i, s) => {
      const env = Math.min(1, s * 60) * Math.exp(-s * 2.4);
      return (Math.sin(2 * Math.PI * f * s) + .35 * Math.sin(4 * Math.PI * f * s) + .12 * Math.sin(6 * Math.PI * f * s)) * env;
    });
  }
}
/** A glassy bell: two-operator FM with a long, bright tail. */
function bell(t, note, g = .08, pan = .2) {
  const f = midi(note);
  add(t, 2.6, g, pan, (i, s) => Math.sin(2 * Math.PI * f * s + 2.2 * Math.exp(-s * 3) * Math.sin(2 * Math.PI * f * 3.5 * s)) * Math.exp(-s * 1.6));
}
function pad(t, notes, len, g = .035) {
  for (const n of notes) for (const det of [-.08, .08]) {
    const lp = biquad("lp", 1400, .7); let ph = Math.random(); const f = midi(n + det);
    add(t, len, g, det * 4, (i, s) => { ph = (ph + f / SR) % 1; return lp(2 * ph - 1) * Math.sin(Math.PI * Math.min(1, s / len)); });
  }
}
function riser(t, len, g = .16) {
  let filt = biquad("hp", 400, .9);
  add(t, len, g, 0, (i, s) => {
    if (i % 512 === 0) filt = biquad("hp", 400 + 8000 * Math.pow(s / len, 2), .9);
    return filt(noise()) * Math.pow(s / len, 1.8);
  });
}
function impact(t, g = .5) {
  let ph = 0;
  add(t, 2.4, g, 0, (i, s) => { ph += 2 * Math.PI * 44 / SR; return Math.sin(ph) * Math.exp(-s * 1.7); });
  const lp = biquad("lp", 5000, .7);
  add(t, 1.4, g * .25, 0, (i, s) => lp(noise()) * Math.exp(-s * 2.6));
}

// ---- arrangement ---------------------------------------------------------------------------
// D - A - Bm - G, two bars each: chord tones for keys/arp, and the bass root.
const PROG = [[62, 66, 69, 38], [61, 64, 69, 33], [62, 66, 71, 35], [62, 67, 71, 31]];
const chordAt = bar => PROG[Math.floor(bar / 2) % 4];
const ARP = [0, 1, 2, 1, 2, 0, 1, 2]; // eighths through the chord, an octave up

for (let bar = 0; bar < BARS; bar++) {
  const [a, b, c, root] = chordAt(bar);
  const tones = [a + 12, b + 12, c + 12];
  const intro = bar < 4, groove = bar >= 4 && bar < 8, full = (bar >= 8 && bar < 16) || (bar >= 20 && bar < 24) || bar === 25 || bar === 26;
  const light = bar >= 16 && bar < 20, silent = bar === 24, last = bar === 27;
  if (silent) continue;

  if (last) {
    keys(at(bar), [a, b, c, a + 12], BAR * 1.6, .08);
    sub(at(bar), root, BAR, .45);
    bell(at(bar), a + 24, .12, 0); bell(at(bar, .5), c + 12, .07, .3);
    kick(at(bar), 1);
    pad(at(bar), [a, c], BAR * 1.2, .03);
    continue;
  }

  // The pluck arpeggio runs all through: quiet and dark in the intro, bright later.
  for (let e = 0; e < 8; e++) pluck(at(bar, e / 2), tones[ARP[e]], intro ? .08 : .11, e % 2 ? .35 : -.35, intro ? 9 : 7);
  if (bar % 2 === 0) pad(at(bar), [a, b, c], BAR * 2, intro || light ? .04 : .025);

  if (intro) {
    if (bar === 0 || bar === 2) bell(at(bar), c + 12, .07);
    if (bar === 3) riser(at(3, 2), BEAT * 2, .1);
  }
  if (groove || full) {
    for (let q = 0; q < 4; q++) kick(at(bar, q), groove ? .7 : 1, groove ? 1200 : 0);
    snap(at(bar, 1)); snap(at(bar, 3));
    for (let s = 0; s < 4; s += groove ? .5 : .25) shaker(at(bar, s), full && s % .5 ? .06 : .09, s % .5 ? -.3 : .3);
    // Bass: on the beat in the groove, syncopated when it's full.
    const hits = groove ? [0, 1, 2, 3] : [0, .75, 1.5, 2, 2.75, 3.5];
    for (const h of hits) sub(at(bar, h), root, BEAT * .45, groove ? .36 : .42);
    if (full) for (const h of [.5, 1.5, 2.5, 3.5]) keys(at(bar, h), [a, b, c], BEAT * .45, .045);
    if (full && bar % 2 === 0) bell(at(bar), c + 24, .06, bar % 4 ? -.3 : .3);
  }
  if (light) {
    snap(at(bar, 3), .3);
    keys(at(bar), [a, b, c], BAR, .05);
    sub(at(bar), root, BAR * .9, .3);
    if (bar === 18) riser(at(18), BAR * 2);
  }
  if (bar === 7) riser(at(7), BAR, .12);
  if (bar === 23) { riser(at(23, 2), BEAT * 2, .14); }
}
impact(at(8)); impact(at(20)); impact(at(25), .45);

// ---- space, glue and level -------------------------------------------------------------------
for (const [d, fb] of [[Math.round(BEAT * .75 * SR), .2], [Math.round(.031 * SR), .16]]) {
  for (let i = d; i < N; i++) { L[i] += R[i - d] * fb; R[i] += L[i - d] * fb; }
}
let peak = 0;
for (let i = 0; i < N; i++) { L[i] = Math.tanh(L[i] * 1.2); R[i] = Math.tanh(R[i] * 1.2); peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i])); }
const norm = .89 / peak;
const fadeFrom = Math.round((BAR * BARS + .4) * SR);

const buf = Buffer.alloc(44 + N * 4);
buf.write("RIFF", 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write("WAVEfmt ", 8);
buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34); buf.write("data", 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  const fade = i > fadeFrom ? Math.max(0, 1 - (i - fadeFrom) / (N - fadeFrom)) : 1;
  buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, L[i] * norm * fade)) * 32767), 44 + i * 4);
  buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, R[i] * norm * fade)) * 32767), 46 + i * 4);
}
fs.writeFileSync(process.argv[2] || "beat-1.2.wav", buf);
console.log(`wrote ${(N / SR).toFixed(2)} s, bar = ${BAR.toFixed(4)} s, silence at ${at(24).toFixed(2)}-${at(25).toFixed(2)} s`);
