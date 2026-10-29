// Finds candidate GIFs on GIPHY for the Party GIFs mockup: for each song, funny memes and dance
// moments in that song's language. Nothing is downloaded: it writes only GIPHY's MP4 stream links;
// gifs-mock.js streams them, measures each one's rhythm and plays the ones that fit the song's beat.
// The API key is read from local.properties (giphy.apiKey=...), which git ignores; it is never printed.
// Usage: node fetch-giphy.js <out.json>
"use strict";
const fs = require("fs");
const path = require("path");

const OUT = process.argv[2];
const props = fs.readFileSync(path.join(__dirname, "..", "local.properties"), "utf8");
const KEY = (props.match(/^giphy\.apiKey\s*=\s*(\S+)/m) || [])[1];
if (!KEY) { console.error("Add giphy.apiKey=... to local.properties first."); process.exit(1); }

// The mockup's set: five songs in five languages, each at its own tempo, mixed in turn.
// bar: where in beat-1.2.wav the song's two bars come from (it's retimed to the song's tempo).
const SONGS = [
  { lang: "Telugu", code: "te", bpm: 112, bar: 8, q: ["brahmanandam", "telugu dance", "tollywood funny", "telugu meme"] },
  { lang: "Hindi", code: "hi", bpm: 124, bar: 10, q: ["bollywood dance", "govinda dance", "hindi meme", "bollywood funny"] },
  { lang: "Punjabi", code: "pa", bpm: 100, bar: 12, q: ["bhangra", "punjabi dance", "diljit dosanjh", "punjabi funny"] },
  { lang: "Tamil", code: "ta", bpm: 116, bar: 14, q: ["vadivelu", "tamil dance", "kollywood dance", "tamil meme"] },
  { lang: "English", code: "en", bpm: 128, bar: 8, q: ["funny dance meme", "dancing meme", "dance party funny", "vibing"] },
];
const PER_QUERY = 5;

(async () => {
  const seen = new Set();
  for (const s of SONGS) {
    s.candidates = [];
    for (const q of s.q) {
      const url = `https://api.giphy.com/v1/gifs/search?api_key=${KEY}&q=${encodeURIComponent(q)}&limit=12&rating=pg&lang=${s.code}`;
      const res = await fetch(url);
      if (!res.ok) { console.error(`GIPHY said ${res.status} for "${q}"`); process.exit(1); }
      const { data } = await res.json();
      // Loops long enough to hold a few moves, each GIF used once in the whole set.
      for (const g of data.filter(g => !seen.has(g.id) && g.images?.original?.mp4 && +g.images.original.frames >= 12).slice(0, PER_QUERY)) {
        seen.add(g.id);
        const o = g.images.original;
        s.candidates.push({ id: g.id, query: q, title: g.title, user: g.username || "", page: g.url, mp4: o.mp4, w: +o.width, h: +o.height });
      }
    }
    console.log(`${s.lang}: ${s.candidates.length} candidates`);
  }
  fs.writeFileSync(OUT, JSON.stringify(SONGS, null, 1));
})();
