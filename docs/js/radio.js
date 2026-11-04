'use strict';
// Live radio, played by Fidelis itself rather than Spotify (radio/Station.kt, radio/Radio.kt, ui/radio/RadioScreen.kt).
// Stations come from Radio Browser (radio-browser.info), a free, open directory of about 50,000 stations.

/** One live station. lat and lon are in degrees, when the directory knows them. */
function station(o) {
  const stream = o.url_resolved || o.url || '';
  const name = (o.name || '').trim();
  if (!stream || !name) return null;
  return {
    id: o.stationuuid, name, stream: stream.replace(/^http:\/\//, 'https://'), secure: stream.startsWith('https://'),
    favicon: /^https:\/\//.test(o.favicon || '') ? o.favicon : '',
    tags: (o.tags || '').split(',').map(t => t.trim()).filter(Boolean).slice(0, 4),
    country: o.country || '', cc: (o.countrycode || '').toUpperCase(), codec: o.codec || '', bitrate: +o.bitrate || 0,
    hls: o.hls === 1 || o.hls === '1' || /\.m3u8(\?|$)/i.test(stream), place: o.state || '',
    lat: o.geo_lat != null ? +o.geo_lat : null, lon: o.geo_long != null ? +o.geo_long : null,
  };
}
/** "Kraków, Poland · Jazz · 128 kbps" */
const stationMeta = s => [[s.place, s.cc ? Where.name(s.cc) : s.country].filter(Boolean).join(', '),
  s.tags[0] && s.tags[0].charAt(0).toUpperCase() + s.tags[0].slice(1), s.bitrate ? `${s.bitrate} kbps` : ''].filter(Boolean).join(' · ');
/** Lower case without accents, so "radio" finds "Rádio". */
const fold = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();

const RadioBrowser = {
  hosts: ['de1.api.radio-browser.info', 'de2.api.radio-browser.info', 'fi1.api.radio-browser.info', 'all.api.radio-browser.info'],
  good: store.get('radioHost'),
  /** Hand-picked stations, each checked to be online, with tidy names. */
  PICKS: [
    ['9617a958-0601-11e8-ae97-52543be04c81', 'Radio Paradise'], ['961e6cac-0601-11e8-ae97-52543be04c81', 'NTS Radio 1'],
    ['932eb148-e6f6-11e9-a96c-52543be04c81', 'FIP'], ['445cbb3a-1c4e-49aa-a268-f5b6acfa8f2e', 'KEXP Seattle'],
    ['e4e4d123-0616-11ea-afd6-52543be04c81', 'LISTEN.moe J-Pop'], ['960cf833-0601-11e8-ae97-52543be04c81', 'SomaFM Groove Salad'],
    ['9067dc39-4bf4-4364-bd6b-8020cff3e15d', 'Nightride FM Chillsynth'], ['1d730b8a-49e2-403d-870c-07b1e27be7fd', 'Jazz24'],
    ['963fb390-0601-11e8-ae97-52543be04c81', 'Radio Nova'], ['5b9ceedf-eb85-11e9-a96c-52543be04c81', 'FIP Jazz'],
    ['d88c0d24-adb2-11e9-88f4-52543be04c81', 'Radio Paradise Mellow'], ['a1c3d110-9136-4564-bd41-5ec1b97c41ac', 'Record Megamix'],
    ['43be83dc-340b-11e8-bb9b-52543be04c81', 'LISTEN.moe K-Pop'],
  ],
  async get(path, params = {}) {
    let last;
    for (const host of [this.good, ...this.hosts.filter(x => x !== this.good)].filter(Boolean)) {
      try {
        const r = await fetch(`https://${host}/json/${path}?${new URLSearchParams(params)}`);
        if (r.ok) { if (this.good !== host) { this.good = host; store.set('radioHost', host); } return await r.json(); }
      } catch (e) { last = e; }
    }
    if (Demo.on) return DemoRadio.answer(path, params);
    throw last || new Error('The radio directory is out of reach.');
  },
  /** Only streams a phone can play: secure ones, and live HLS where the browser has it (Safari does). */
  playable(list) {
    const hls = !!document.createElement('audio').canPlayType('application/vnd.apple.mpegurl');
    return list.map(station).filter(s => s && (s.hls ? hls : true));
  },
  async stations(params) {
    const all = this.playable(await this.get('stations/search', { hidebroken: true, order: 'clickcount', reverse: true, is_https: true, ...params }));
    const seen = new Set();
    return all.filter(s => { const k = s.name.toLowerCase(); return !seen.has(k) && seen.add(k); });
  },
  top: (limit = 30) => RadioBrowser.stations({ limit }),
  loved: (limit = 12) => RadioBrowser.stations({ order: 'votes', limit }),
  country: (cc, limit = 80) => RadioBrowser.stations({ countrycode: cc, limit }),
  tag: (tag, limit = 80) => RadioBrowser.stations({ tag, limit }),
  search: q => RadioBrowser.stations({ name: q, limit: 60 }),
  /** Stations with a known spot on the map, for the globe. Kept for a week: the list is large. */
  async mapped() {
    const kept = store.get('radioMap');
    if (kept && Date.now() - kept.at < 7 * 864e5 && kept.list.length) return kept.list;
    const list = (await this.stations({ has_geo_info: true, limit: 3000 })).filter(s => s.lat != null && s.lon != null && (s.lat || s.lon));
    try { store.set('radioMap', { at: Date.now(), list }); } catch (e) { /* too big to keep: fetched again next time */ }
    return list;
  },
  async picks() {
    const order = this.PICKS.map(p => p[0]), names = Object.fromEntries(this.PICKS);
    const found = this.playable(await this.get('stations/byuuid', { uuids: order.join(',') }));
    return found.sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id)).map(s => ({ ...s, name: names[s.id] || s.name }));
  },
  async countries() {
    const list = await this.get('countries', { order: 'stationcount', reverse: true, hidebroken: true });
    const seen = new Set();
    return list.map(c => ({ code: (c.iso_3166_1 || '').toUpperCase(), stations: c.stationcount }))
      .filter(c => c.code.length === 2 && c.stations >= 5 && !seen.has(c.code) && seen.add(c.code));
  },
  /** Tells the directory a station was played, which keeps its popular lists honest. */
  click(id) { this.get('url/' + id).catch(() => {}); },
};

/** Your favourite and recently played stations, kept on this phone. */
const RadioLibrary = {
  get favorites() { return store.get('radioFav') || []; },
  get recents() { return store.get('radioRecent') || []; },
  isFavorite(s) { return this.favorites.some(x => x.id === s.id); },
  toggleFavorite(s) {
    const on = this.isFavorite(s);
    store.set('radioFav', on ? this.favorites.filter(x => x.id !== s.id) : [s, ...this.favorites]);
    toast(on ? 'Removed from your stations.' : 'Added to your stations.');
    Radio.changed();
  },
  played(s) { store.set('radioRecent', [s, ...this.recents.filter(x => x.id !== s.id)].slice(0, 12)); },
};

/** The radio, for any screen: what's on, and play, pause and stop. One audio element, played by the browser. */
const Radio = {
  station: null, playing: false, buffering: false,
  audio: null,
  el() {
    if (this.audio) return this.audio;
    const a = this.audio = new Audio();
    a.preload = 'none';
    a.addEventListener('playing', () => { this.playing = true; this.buffering = false; this.changed(); });
    a.addEventListener('waiting', () => { this.buffering = true; this.changed(); });
    a.addEventListener('pause', () => { this.playing = false; this.buffering = false; this.changed(); });
    a.addEventListener('error', () => {
      if (!this.station || !a.getAttribute('src')) return;
      this.playing = false; this.buffering = false; this.changed();
      toast(this.station.secure ? "This station isn't answering. Try another one." : "This station only streams without encryption, which iPhone won't play from a website. Try another one.");
    });
    if ('mediaSession' in navigator) {
      navigator.mediaSession.setActionHandler('play', () => this.toggle());
      navigator.mediaSession.setActionHandler('pause', () => this.toggle());
      navigator.mediaSession.setActionHandler('stop', () => this.stop());
    }
    return a;
  },
  play(s) {
    const a = this.el();
    this.station = s; this.buffering = true; this.playing = false;
    RadioLibrary.played(s);
    RadioBrowser.click(s.id);
    // one thing at a time: Spotify pauses while the radio plays
    if (Player.playing) Player.run(() => S.pause(), st => { st.is_playing = false; });
    a.src = s.stream;
    a.play().catch(e => { if (e.name === 'NotAllowedError') { this.buffering = false; this.changed(); toast('Tap play to start the radio.'); } });
    if ('mediaSession' in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({ title: s.name, artist: stationMeta(s) || 'Live radio', album: 'Fidelis radio',
        artwork: s.favicon ? [{ src: s.favicon, sizes: '256x256' }] : [{ src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' }] });
    }
    this.changed();
  },
  /** Pause, or pick the broadcast up again live rather than where you left it. */
  toggle() {
    const a = this.el();
    if (!this.station) return;
    if (this.playing || this.buffering) { a.pause(); return; }
    this.play(this.station);
  },
  stop() {
    if (this.audio) { this.audio.pause(); this.audio.removeAttribute('src'); this.audio.load(); }
    this.station = null; this.playing = false; this.buffering = false;
    if ('mediaSession' in navigator) navigator.mediaSession.metadata = null;
    this.changed();
  },
  changed() {
    renderMini();
    const l = Layers.get('radioSheet'); if (l) l.refresh();
    document.querySelectorAll('[data-station]').forEach(el => {
      const on = this.station && el.dataset.station === this.station.id;
      el.classList.toggle('on', !!on);
    });
    document.querySelectorAll('[data-fav]').forEach(el => {
      const fav = RadioLibrary.favorites.some(x => x.id === el.dataset.fav);
      el.classList.toggle('on', fav); el.innerHTML = ICON[fav ? 'heart' : 'heartLine'];
    });
  },
};

// ---- how stations look ----

/** A steady colour for each station, so cards without a logo still look their own. */
function stationHue(name) {
  let x = 0;
  for (const ch of name) x = (Math.imul(31, x) + ch.charCodeAt(0)) | 0;
  return ((x % 360) + 360) % 360;
}
const stationColor = (name, l = 42) => `hsl(${stationHue(name)} 55% ${l}%)`;

/** The station's logo on white, or its initials on its own colour when it has none (or it won't load). */
function stationLogo(s, size, radius, cls = '') {
  const initials = s.name.split(/\s+/).filter(w => /^[\p{L}\p{N}]/u.test(w)).slice(0, 2).map(w => w[0].toUpperCase()).join('') || '♪';
  const box = h('div.slogo' + (cls ? '.' + cls : ''), { style: { width: size, height: size, borderRadius: radius,
    background: `linear-gradient(135deg, ${stationColor(s.name, 52)}, ${stationColor(s.name, 22)})` } },
  h('span', { style: { fontSize: Math.round(size * .34) + 'px' } }, initials));
  if (s.favicon) {
    const img = h('img', { alt: '', loading: 'lazy', referrerPolicy: 'no-referrer' });
    img.onload = () => { if (img.naturalWidth > 8) { box.classList.add('logo'); box.style.background = '#fff'; } else img.remove(); };
    img.onerror = () => img.remove();
    img.src = s.favicon;
    box.append(img);
  }
  return box;
}
const liveDot = on => h('i.live' + (on ? '.on' : ''));

function stationRow(s) {
  const current = Radio.station && Radio.station.id === s.id;
  const fav = RadioLibrary.isFavorite(s);
  return h('div.srow' + (current ? '.on' : ''), { 'data-station': s.id, onclick: () => Radio.play(s) },
    stationLogo(s, 52, 14),
    h('div.grow', null, h('div.t1', null, s.name),
      h('div.t2', null, current && liveDot(Radio.playing), current && Radio.buffering ? 'Tuning in…' : stationMeta(s))),
    h('button.icbtn.fav' + (fav ? '.on' : ''), { 'aria-label': 'Favourite', 'data-fav': s.id, html: ICON[fav ? 'heart' : 'heartLine'],
      onclick: e => { e.stopPropagation(); RadioLibrary.toggleFavorite(s); } }));
}
function stationTile(s) {
  return h('button.stile', { 'data-station': s.id, onclick: () => Radio.play(s) },
    stationLogo(s, 124, 22, 'shadow'), h('div.t1', null, s.name), h('div.t2', null, s.cc ? Where.name(s.cc) : s.country));
}
function featureCard(s) {
  return h('button.feature', { 'data-station': s.id, style: { '--c': stationColor(s.name, 48), '--d': stationColor(s.name, 14) }, onclick: () => Radio.play(s) },
    stationLogo(s, 64, 16),
    h('span.badge', null, liveDot(true), 'LIVE'),
    h('div.bot', null, h('div.fname', null, s.name), h('div.t2', null, stationMeta(s))));
}

const GENRES = [
  ['Pop', 'pop', '#FF5E8A', '#8B5CC7'], ['Rock', 'rock', '#E4572E', '#5A1A2E'], ['Jazz', 'jazz', '#C9A227', '#3B2A12'],
  ['Classical', 'classical', '#8FA8C8', '#2A3550'], ['Electronic', 'electronic', '#22D3EE', '#4338CA'], ['Hip hop', 'hip', '#F59E0B', '#7C2D12'],
  ['Chill', 'chill', '#34D399', '#0F4C5C'], ['Dance', 'dance', '#EC4899', '#6D28D9'], ['News', 'news', '#94A3B8', '#1E293B'],
  ['Talk', 'talk', '#60A5FA', '#1E3A8A'], ['Oldies', 'oldies', '#D97706', '#451A03'], ['Metal', 'metal', '#6B7280', '#111827'],
  ['J-Pop', 'jpop', '#FF7AB6', '#3B1D6E'], ['Lo-fi', 'lofi', '#A78BFA', '#1F2A44'],
];

// ---- the Listen tab: Radio and Podcasts ----

const Listen = { mode: store.get('listenMode') || 'radio', q: '', timer: null };
function listenSwitch() {
  return h('div.switch2', null, [['radio', 'Radio'], ['podcasts', 'Podcasts']].map(([k, n]) =>
    h('button' + (Listen.mode === k ? '.on' : ''), { onclick: () => { Listen.mode = k; store.set('listenMode', k); App.render(true); } }, n)));
}
function listenScreen() { return Listen.mode === 'podcasts' ? podcastsScreen() : radioScreen(); }

function radioScreen() {
  const cc = Where.cc;
  const home = Data.get('radioHome', async () => {
    const [picks, near, top, countries] = await Promise.all([
      RadioBrowser.picks().catch(() => []), RadioBrowser.country(cc, 40), RadioBrowser.top(30), RadioBrowser.countries().catch(() => []),
    ]);
    const loved = picks.length ? [] : await RadioBrowser.loved(10).catch(() => []);
    return { featured: picks.length ? picks : [...near.slice(0, 4), ...loved].slice(0, 9), near, top, countries };
  });
  const q = Listen.q.trim();
  const out = h('div', null,
    h('div.hdr.row', null, h('div.grow', null, h('div.k', null, 'RADIO'), h('h1', null, 'Listen')), listenSwitch()),
    searchBox('Stations from anywhere in the world', Listen.q, v => { Listen.q = v; clearTimeout(Listen.timer); Listen.timer = setTimeout(() => App.render(), 400); }));
  if (q) {
    const res = Data.get('radioSearch:' + fold(q), async () => {
      const want = fold(q), words = want.split(' ').filter(Boolean);
      const list = await RadioBrowser.search(q);
      const rank = s => { const n = fold(s.name); return n === want ? 3 : words.every(w => n.includes(w)) ? 2 : words.some(w => n.includes(w)) ? 1 : 0; };
      return list.sort((a, b) => rank(b) - rank(a));
    });
    if (res === undefined) out.append(loading());
    else if (!res || !res.length) out.append(h('div.note.pad', null, res ? 'No stations by that name.' : Data.failed['radioSearch:' + fold(q)]));
    else out.append(h('div.list', null, res.map(stationRow)));
    return out;
  }
  if (home === undefined) { out.append(loading()); return out; }
  if (!home) {
    out.append(h('div.card.empty', null, h('div.t1', null, 'Radio is out of reach'), h('div.t2', null, 'Check your connection and try again.'),
      h('button.btn.glass', { onclick: () => { Data.drop('radioHome'); App.render(); } }, 'Try again')));
    return out;
  }
  const label = (t, more) => h('div.shead', null, h('h2', null, t), more);
  out.append(
    h('section', null, label('Fidelis picks'), h('div.shelf.wide', null, home.featured.map(featureCard))),
    globeCard());
  const fav = RadioLibrary.favorites, rec = RadioLibrary.recents;
  if (fav.length) out.append(h('section', null, label('Your stations'), h('div.shelf', null, fav.map(stationTile))));
  if (rec.length) out.append(h('section', null, label('Recently played'), h('div.shelf', null, rec.map(stationTile))));
  if (home.near.length) out.append(h('section', null,
    label(`Near you · ${Where.flag(cc)} ${Where.name(cc)}`, h('button.see', { onclick: () => Nav.push('radioBrowse', 'c:' + cc) }, 'See all')),
    h('div.list', null, home.near.slice(0, 8).map(stationRow))));
  out.append(
    h('section', null, label('Top worldwide'), h('div.shelf', null, home.top.map(stationTile))),
    h('section', null, label('Genres'), h('div.genres', null, GENRES.map(([n, tag, a, b]) =>
      h('button.genre', { style: { background: `linear-gradient(135deg, ${a}, ${b})` }, onclick: () => Nav.push('radioBrowse', 't:' + tag + ':' + n) }, n)))),
    h('section', null, label('Countries'), h('div.shelf.chips', null, home.countries.slice(0, 50).map(c =>
      h('button.chip.big', { onclick: () => Nav.push('radioBrowse', 'c:' + c.code) }, `${Where.flag(c.code)}  ${Where.name(c.code)}`)))));
  return out;
}

/** A country's or a genre's stations: "c:PL" or "t:jazz:Jazz". */
function radioBrowsePage(id) {
  const [kind, code, label] = id.split(':');
  const list = Data.get('radioBrowse:' + id, () => (kind === 'c' ? RadioBrowser.country(code, 150) : RadioBrowser.tag(code, 150)));
  return h('div', null,
    h('div.hdr', null, h('div.k', null, kind === 'c' ? 'COUNTRY' : 'GENRE'), h('h1', null, kind === 'c' ? `${Where.flag(code)} ${Where.name(code)}` : label)),
    list === undefined ? loading() : !list || !list.length ? h('div.note.pad', null, list ? 'Nothing on air here right now.' : Data.failed['radioBrowse:' + id])
      : h('div.list', null, list.map(stationRow)));
}

/** A search field like the one on Search, for the radio and podcasts. */
function searchBox(placeholder, value, onInput) {
  const input = h('input.searchIn', { type: 'search', value, placeholder, autocapitalize: 'off', autocomplete: 'off', enterkeyhint: 'search',
    oninput: e => onInput(e.target.value), onkeydown: e => { if (e.key === 'Enter') e.target.blur(); } });
  return h('div.searchBox', null, icon('search'), input,
    value && h('button.icbtn', { 'aria-label': 'Clear', onclick: () => { onInput(''); App.render(); } }, icon('close')));
}

// ---- the radio's mini player and its full card ----

function radioMini() {
  const s = Radio.station;
  const busy = Radio.playing || Radio.buffering;
  return h('div.mini.radio', { style: { '--tint': stationColor(s.name, 45) }, onclick: e => { if (!e.target.closest('button')) openRadioSheet(); } },
    stationLogo(s, 46, 16),
    h('div.grow', null, h('div.t1', null, s.name),
      h('div.t2', null, liveDot(Radio.playing), Radio.buffering ? 'Tuning in…' : `Live · ${s.cc ? Where.name(s.cc) : s.country || 'radio'}`)),
    h('button.playbtn.small', { 'aria-label': busy ? 'Pause' : 'Play', onclick: () => Radio.toggle() }, icon(busy ? 'pause' : 'play')),
    h('button.icbtn', { 'aria-label': 'Stop radio', onclick: () => Radio.stop() }, icon('close')));
}

function openRadioSheet() {
  sheet('radioSheet', () => {
    const s = Radio.station;
    if (!s) { setTimeout(() => Layers.close('radioSheet'), 0); return h('div'); }
    const busy = Radio.playing || Radio.buffering, fav = RadioLibrary.isFavorite(s);
    return h('div.rsheet', { style: { '--c': stationColor(s.name, 40) } },
      stationLogo(s, 220, 36, 'big'),
      h('div.k.live2', null, liveDot(Radio.playing), Radio.buffering ? 'TUNING IN' : 'LIVE'),
      h('h2', null, s.name),
      h('div.t2', null, stationMeta(s)),
      h('div.ctl', null,
        h('button.icbtn.glass.lg' + (fav ? '.on' : ''), { 'aria-label': 'Favourite', onclick: () => RadioLibrary.toggleFavorite(s) }, icon(fav ? 'heart' : 'heartLine')),
        h('button.playbtn', { 'aria-label': busy ? 'Pause' : 'Play', onclick: () => Radio.toggle() }, icon(busy ? 'pause' : 'play')),
        h('button.icbtn.glass.lg', { 'aria-label': 'Stop', onclick: () => { Radio.stop(); Layers.close('radioSheet'); } }, icon('stop'))),
      h('div.note.c', null, "Browsers don't pass on the song a station is playing, so Find on Spotify is in Fidelis for Android."));
  });
}

// ---- the demo's radio, for trying it without the directory ----

const DemoRadio = {
  list: [
    ['d-paradise', 'Radio Paradise', 'https://stream.radioparadise.com/aac-128', 'US', 'eclectic', 39.73, -121.84],
    ['d-groove', 'SomaFM Groove Salad', 'https://ice1.somafm.com/groovesalad-128-mp3', 'US', 'chill', 37.77, -122.42],
    ['d-fip', 'FIP', 'https://icecast.radiofrance.fr/fip-hifi.aac', 'FR', 'jazz', 48.85, 2.35],
    ['d-nts', 'NTS Radio 1', 'https://stream-relay-geo.ntslive.net/stream', 'GB', 'electronic', 51.53, -0.07],
    ['d-357', 'Radio 357', 'https://stream.radio357.pl', 'PL', 'pop', 52.23, 21.01],
    ['d-nowy', 'Radio Nowy Świat', 'https://stream.nowyswiat.online/aac', 'PL', 'talk', 52.22, 21.0],
    ['d-krakow', 'Radio Kraków', 'https://stream4.nadaje.com:9233/krakow', 'PL', 'news', 50.06, 19.94],
    ['d-moe', 'LISTEN.moe J-Pop', 'https://listen.moe/stream', 'JP', 'jpop', 35.68, 139.69],
  ],
  stations() {
    return this.list.map(([id, name, url, cc, tag, lat, lon]) => station({ stationuuid: id, name, url_resolved: url, countrycode: cc, country: Where.name(cc), tags: tag, bitrate: 128, geo_lat: lat, geo_long: lon }));
  },
  answer(path, params) {
    const all = this.stations().map(s => ({ stationuuid: s.id, name: s.name, url_resolved: s.stream, countrycode: s.cc, country: s.country, tags: s.tags.join(','), bitrate: 128, geo_lat: s.lat, geo_long: s.lon }));
    if (path === 'countries') return [['PL', 48], ['US', 120], ['FR', 64], ['GB', 70], ['JP', 22]].map(([c, n]) => ({ iso_3166_1: c, stationcount: n }));
    if (path.startsWith('url/')) return {};
    if (params.countrycode) return all.filter(s => s.countrycode === params.countrycode);
    if (params.tag) return all.filter(s => s.tags.includes(params.tag));
    if (params.name) return all.filter(s => fold(s.name).includes(fold(params.name)));
    return all;
  },
};
