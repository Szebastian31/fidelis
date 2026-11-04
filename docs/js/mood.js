'use strict';
// The Mood tab (mood/Vibe.kt, mood/MoodRepository.kt, ui/mood/): one dial for any mood. Drag the dot, choose how much
// is new, make the mix. On Android the songs are matched by Last.fm tags; here by the genres Spotify gives each artist,
// so it needs no key of its own.

/** Where each feeling sits: x runs dark (−1) to bright (1), y runs calm (−1) to hyped (1). */
const MOOD_ANCHORS = [
  { label: 'Melancholy', blurb: 'Moody, emotional, late at night', x: -0.72, y: -0.55,
    tags: ['sad', 'melancholy', 'melancholic', 'moody', 'emotional', 'heartbreak', 'slowcore', 'sadcore', 'dark', 'indie folk', 'singer-songwriter', 'emo', 'shoegaze'],
    avoid: ['party', 'happy', 'dance', 'club'] },
  { label: 'Chill', blurb: 'Mellow, soft and unhurried', x: 0.38, y: -0.68,
    tags: ['chill', 'chillout', 'mellow', 'calm', 'relaxing', 'ambient', 'downtempo', 'acoustic', 'lo-fi', 'lofi', 'chillhop', 'dream pop', 'soft', 'trip hop', 'bossa nova', 'jazz'],
    avoid: ['metal', 'hardcore', 'punk', 'thrash', 'hard rock'] },
  { label: 'Focus', blurb: 'Instrumental and minimal', x: -0.12, y: -0.22,
    tags: ['instrumental', 'ambient', 'minimal', 'post-rock', 'study', 'lo-fi', 'lofi', 'classical', 'piano', 'electronic', 'downtempo', 'soundtrack', 'idm', 'modern classical', 'neoclassical'],
    avoid: ['rap', 'hip hop', 'party', 'pop punk'] },
  { label: 'Happy', blurb: 'Feel-good, sunny and bright', x: 0.78, y: 0.18,
    tags: ['happy', 'feel good', 'upbeat', 'summer', 'sunny', 'fun', 'pop', 'indie pop', 'funk', 'disco', 'k-pop', 'j-pop', 'bubblegum'],
    avoid: ['sad', 'melancholy', 'depressing', 'dark'] },
  { label: 'Party', blurb: 'Dance-floor picks, club to disco', x: 0.5, y: 0.78,
    tags: ['party', 'dance', 'club', 'house', 'deep house', 'tech house', 'disco', 'nu disco', 'funk', 'hip hop', 'r&b', 'rnb', 'pop', 'electro', 'afro house', 'reggaeton', 'dance pop', 'edm'],
    avoid: ['sad', 'ambient', 'sleep', 'folk', 'acoustic'] },
  { label: 'Energetic', blurb: 'Upbeat and loud, keeps you moving', x: -0.08, y: 0.74,
    tags: ['energetic', 'upbeat', 'high energy', 'workout', 'dance', 'edm', 'drum and bass', 'rock', 'punk', 'power', 'hype', 'electro', 'big beat', 'alternative rock', 'garage rock'],
    avoid: ['ambient', 'sad', 'slow', 'calm', 'sleep'] },
  { label: 'Intense', blurb: 'Heavy, dark and driving', x: -0.78, y: 0.72,
    tags: ['metal', 'hard rock', 'industrial', 'techno', 'dark techno', 'drill', 'trap', 'aggressive', 'heavy', 'hardcore', 'dubstep', 'grunge', 'post-punk', 'darkwave', 'nu metal', 'phonk'],
    avoid: ['happy', 'sleep', 'calm', 'acoustic', 'soft', 'relaxing'] },
];

const mix3 = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const hexRgb = x => [x >> 16 & 255, x >> 8 & 255, x & 255];
const CALM_DARK = hexRgb(0x2B2F7A), CALM_BRIGHT = hexRgb(0x1FA39A), HYPED_DARK = hexRgb(0xC0243F), HYPED_BRIGHT = hexRgb(0xFFA23F), CENTRE = hexRgb(0x7B5CE0);
/** The dial's colour at a spot: four corners blended, with violet in the middle. */
function vibeColor(x, y) {
  const u = Math.max(0, Math.min(1, (x + 1) / 2)), v = Math.max(0, Math.min(1, (y + 1) / 2));
  const edge = mix3(mix3(CALM_DARK, CALM_BRIGHT, u), mix3(HYPED_DARK, HYPED_BRIGHT, u), v);
  const mid = 1 - Math.min(1, Math.max(Math.abs(x), Math.abs(y)));
  return mix3(edge, CENTRE, mid * mid * .7).map(Math.round);
}
const NAMES = [['Slow and heavy-hearted', 'Calm and focused', 'Soft and sunny'], ['Moody', 'Easy-going', 'Warm and upbeat'], ['Dark and driving', 'High energy', 'Euphoric']];

/** A spot on the mood dial. Songs are scored against the three nearest feelings, blended by distance. */
class Vibe {
  constructor(x, y) {
    this.x = x; this.y = y;
    const raw = MOOD_ANCHORS.map(a => [a, 1 / ((a.x - x) ** 2 + (a.y - y) ** 2 + .04)]).sort((a, b) => b[1] - a[1]).slice(0, 3);
    const sum = raw.reduce((s, r) => s + r[1], 0);
    this.weights = raw.map(([a, w]) => [a, w / sum]);
    this.tagWeights = {};
    this.weights.forEach(([a, w]) => a.tags.forEach(t => { this.tagWeights[t] = (this.tagWeights[t] || 0) + w; }));
    this.avoid = new Set(this.weights.filter(w => w[1] >= .25).flatMap(w => w[0].avoid).filter(t => (this.tagWeights[t] || 0) < .2));
  }
  get nearest() { return this.weights[0][0]; }
  get name() { const e = this.y > .33 ? 2 : this.y < -.33 ? 0 : 1, b = this.x > .33 ? 2 : this.x < -.33 ? 0 : 1; return NAMES[e][b]; }
  get color() { return vibeColor(this.x, this.y); }
  /** A genre counts for a tag when it is the tag or contains it as words: "polish indie pop" has "indie pop". */
  static fits(genre, tag) { return genre === tag || (' ' + genre + ' ').includes(' ' + tag + ' ') || genre.replace(/-/g, ' ') === tag.replace(/-/g, ' '); }
  /** Strong genres (listed first) weigh more; clashing ones count against. */
  score(genres) {
    let s = 0;
    genres.forEach((g, i) => {
      let best = 0;
      for (const t in this.tagWeights) if (Vibe.fits(g, t)) best = Math.max(best, this.tagWeights[t]);
      s += best * (12 - Math.min(i, 11));
      for (const t of this.avoid) if (Vibe.fits(g, t)) { s -= 8; break; }
    });
    return s;
  }
  /** The genres this spot is after most, for finding new songs. */
  wanted() { return Object.entries(this.tagWeights).sort((a, b) => b[1] - a[1]).map(e => e[0]); }
  encode() { return `${this.x},${this.y}`; }
  static parse(s) { const p = (s || '').split(',').map(Number); return p.length === 2 && p.every(isFinite) ? new Vibe(Math.max(-1, Math.min(1, p[0])), Math.max(-1, Math.min(1, p[1]))) : null; }
}

/** Your mixes, kept on this phone. A new mix with the same title replaces the old one. */
const Mixes = {
  get list() { return store.get('mixes') || []; },
  save(list) { store.set('mixes', list.slice(0, 24)); },
  get(id) { return this.list.find(m => m.id === id) || null; },
  add(title, subtitle, tracks, vibe) {
    const m = { id: Date.now().toString(36), title, subtitle, tracks, at: Date.now(), vibe: vibe && vibe.encode() };
    this.save([m, ...this.list.filter(x => x.title !== title)]);
    return m;
  },
  update(m) { this.save(this.list.map(x => (x.id === m.id ? m : x))); },
  remove(id) { this.save(this.list.filter(x => x.id !== id)); },
  covers: m => [...new Set(m.tracks.map(t => t.image).filter(Boolean))].slice(0, 4),
};

const asMixTrack = (t, extra = {}) => ({ uri: t.uri, title: t.name, artist: names(t.artists), artistId: t.artists && t.artists[0] && t.artists[0].id, image: art(t.album && t.album.images, 300), ms: t.duration_ms, ...extra });
const mixAsTrack = t => ({ uri: t.uri, id: t.uri.split(':').pop(), name: t.title, artists: [{ name: t.artist, id: t.artistId }], album: { images: t.image ? [{ url: t.image, width: 300 }] : [] }, duration_ms: t.ms || 0, type: 'track' });
function capPerArtist(list, max, key) { const n = {}; return list.filter(x => (n[key(x)] = (n[key(x)] || 0) + 1) <= max); }

const MoodMaker = {
  progress: null,
  genres: store.get('artistGenres') || {},
  report(phase, done, total) { this.progress = { phase, done, total }; const b = document.querySelector('[data-mood-progress]'); if (b) b.replaceWith(moodProgress()); },
  /** Your listening: freshest first, so the cap keeps what you actually play now. */
  async library() {
    const pages = await Promise.all([
      S.recent().then(r => (r.items || []).map(x => x.track)),
      S.topTracks('short_term', 50).then(r => r.items || []),
      S.topTracks('medium_term', 50).then(r => r.items || []),
      ...[0, 50, 100, 150].map(o => S.liked(o).then(r => (r.items || []).map(x => x.track), () => [])),
      S.topTracks('long_term', 50).then(r => r.items || []),
    ].map(p => p.catch(() => [])));
    const seen = new Set();
    return pages.flat().filter(t => t && t.uri && t.type !== 'episode' && !seen.has(t.uri) && seen.add(t.uri)).slice(0, 220);
  },
  /** Every artist's genres: free with your top artists, then asked one by one (a few at a time) and kept. */
  async artistGenres(tracks) {
    const tops = await Promise.all(['short_term', 'medium_term', 'long_term'].map(r => S.topArtists(r, 50).then(x => x.items || [], () => [])));
    tops.flat().forEach(a => { if (a && a.id) this.genres[a.id] = a.genres || []; });
    const ids = [...new Set(tracks.map(t => t.artists && t.artists[0] && t.artists[0].id).filter(Boolean))];
    const need = ids.filter(id => !(id in this.genres)).slice(0, 80);
    let done = 0;
    this.report('Reading your artists', 0, need.length);
    const work = need.slice();
    await Promise.all([0, 1, 2, 3].map(async () => {
      while (work.length) {
        const id = work.shift();
        try { const a = await S.artist(id); this.genres[id] = (a && a.genres) || []; } catch (e) { /* skipped: scored as unknown */ }
        this.report('Reading your artists', ++done, need.length);
      }
    }));
    try { store.set('artistGenres', this.genres); } catch (e) { /* storage full: asked again next time */ }
  },
  genresOf(t) { const id = t.artists && t.artists[0] && t.artists[0].id; return (id && this.genres[id]) || []; },
  /** Chain songs that share genres so the mix flows; avoid the same artist back to back. */
  flow(list) {
    if (list.length <= 2) return list;
    const rest = list.slice();
    let cur = rest.splice(Math.floor(Math.random() * Math.min(3, rest.length)), 1)[0];
    const out = [cur];
    while (rest.length) {
      const g = new Set(cur.genres || []);
      let best = 0, bestScore = -1e9;
      rest.forEach((c, i) => { const sc = (c.genres || []).filter(x => g.has(x)).length * 10 - (c.artist === cur.artist ? 50 : 0) + Math.random() * 4; if (sc > bestScore) { bestScore = sc; best = i; } });
      cur = rest.splice(best, 1)[0]; out.push(cur);
    }
    return out;
  },
  /**
   * A mix for a spot on the dial. [share] is how much of it is new to you, from 0 to about 0.6. Your songs that fit
   * come first, then new songs from the genres this spot wants, spread evenly through.
   */
  async vibe(v, share, limit = 36) {
    try {
      this.report('Gathering your songs', 0, 0);
      const library = await this.library();
      if (!library.length) return [];
      await this.artistGenres(library);
      const ownTarget = Math.max(8, Math.floor(limit * (1 - share)));
      this.report('Picking what fits', 0, 0);
      const scored = library.map(t => ({ t, g: this.genresOf(t), s: v.score(this.genresOf(t)) })).filter(x => x.s > 2).sort((a, b) => b.s - a.s);
      const own = capPerArtist(scored, 2, x => x.t.artists[0].id).slice(0, ownTarget)
        .map(x => asMixTrack(x.t, { genres: x.g, matched: x.g.filter(g => Object.keys(v.tagWeights).some(t => Vibe.fits(g, t))).slice(0, 3) }));
      if (!own.length) return [];
      const mine = this.flow(own);
      if (share <= 0) return mine;

      // new songs: the genres this spot wants that your own fits share, searched on Spotify
      const known = new Set(library.map(t => t.uri)), knownArtists = new Set(library.map(t => t.artists[0] && t.artists[0].id));
      const yours = {};
      own.forEach(t => (t.matched || []).forEach(g => { yours[g] = (yours[g] || 0) + 1; }));
      const seeds = [...Object.keys(yours).sort((a, b) => yours[b] - yours[a]), ...v.wanted()].filter((g, i, a) => a.indexOf(g) === i).slice(0, 5);
      const need = limit - mine.length, fresh = [], seenArtists = new Set();
      let n = 0;
      for (const g of seeds) {
        this.report('Finding new songs', n++, seeds.length);
        for (const offset of [0, 10]) {
          const r = await S.search(`genre:"${g}"`, 'track', 10, offset).catch(() => null);
          for (const t of ((r && r.tracks && r.tracks.items) || [])) {
            const a = t && t.artists && t.artists[0] && t.artists[0].id;
            if (!t || known.has(t.uri) || !a || knownArtists.has(a) || seenArtists.has(a)) continue;
            seenArtists.add(a); fresh.push(asMixTrack(t, { isNew: true, matched: [g] }));
          }
          if (fresh.length >= need + 6) break;
        }
        if (fresh.length >= need + 6) break;
      }
      fresh.sort(() => Math.random() - .5);
      const add = fresh.slice(0, need);
      // spread the new songs evenly through yours
      const total = Math.min(limit, mine.length + add.length), every = (mine.length + add.length) / Math.max(1, add.length);
      const out = [];
      let i = 0, j = 0;
      while (out.length < total && (i < mine.length || j < add.length)) {
        const newTurn = j < add.length && (i >= mine.length || out.length + 1 >= Math.floor((j + 1) * every));
        out.push(newTurn ? add[j++] : mine[i++]);
      }
      return out;
    } finally { this.progress = null; }
  },
};

const MoodUI = {
  vibe: Vibe.parse(store.get('moodLast')) || (() => { const hr = new Date().getHours(); return hr >= 5 && hr <= 10 ? new Vibe(.45, .35) : hr <= 16 && hr >= 11 ? new Vibe(.3, -.35) : hr >= 17 && hr <= 21 ? new Vibe(.4, .55) : new Vibe(-.45, -.5); })(),
  share: store.get('moodShare') ?? 1,
  building: false, note: null,
  shares: ['Just mine', 'A few new', 'Mostly new'], amounts: [0, .3, .6],
  async make() {
    if (this.building) return;
    const v = this.vibe, i = this.share;
    this.building = true; this.note = null; store.set('moodLast', v.encode()); App.render();
    let tracks = [];
    try { tracks = await MoodMaker.vibe(v, this.amounts[i]); } catch (e) { this.note = e instanceof SpotifyError ? e.message : "Couldn't reach Spotify."; }
    this.building = false;
    if (!tracks.length) { this.note = this.note || 'Nothing in your listening fits this spot yet. Move the dot a little, or add new songs.'; App.render(); return; }
    const sub = v.nearest.blurb + [' · your songs', ' · with a few new ones', ' · mostly new to you'][i];
    const m = Mixes.add(v.name + (i === 2 ? ' · new' : ''), sub, tracks, v);
    Nav.push('mix', m.id);
  },
};

function moodProgress() {
  const p = MoodMaker.progress;
  return h('div', { 'data-mood-progress': '' },
    h('div.pbar', null, h('i', { class: p && p.total ? '' : 'busy', style: { width: p && p.total ? (p.done / p.total * 100) + '%' : null } })),
    h('div.t2.c', { style: { marginTop: '8px' } }, p ? (p.total ? `${p.phase} · ${p.done}/${p.total}` : p.phase + '…') : 'Gathering your songs…'));
}

function moodScreen() {
  const hr = new Date().getHours();
  const greeting = hr >= 5 && hr < 12 ? 'GOOD MORNING' : hr < 18 && hr >= 12 ? 'GOOD AFTERNOON' : hr < 22 && hr >= 18 ? 'GOOD EVENING' : 'UP LATE';
  const v = MoodUI.vibe, glow = `rgb(${v.color})`;
  const name = h('div.vname', null, v.name), sub = h('div.t2.c', null, v.weights.slice(0, 2).map(w => w[0].label).join(' · '));
  const page = h('div.mood', { style: { '--glow': glow } },
    h('div.hdr', null, h('div.k', null, greeting), h('h1', null, 'Mood')),
    h('div.t2.pad', null, 'Where are you right now?'),
    moodDial(v, (x, y, done) => {
      const nv = new Vibe(x, y), before = MoodUI.vibe.nearest.label;
      MoodUI.vibe = nv; MoodUI.note = null;
      name.textContent = nv.name; sub.textContent = nv.weights.slice(0, 2).map(w => w[0].label).join(' · ');
      page.style.setProperty('--glow', `rgb(${nv.color})`);
      if (nv.nearest.label !== before && navigator.vibrate) navigator.vibrate(8);
      if (done) store.set('moodLast', nv.encode());
    }, !MoodUI.building),
    h('div.vtitle', null, name, sub),
    h('div.switch2.wide', null, MoodUI.shares.map((n, i) => h('button' + (i === MoodUI.share ? '.on' : ''), { onclick: () => { MoodUI.share = i; store.set('moodShare', i); App.render(); } }, n))),
    h('div.pad', { style: { paddingTop: '16px' } },
      MoodUI.building ? moodProgress() : h('button.btn.grad.wide', { onclick: () => MoodUI.make() }, icon('sparkle'), 'Make this mix'),
      MoodUI.note && h('div.t2.c', { style: { marginTop: '12px' } }, MoodUI.note)));
  const mixes = Mixes.list;
  if (mixes.length) page.append(h('section', null, h('div.shead', null, h('h2', null, 'Your mixes')), h('div.shelf', null, mixes.map(mixCard))));
  page.append(h('div.note.pad', null, 'Mixes come from your own listening, matched by the genres Spotify gives each artist. Fidelis for Android matches with Last.fm tags, which is finer.'));
  return page;
}

/** A square colour field: calm at the bottom, hyped at the top, dark to the left, bright to the right. */
function moodDial(v, onMove, enabled) {
  const field = document.createElement('canvas');
  const n = 48; field.width = field.height = n;
  const fg = field.getContext('2d'), img = fg.createImageData(n, n);
  for (let j = 0; j < n; j++) for (let i = 0; i < n; i++) { const c = vibeColor(i / (n - 1) * 2 - 1, 1 - j / (n - 1) * 2), k = (j * n + i) * 4; img.data[k] = c[0]; img.data[k + 1] = c[1]; img.data[k + 2] = c[2]; img.data[k + 3] = 255; }
  fg.putImageData(img, 0, 0);
  const cv = h('canvas');
  const labels = MOOD_ANCHORS.map(a => h('span.anchor', { style: { left: ((a.x + 1) / 2 * 100) + '%', top: ((1 - a.y) / 2 * 100) + '%' } }, a.label));
  const box = h('div.dial' + (enabled ? '' : '.off'), null, cv, ...labels,
    h('span.edge.t', null, 'HYPED'), h('span.edge.b', null, 'CALM'), h('span.edge.l', null, 'DARK'), h('span.edge.r', null, 'BRIGHT'));
  let cur = v;
  const draw = () => {
    const W = box.clientWidth, dpr = window.devicePixelRatio || 1;
    if (!W) return;
    if (cv.width !== Math.round(W * dpr)) { cv.width = cv.height = Math.round(W * dpr); }
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high';
    g.drawImage(field, 0, 0, W, W);
    const sheen = g.createLinearGradient(0, 0, 0, W); sheen.addColorStop(0, 'rgba(255,255,255,.14)'); sheen.addColorStop(.5, 'transparent'); sheen.addColorStop(1, 'rgba(0,0,0,.22)');
    g.fillStyle = sheen; g.fillRect(0, 0, W, W);
    g.strokeStyle = 'rgba(255,255,255,.12)'; g.lineWidth = 1;
    g.beginPath(); g.moveTo(W / 2, 0); g.lineTo(W / 2, W); g.moveTo(0, W / 2); g.lineTo(W, W / 2); g.stroke();
    const px = (cur.x + 1) / 2 * W, py = (1 - cur.y) / 2 * W;
    const halo = g.createRadialGradient(px, py, 0, px, py, 46); halo.addColorStop(0, 'rgba(255,255,255,.45)'); halo.addColorStop(1, 'transparent');
    g.fillStyle = halo; g.beginPath(); g.arc(px, py, 46, 0, TAU); g.fill();
    g.fillStyle = '#fff'; g.beginPath(); g.arc(px, py, 13, 0, TAU); g.fill();
    g.fillStyle = `rgb(${cur.color})`; g.beginPath(); g.arc(px, py, 8, 0, TAU); g.fill();
    g.strokeStyle = 'rgba(255,255,255,.6)'; g.lineWidth = 1.5; g.beginPath(); g.arc(px, py, 20, 0, TAU); g.stroke();
    labels.forEach((el, i) => el.classList.toggle('on', MOOD_ANCHORS[i] === cur.nearest));
    box.style.boxShadow = `0 18px 60px rgba(${cur.color},.45), inset 0 0 0 1px rgba(255,255,255,.14)`;
  };
  const at = (e, done) => {
    const r = box.getBoundingClientRect();
    const x = Math.max(-1, Math.min(1, (e.clientX - r.left) / r.width * 2 - 1)), y = Math.max(-1, Math.min(1, 1 - (e.clientY - r.top) / r.height * 2));
    cur = new Vibe(x, y); onMove(x, y, done); draw();
  };
  let down = false;
  if (enabled) {
    box.addEventListener('pointerdown', e => { down = true; box.setPointerCapture(e.pointerId); at(e, false); });
    box.addEventListener('pointermove', e => { if (down) at(e, false); });
    box.addEventListener('pointerup', e => { if (down) { down = false; at(e, true); } });
    box.addEventListener('pointercancel', () => { down = false; });
  }
  new ResizeObserver(draw).observe(box);
  requestAnimationFrame(draw);
  return box;
}

/** Up to four covers in a square, like the Android collage. */
function collage(covers) {
  const c = covers.slice(0, 4);
  return h('div.collage.n' + c.length, null, c.map(u => h('img', { src: u, alt: '', loading: 'lazy' })));
}
function mixCard(m) {
  const v = Vibe.parse(m.vibe), tint = v ? `rgb(${v.color})` : 'var(--start)';
  const fresh = m.tracks.filter(t => t.isNew).length;
  return h('button.tile.mix', { style: { '--tint': tint }, onclick: () => Nav.push('mix', m.id) },
    h('div.cover.sq', null, collage(Mixes.covers(m)), h('i.mdot'),
      h('span.playdot', { 'aria-label': `Play ${m.title}`, onclick: e => { e.stopPropagation(); playMix(m, 0); } }, icon('play'))),
    h('div.t1.two', null, m.title), h('div.t2', null, fresh ? `${songs(m.tracks.length)} · ${fresh} new` : songs(m.tracks.length)));
}
function playMix(m, from = 0, shuffle = false) {
  if (!m.tracks.length) return;
  const list = shuffle ? m.tracks.slice().sort(() => Math.random() - .5) : m.tracks;
  Player.play({ uris: list.slice(0, 100).map(t => t.uri), offset: { position: shuffle ? 0 : from } });
}

function mixPage(id) {
  const m = Mixes.get(id);
  if (!m) return h('div.note.pad', null, 'This mix was deleted.');
  const fresh = m.tracks.filter(t => t.isNew).length;
  const v = Vibe.parse(m.vibe);
  const rows = m.tracks.map((t, i) => {
    const tr = mixAsTrack(t), playing = Player.item && Player.item.uri === t.uri;
    return h('div.song' + (playing ? '.on' : ''), { onclick: () => playMix(m, i) },
      coverEl(tr.album.images, 46, 'sq'),
      h('div.grow', null, h('div.t1', null, t.title), h('div.t2', null, t.isNew && h('b.new', null, 'NEW'), t.artist)),
      h('button.icbtn', { 'aria-label': 'More', onclick: e => { e.stopPropagation(); trackMenu(tr, {
        extra: [['Play from here', 'play', () => playMix(m, i)], ['Remove from mix', 'close', () => { m.tracks.splice(i, 1); Mixes.update(m); App.render(); }]],
      }); } }, icon('more')));
  });
  return h('div', { style: { '--tint': v ? `rgb(${v.color})` : 'var(--start)' } },
    h('div.dhead', null, h('div.cover.sq.mixcover', null, collage(Mixes.covers(m))),
      h('div.k', null, 'MIX'), h('h1', null, m.title),
      h('div.t2', null, songs(m.tracks.length) + (fresh ? ` · ${fresh} new` : '')), m.subtitle && h('div.t2', null, m.subtitle),
      h('div.acts', null,
        h('button.btn.grad', { disabled: !m.tracks.length, onclick: () => playMix(m, 0) }, icon('play'), 'Play'),
        h('button.btn.glass', { disabled: !m.tracks.length, onclick: () => playMix(m, 0, true) }, icon('shuffle'), 'Shuffle'),
        m.tracks.length > 0 && h('button.icbtn.glass', { 'aria-label': 'Add all to queue', onclick: async () => {
          toast(`Adding ${songs(m.tracks.length)} to the queue…`);
          let ok = 0;
          for (const t of m.tracks.slice(0, 50)) if (await attempt(() => S.enqueue(t.uri), true) !== undefined) ok++;
          toast(ok ? `Added ${songs(ok)} to the queue.` : "Couldn't add to the queue. Is Spotify playing?");
        } }, icon('queue')),
        h('button.icbtn.glass', { 'aria-label': 'Delete mix', onclick: () => confirmBox(`Delete “${m.title}”?`, "The mix is removed from Fidelis. Your Spotify library isn't touched.", 'Delete', () => { Mixes.remove(m.id); Nav.back(); }) }, icon('trash')))),
    !m.tracks.length ? h('div.note.pad', null, 'Every song was removed. Build a new one from the Mood tab.') : h('div.list', null, rows),
    m.tracks.length > 0 && h('button.more', { onclick: () => saveMixToSpotify(m) }, icon('plus'), 'Save as a Spotify playlist'));
}

/** A mix kept for good, as a playlist of your own on Spotify. */
async function saveMixToSpotify(m) {
  if (!Auth.allows('playlist-modify-private')) return reconnectFor('save mixes as playlists');
  const p = await attempt(() => (Demo.on ? Promise.resolve({ id: 'pl0', name: m.title }) : api('POST', '/me/playlists', { body: { name: m.title, description: m.subtitle + ' · made in Fidelis', public: false } })));
  if (!p) return;
  for (let i = 0; i < m.tracks.length; i += 100) await attempt(() => S.addToPlaylist(p.id, m.tracks.slice(i, i + 100).map(t => t.uri)));
  Data.drop('playlists');
  toast(`Saved as the playlist “${m.title}”.`);
}
