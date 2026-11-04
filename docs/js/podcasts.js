'use strict';
// Podcasts (ui/podcasts/, integration/spotify/PodcastDiscovery.kt): your shows and their newest episodes from Spotify;
// charts and suggestions from Apple Podcasts, since Spotify's API has none. Spotify plays them.

/** Apple Podcasts' main genres and their ids, used for its charts. */
const PODCAST_GENRES = {
  'Comedy': 1303, 'True Crime': 1488, 'News': 1489, 'Society & Culture': 1324, 'History': 1487, 'Science': 1533,
  'Technology': 1318, 'Business': 1321, 'Sports': 1545, 'Music': 1310, 'Health & Fitness': 1512, 'Education': 1304,
  'Fiction': 1483, 'Arts': 1301, 'TV & Film': 1309, 'Kids & Family': 1305, 'Leisure': 1502, 'Religion & Spirituality': 1314,
};
/** Names reduced to letters and digits, to tell whether two shows are the same. */
const podKey = s => (s || '').toLowerCase().normalize('NFD').replace(/[^\p{L}\p{N}]/gu, '');

/** A JSON answer from Apple: fetched directly, or as JSONP where the browser isn't allowed to read it. */
function appleJson(url) {
  return fetch(url).then(r => (r.ok ? r.json() : Promise.reject(new Error(r.status)))).catch(() => new Promise((res, rej) => {
    const cb = 'fidelisApple' + Math.random().toString(36).slice(2);
    const s = document.createElement('script');
    const done = () => { delete window[cb]; s.remove(); };
    window[cb] = j => { done(); res(j); };
    s.onerror = () => { done(); rej(new Error('Apple Podcasts is out of reach.')); };
    s.src = url + (url.includes('?') ? '&' : '?') + 'callback=' + cb;
    document.head.append(s);
    setTimeout(() => { if (window[cb]) { done(); rej(new Error('Apple Podcasts took too long.')); } }, 9000);
  }));
}

const Pods = {
  genres: store.get('podGenres') || {},
  /** Your shows: Spotify's list, newest followed first. */
  async shows() {
    const r = await S.shows();
    return ((r && r.items) || []).map(x => x.show).filter(Boolean);
  },
  /** The newest episodes across your shows, like Spotify's "New episodes". */
  async latest(shows) {
    const lists = await Promise.all(shows.slice(0, 12).map(s => S.episodes(s.id, 3).then(r => ((r && r.items) || []).filter(Boolean).map(e => ({ ...e, show: e.show || s })), () => [])));
    return lists.flat().sort((a, b) => (b.release_date || '').localeCompare(a.release_date || ''));
  },
  /** A Spotify show search, turned into picks. */
  async spotify(q) {
    const r = await S.search(q, 'show', 10);
    return ((r && r.shows && r.shows.items) || []).filter(Boolean).map(s => ({ key: s.id, name: s.name, by: s.publisher, image: art(s.images, 300), spotifyId: s.id }));
  },
  async apple(term, limit = 25) {
    if (Demo.on) return [];
    const j = await appleJson(`https://itunes.apple.com/search?media=podcast&entity=podcast&limit=${limit}&country=${Where.cc.toLowerCase()}&term=${encodeURIComponent(term.trim().slice(0, 100))}`);
    return (j.results || []).map(o => ({ key: 'a' + o.collectionId, name: o.collectionName, by: o.artistName, image: o.artworkUrl600 || o.artworkUrl100, genres: o.genres || [] })).filter(p => p.name);
  },
  /** Apple Podcasts' top shows, overall or in one genre. */
  async chart(genre = null, cc = Where.cc.toLowerCase(), limit = 25) {
    if (Demo.on) return Demo.shows().map(s => ({ key: 'a' + s.id, name: s.name, by: s.publisher, image: art(s.images, 300) }));
    const g = genre && PODCAST_GENRES[genre] ? `/genre=${PODCAST_GENRES[genre]}` : '';
    try {
      const j = await appleJson(`https://itunes.apple.com/${cc}/rss/toppodcasts/limit=${limit}${g}/json`);
      const e = j.feed && j.feed.entry;
      return (Array.isArray(e) ? e : e ? [e] : []).map(x => {
        const imgs = x['im:image'] || [];
        const img = imgs.length ? imgs[imgs.length - 1].label.replace(/\/\d+x\d+bb\.png$/, '/600x600bb.jpg') : null;
        return { key: 'a' + ((x.id && x.id.attributes && x.id.attributes['im:id']) || x['im:name'].label), name: x['im:name'].label, by: (x['im:artist'] || {}).label || '', image: img };
      });
    } catch (err) {
      // the newer chart feed has no genres; a genre then falls back to a Spotify search
      if (genre) return this.spotify(genre);
      const j = await fetch(`https://rss.applemarketingtools.com/api/v2/${cc}/podcasts/top/${limit}/podcasts.json`).then(r => r.json());
      return ((j.feed && j.feed.results) || []).map(x => ({ key: 'a' + x.id, name: x.name, by: x.artistName, image: (x.artworkUrl100 || '').replace('100x100', '600x600') }));
    }
  },
  /** The show's main genre, such as "Comedy", kept on this phone. Null when Apple doesn't know the show. */
  async genre(s) {
    if (s.id in this.genres) return this.genres[s.id] || null;
    const want = podKey(s.name);
    const hit = (await this.apple(s.name, 5).catch(() => [])).find(o => { const k = podKey(o.name); return want && k && (k === want || k.startsWith(want) || want.startsWith(k)); });
    const g = hit && (hit.genres || []).find(x => x in PODCAST_GENRES) || '';
    this.genres[s.id] = g; store.set('podGenres', this.genres);
    return g || null;
  },
  /** Rows of shows picked from what you listen to. Shows you already follow never appear. */
  async forYou(shows) {
    const tagged = await Promise.all(shows.slice(0, 15).map(async s => [s, await this.genre(s).catch(() => null)]));
    const count = {};
    tagged.forEach(([, g]) => { if (g) count[g] = (count[g] || 0) + 1; });
    const vibe = Object.keys(count).sort((a, b) => count[b] - count[a]).slice(0, 4);
    const fav = shows[0], favGenre = fav && (tagged.find(t => t[0].id === fav.id) || [])[1];
    const cc = Where.cc.toLowerCase();
    const [like, home, world, artists] = await Promise.all([
      favGenre ? this.chart(favGenre, cc, 50).catch(() => []) : [],
      this.chart(null, cc).catch(() => []),
      this.chart(null, cc === 'us' ? 'gb' : 'us').catch(() => []),
      S.topArtists('short_term', 8).then(r => ((r && r.items) || []).map(a => a.name), () => []),
    ]);
    const about = (await Promise.all(artists.slice(0, 6).filter(a => podKey(a).length >= 3).map(async a =>
      [a, (await this.apple(a, 15).catch(() => [])).filter(p => podKey(p.name).includes(podKey(a)))]))).filter(x => x[1].length);
    const moods = await Promise.all(vibe.filter(g => g !== favGenre).slice(0, 2).map(async g => [g, await this.chart(g, cc, 50).catch(() => [])]));
    const seen = new Set(shows.map(s => podKey(s.name)));
    const fresh = l => l.filter(p => { const k = podKey(p.name); return k && !seen.has(k) && seen.add(k); }).slice(0, 15);
    const rows = [];
    const add = (key, eyebrow, title, list) => { const f = fresh(list); if (f.length) rows.push({ key, eyebrow, title, shows: f }); };
    if (fav) add('like', 'Because you listen to', fav.name, like);
    if (about.length) add('artists', 'About the artists you play', about.slice(0, 3).map(x => x[0]).join(', '), about.flatMap(x => x[1]));
    add('home', 'Charts', `Top in ${Where.name()}`, home);
    moods.forEach(([g, l]) => add('vibe' + g, 'Your vibe', `More ${g}`, l));
    add('world', 'Charts', 'Top worldwide', world);
    return { vibe, rows };
  },
  /** Any show: Spotify first, so it plays straight away, then Apple Podcasts. A genre's name gives its chart. */
  async search(q) {
    const g = Object.keys(PODCAST_GENRES).find(k => k.toLowerCase() === q.trim().toLowerCase());
    if (g) return this.chart(g, Where.cc.toLowerCase(), 50);
    const sp = await this.spotify(q).catch(() => []);
    return sp.length ? sp : this.apple(q).catch(() => []);
  },
  resolved: {},
  /** The Spotify show for a pick, matched by name. Null when Spotify doesn't have it. */
  async resolve(p) {
    if (p.spotifyId) return p.spotifyId;
    if (this.resolved[p.key]) return this.resolved[p.key];
    const hits = await this.spotify(p.name);
    const want = podKey(p.name);
    const best = hits.find(x => podKey(x.name) === want) || hits.find(x => { const k = podKey(x.name); return k && (k.startsWith(want) || want.startsWith(k)); }) || hits[0];
    return best ? (this.resolved[p.key] = best.spotifyId) : null;
  },
  async open(p) {
    const id = await this.resolve(p).catch(() => null);
    if (id) Nav.push('show', id); else toast(`${p.name} isn't on Spotify.`);
  },
  /** Plays an episode where you left off. */
  play(e) {
    const rp = e.resume_point || {};
    const at = !rp.fully_played && rp.resume_position_ms > 0 ? rp.resume_position_ms : 0;
    Player.play({ uris: [e.uri], position_ms: at });
  },
};

/** "Yesterday · 42 min", "3 days ago · 12 min left", "12 Oct 2026 · Played". */
function episodeMeta(e) {
  let date = e.release_date || '';
  const d = date.length === 10 ? new Date(date + 'T12:00:00') : null;
  if (d && !isNaN(d)) {
    const days = Math.floor((Date.now() - d) / 864e5);
    date = days <= 0 ? 'Today' : days === 1 ? 'Yesterday' : days < 7 ? `${days} days ago` : d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }
  const rp = e.resume_point || {}, mins = Math.round((e.duration_ms || 0) / 60000);
  const length = rp.fully_played ? 'Played' : rp.resume_position_ms > 0 ? `${Math.max(1, Math.round((e.duration_ms - rp.resume_position_ms) / 60000))} min left`
    : mins >= 60 ? `${Math.floor(mins / 60)} h ${mins % 60} min` : `${mins} min`;
  return [date, length].filter(Boolean).join(' · ');
}
const episodeFresh = e => e.release_date && Date.now() - new Date(e.release_date + 'T12:00:00') < 7 * 864e5;

/** A big card for a fresh episode, in its cover's own colour. */
function episodeCard(e) {
  const playing = Player.item && Player.item.uri === e.uri;
  const card = h('button.feature.ep', { style: { '--c': stationColor(e.show ? e.show.name : e.name, 46), '--d': stationColor(e.show ? e.show.name : e.name, 14) }, onclick: () => Pods.play(e) },
    coverEl(e.images, 64, 'sq'),
    (playing || episodeFresh(e)) && h('span.badge', null, playing ? 'PLAYING' : 'NEW'),
    h('div.bot', null, h('div.k', null, e.show ? e.show.name.toUpperCase() : ''), h('div.fname.two', null, e.name), h('div.t2', null, episodeMeta(e))));
  Tint.of(art(e.images, 300)).then(rgb => { if (rgb) { card.style.setProperty('--c', `rgb(${rgb})`); card.style.setProperty('--d', `rgb(${rgb.map(v => Math.round(v * .25))})`); } });
  return card;
}
function episodeRow(e, showName = true) {
  const playing = Player.item && Player.item.uri === e.uri;
  const rp = e.resume_point || {};
  return h('button.eprow' + (playing ? '.on' : ''), { onclick: () => Pods.play(e) },
    coverEl(e.images, 64, 'sq'),
    h('div.grow', null,
      showName && e.show && h('div.k.show', null, e.show.name),
      h('div.t1.two', null, e.name),
      e.description && h('div.t2.two', null, e.description),
      h('div.meta', null, h('span', null, episodeMeta(e)),
        !rp.fully_played && rp.resume_position_ms > 0 && e.duration_ms > 0 && h('span.prog', null, h('i', { style: { width: Math.min(100, rp.resume_position_ms / e.duration_ms * 100) + '%' } })))));
}
const showTile = s => tile(s.images, s.name, s.publisher, () => Nav.push('show', s.id));
const pickTile = p => h('button.tile', { onclick: () => Pods.open(p) }, h('div.cover.sq', null, p.image && h('img', { src: p.image, alt: '', loading: 'lazy' })),
  h('div.t1', null, p.name), p.by && h('div.t2', null, p.by));

const PodSearch = { q: '', timer: null };
function podcastsScreen() {
  const out = h('div', null,
    h('div.hdr.row', null, h('div.grow', null, h('div.k', null, 'PODCASTS'), h('h1', null, 'Listen')), listenSwitch()),
    searchBox('Search any podcast', PodSearch.q, v => { PodSearch.q = v; clearTimeout(PodSearch.timer); PodSearch.timer = setTimeout(() => App.render(), 400); }));
  const shows = Data.get('pods', () => Pods.shows());
  const q = PodSearch.q.trim();
  if (q) {
    const res = Data.get('podSearch:' + q.toLowerCase(), () => Pods.search(q));
    const followed = new Set((shows || []).map(s => podKey(s.name)));
    if (res === undefined) out.append(loading());
    else if (!res || !res.length) out.append(h('div.note.pad', null, 'No podcasts found. Try a show name, a host or a topic.'));
    else out.append(h('div.list', null, res.map(p => h('button.eprow', { onclick: () => Pods.open(p) },
      h('div.cover.sq', null, p.image && h('img', { src: p.image, alt: '', loading: 'lazy' })),
      h('div.grow', null, h('div.t1.two', null, p.name), p.by && h('div.t2', null, p.by)),
      followed.has(podKey(p.name)) && h('span.k', { style: { color: 'var(--hi)' } }, 'FOLLOWING')))));
    return out;
  }
  if (shows === undefined) { out.append(loading()); return out; }
  if (!shows) { out.append(failed('pods')); return out; }
  if (!shows.length) {
    out.append(h('div.card.empty', null, h('div.t1', null, 'No shows yet'), h('div.t2', null, 'Follow a podcast in Spotify and it shows up here. Or find one in the charts below.')));
  }
  const latest = shows.length ? Data.get('podLatest', () => Pods.latest(shows)) : [];
  if (latest === undefined) out.append(loading());
  else if (latest) {
    const fresh = latest.filter(e => !(e.resume_point || {}).fully_played).slice(0, 8);
    const resume = latest.filter(e => !(e.resume_point || {}).fully_played && (e.resume_point || {}).resume_position_ms > 0).slice(0, 4);
    if (fresh.length) out.append(h('section', null, h('div.shelf.wide', null, fresh.map(episodeCard))));
    if (resume.length) out.append(h('section', null, h('div.shead', null, h('h2', null, 'Continue listening')), h('div.list', null, resume.map(e => episodeRow(e)))));
  }
  if (shows.length) out.append(h('section', null, h('div.shead', null, h('h2', null, 'Your shows')), h('div.shelf', null, shows.map(showTile))));
  const picks = Data.get('podPicks', () => Pods.forYou(shows));
  const vibe = (picks && picks.vibe) || [];
  out.append(h('section', null,
    h('div.shead.col', null, h('div.k', { style: { color: 'var(--hi)' } }, vibe.length ? 'YOUR VIBE' : 'BROWSE'), h('h2', null, vibe.length ? vibe.slice(0, 2).join(' · ') : 'Top shows by genre')),
    h('div.shelf.chips', null,
      vibe.map(g => h('button.chip.big.mine', { style: { background: `linear-gradient(135deg, ${stationColor(g, 46)}, ${stationColor(g, 18)})` }, onclick: () => { PodSearch.q = g; App.render(true); } }, g)),
      Object.keys(PODCAST_GENRES).filter(g => !vibe.includes(g)).map(g => h('button.chip.big', { onclick: () => { PodSearch.q = g; App.render(true); } }, g)))));
  if (picks === undefined) out.append(loading());
  else if (picks) picks.rows.forEach(r => out.append(h('section', null,
    h('div.shead.col', null, h('div.k', { style: { color: 'var(--hi)' } }, r.eyebrow.toUpperCase()), h('h2', null, r.title)),
    h('div.shelf', null, r.shows.map(pickTile)))));
  if (latest && latest.length) out.append(h('section', null, h('div.shead', null, h('h2', null, 'New episodes')), h('div.list', null, latest.slice(0, 12).map(e => episodeRow(e)))));
  return out;
}

/** One show's page: its episodes, newest first. */
function showPage(id) {
  const show = Data.get('show:' + id, () => S.show(id));
  const eps = Data.get('showEps:' + id, async () => ((await S.episodes(id, 50)) || {}).items || []);
  if (show === undefined) return loading();
  if (!show) return failed('show:' + id);
  const list = (eps || []).filter(Boolean).map(e => ({ ...e, show }));
  return h('div', null,
    h('div.dhead', null, coverEl(show.images, 260, 'sq'), h('div.k', null, 'PODCAST'), h('h1', null, show.name), h('div.t2', null, show.publisher),
      h('div.acts', null,
        h('button.btn.grad', { disabled: !list.length, onclick: () => list[0] && Pods.play(list[0]) }, icon('play'), 'Play latest'),
        h('a.btn.glass', { href: `https://open.spotify.com/show/${show.id}`, target: '_blank', rel: 'noopener' }, icon('open'), 'Spotify'))),
    show.description && h('div.note.pad', null, show.description.slice(0, 400)),
    eps === undefined ? loading() : !list.length ? h('div.note.pad', null, "Spotify didn't list any episodes for this show.")
      : h('div.list', null, list.map(e => episodeRow(e, false))));
}
