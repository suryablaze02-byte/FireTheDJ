// FireTheDJ launch beat: an original 124 BPM house track in A minor, 16 bars, synthesised from
// scratch (no samples), so the launch video's music is entirely FireTheDJ's own.
// Usage: node beat.js <out.wav>
"use strict";
const fs = require("fs");

const SR = 44100, BPM = 124, BEAT = 60 / BPM, BAR = BEAT * 4, BARS = 16, TAIL = 1.2;
const N = Math.ceil((BAR * BARS + TAIL) * SR);
const L = new Float32Array(N), R = new Float32Array(N);

// ---- tiny DSP kit ----------------------------------------------------------------------
let seed = 7;
const noise = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 2147483648 - 1; };
const midi = m => 440 * Math.pow(2, (m - 69) / 12);
function biquad(type, f, q) {
  const w = 2 * Math.PI * f / SR, a = Math.sin(w) / (2 * q), c = Math.cos(w);
  let b0, b1, b2, a0 = 1 + a, a1 = -2 * c, a2 = 1 - a;
  if (type === "lp") { b0 = (1 - c) / 2; b1 = 1 - c; b2 = b0; }
  else if (type === "hp") { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = b0; }
  else { b0 = a; b1 = 0; b2 = -a; } // band-pass
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  return x => { const y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0; x2 = x1; x1 = x; y2 = y1; y1 = y; return y; };
}
function add(t0, len, gain, pan, fn) { // fn(i, t) → sample
  const s0 = Math.round(t0 * SR), n = Math.round(len * SR);
  const gl = gain * Math.cos((pan + 1) * Math.PI / 4), gr = gain * Math.sin((pan + 1) * Math.PI / 4);
  for (let i = 0; i < n && s0 + i < N; i++) { const v = fn(i, i / SR); L[s0 + i] += v * gl; R[s0 + i] += v * gr; }
}
const at = (bar, beat = 0) => bar * BAR + beat * BEAT;

// ---- instruments ---------------------------------------------------------------------
function kick(t, g = 1, lp = 0) {
  const f = lp ? biquad("lp", lp, .7) : x => x;
  let ph = 0;
  add(t, .42, g, 0, (i, s) => {
    ph += 2 * Math.PI * (48 + 110 * Math.exp(-s * 38)) / SR;
    return f(Math.sin(ph) * Math.exp(-s * 7.5) + (s < .004 ? noise() * .5 : 0));
  });
}
function clap(t, g = .5) {
  const bp = biquad("bp", 1500, .9);
  add(t, .3, g, .1, (i, s) => {
    const burst = [0, .011, .022].some(o => s >= o && s < o + .01) ? 1 : 0;
    return bp(noise()) * (burst ? 1.4 : Math.exp(-(s - .022) * 18) * (s > .022 ? 1 : 0)) * 1.6;
  });
}
function hat(t, open = false, g = .16, pan = .25) {
  const hp = biquad("hp", 7500, .7);
  add(t, open ? .22 : .06, g, pan, (i, s) => hp(noise()) * Math.exp(-s * (open ? 16 : 70)));
}
function bass(t, note, len = BEAT * .45, g = .38) {
  const lp = biquad("lp", 420, 1.1); let ph = 0; const f = midi(note);
  add(t, len, g, 0, (i, s) => {
    ph = (ph + f / SR) % 1;
    const saw = 2 * ph - 1, sub = Math.sin(2 * Math.PI * ph);
    return lp(saw * .55 + sub * .8) * Math.min(1, s * 300) * Math.exp(-s * 3.5);
  });
}
function stab(t, notes, g = .14, cutoff = 2600, len = BEAT * .7) {
  for (const [k, n] of notes.entries()) for (const det of [-.07, .07]) {
    const lp = biquad("lp", cutoff, .9); let ph = Math.random(); const f = midi(n + det);
    add(t, len, g, det > 0 ? .45 - k * .1 : -.45 + k * .1, (i, s) => {
      ph = (ph + f / SR) % 1;
      return lp(2 * ph - 1) * Math.min(1, s * 400) * Math.exp(-s * 6);
    });
  }
}
function pad(t, notes, len, g = .05) {
  for (const n of notes) { const f = midi(n);
    add(t, len, g, 0, (i, s) => Math.sin(2 * Math.PI * f * s) * Math.sin(Math.PI * Math.min(1, s / len))); }
}
function riser(t, len, g = .22) {
  const hp = biquad("hp", 300, .7);
  let cut = 300, filt = hp;
  add(t, len, g, 0, (i, s) => {
    if (i % 512 === 0) { cut = 300 + 9000 * Math.pow(s / len, 2); filt = biquad("hp", cut, .9); }
    return filt(noise()) * Math.pow(s / len, 1.6);
  });
}
function impact(t, g = .7) {
  let ph = 0;
  add(t, 2.2, g, 0, (i, s) => { ph += 2 * Math.PI * 46 / SR; return Math.sin(ph) * Math.exp(-s * 1.8); });
  const lp = biquad("lp", 6000, .7);
  add(t, 1.6, g * .35, 0, (i, s) => lp(noise()) * Math.exp(-s * 2.4));
}

// ---- arrangement: Am - F - C - G, two bars each ----------------------------------------
const PROG = [[57, 60, 64, 33], [53, 57, 60, 29], [60, 64, 67, 36], [55, 59, 62, 31]]; // stab notes + bass root
const chordAt = bar => PROG[Math.floor(bar / 2) % 4];
const STAB_RHYTHM = [0, 1.5, 3]; // dotted house stabs

for (let bar = 0; bar < BARS; bar++) {
  const [a, b, c, root] = chordAt(bar);
  const intro = bar < 4, build = bar === 6 || bar === 7, drop = bar >= 8 && bar < 15, last = bar === 15;

  if (intro) {
    if (bar % 2 === 0) pad(at(bar), [a, b, c], BAR * 2);
    for (let q = 0; q < 4; q++) kick(at(bar, q), .55, bar < 2 ? 260 : 900);
    for (let q = 0; q < 4; q++) hat(at(bar, q + .5), false, bar < 2 ? .07 : .12);
    if (bar >= 2) for (const s of STAB_RHYTHM) stab(at(bar, s), [a, b, c], .09, 1200);
  } else if (bar < 6 || drop) {
    for (let q = 0; q < 4; q++) kick(at(bar, q), 1);
    clap(at(bar, 1)); clap(at(bar, 3));
    for (let q = 0; q < 4; q++) { hat(at(bar, q + .5), true); hat(at(bar, q + .25), false, .08, -.25); hat(at(bar, q + .75), false, .08, -.25); }
    for (let q = 0; q < 4; q++) bass(at(bar, q + .5), root);
    for (const s of STAB_RHYTHM) stab(at(bar, s), [a, b, c], drop ? .15 : .1, drop ? 3200 : 1800);
    if (drop && bar % 2 === 0) pad(at(bar), [a + 12, c + 12], BAR * 2, .03);
  } else if (build) {
    // Snare roll tightening 8ths → 16ths → 32nds, riser over both bars, kick drops out at the end.
    const step = bar === 6 ? .5 : .25;
    for (let q = 0; q < 4; q += step) clap(at(bar, q), .18 + .25 * ((bar - 6) * 4 + q) / 8);
    if (bar === 7) for (let q = 3; q < 4; q += .125) clap(at(bar, q), .4);
    if (bar === 6) { riser(at(6), BAR * 2); for (let q = 0; q < 4; q++) kick(at(bar, q), .85); }
    for (const s of STAB_RHYTHM) stab(at(bar, s), [a, b, c], .08, 900 + (bar - 6) * 1500);
  } else if (last) {
    impact(at(15), .55);
    stab(at(15), [57, 60, 64, 69], .2, 3000, BAR * 1.2);
    kick(at(15), 1);
  }
}
impact(at(8), .75); // the drop

// ---- a little space: two short comb echoes, then glue and level ------------------------
for (const [d, fb] of [[Math.round(BEAT * .75 * SR), .22], [Math.round(.0297 * SR), .18]]) {
  for (let i = d; i < N; i++) { L[i] += R[i - d] * fb; R[i] += L[i - d] * fb; }
}
let peak = 0;
for (let i = 0; i < N; i++) { L[i] = Math.tanh(L[i] * 1.25); R[i] = Math.tanh(R[i] * 1.25); peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i])); }
const norm = .89 / peak; // about -1 dBFS
const fadeFrom = Math.round((BAR * BARS + .2) * SR);

const buf = Buffer.alloc(44 + N * 4);
buf.write("RIFF", 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write("WAVEfmt ", 8);
buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34); buf.write("data", 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  const fade = i > fadeFrom ? Math.max(0, 1 - (i - fadeFrom) / (N - fadeFrom)) : 1;
  buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, L[i] * norm * fade)) * 32767), 44 + i * 4);
  buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, R[i] * norm * fade)) * 32767), 46 + i * 4);
}
fs.writeFileSync(process.argv[2] || "beat.wav", buf);
console.log(`wrote ${(N / SR).toFixed(2)} s, drop at ${at(8).toFixed(2)} s, bar = ${BAR.toFixed(4)} s`);
