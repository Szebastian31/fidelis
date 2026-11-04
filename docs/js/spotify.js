'use strict';
// Spotify for the web remote: PKCE sign-in with your own Client ID, the Web API, and a demo player for trying it out.
// Like SpotifyKey.kt, SpotifyPkce.kt and SpotifyRepository.kt in the Android app, minus the App Remote bridge.

const API = 'https://api.spotify.com/v1';
const DASHBOARD = 'https://developer.spotify.com/dashboard';
/** This page, as Spotify must have it listed: https://szebastian31.github.io/fidelis/ */
const REDIRECT_URI = location.origin + location.pathname.replace(/index\.html$/, '');
const SCOPES = [
  'user-read-playback-state', 'user-modify-playback-state', 'user-read-currently-playing',
  'user-read-recently-played', 'user-top-read', 'user-library-read', 'user-library-modify',
  'playlist-read-private', 'playlist-read-collaborative', 'playlist-modify-public', 'playlist-modify-private',
  'user-read-playback-position',
].join(' ');

const store = {
  get: k => { try { return JSON.parse(localStorage.getItem('fidelis.' + k)); } catch (e) { return null; } },
  set: (k, v) => { if (v == null) localStorage.removeItem('fidelis.' + k); else localStorage.setItem('fidelis.' + k, JSON.stringify(v)); },
};

/**
 * Where you are, for podcast markets, charts and radio near you. A browser has no phone network or SIM to ask, so it's
 * the language's region (pl-PL), then the time zone, then the US.
 */
const Where = {
  ZONES: { 'Europe/Warsaw': 'PL', 'Europe/London': 'GB', 'Europe/Berlin': 'DE', 'Europe/Paris': 'FR', 'Europe/Madrid': 'ES', 'Europe/Rome': 'IT',
    'Europe/Amsterdam': 'NL', 'Europe/Prague': 'CZ', 'Europe/Vienna': 'AT', 'Europe/Stockholm': 'SE', 'Europe/Oslo': 'NO', 'Europe/Copenhagen': 'DK',
    'Europe/Helsinki': 'FI', 'Europe/Dublin': 'IE', 'Europe/Lisbon': 'PT', 'Europe/Kyiv': 'UA', 'Europe/Kiev': 'UA', 'Europe/Vilnius': 'LT',
    'Europe/Brussels': 'BE', 'Europe/Zurich': 'CH', 'Europe/Budapest': 'HU', 'Europe/Bratislava': 'SK', 'Asia/Tokyo': 'JP', 'Asia/Seoul': 'KR',
    'Australia/Sydney': 'AU', 'America/Toronto': 'CA', 'America/Sao_Paulo': 'BR', 'America/Mexico_City': 'MX' },
  get cc() {
    if (this._cc) return this._cc;
    const fromLang = (navigator.languages || [navigator.language]).map(l => (l || '').split('-')[1]).find(r => r && /^[A-Za-z]{2}$/.test(r));
    let zone = '';
    try { zone = Intl.DateTimeFormat().resolvedOptions().timeZone; } catch (e) { /* no time zone */ }
    // the time zone says where the phone is; the language often just says "en-US" wherever you are
    return (this._cc = (this.ZONES[zone] || fromLang || 'US').toUpperCase());
  },
  name(code = this.cc) { try { return new Intl.DisplayNames(['en'], { type: 'region' }).of(code) || code; } catch (e) { return code; } },
  flag: code => code && code.length === 2 ? String.fromCodePoint(...[...code.toUpperCase()].map(c => 0x1F1E6 + c.charCodeAt(0) - 65)) : '🌍',
};

/** What a failed request means for the person holding the phone. */
class SpotifyError extends Error {
  constructor(status, reason, message) { super(message); this.status = status; this.reason = reason; }
}

const b64url = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const randomUrlSafe = n => b64url(crypto.getRandomValues(new Uint8Array(n)));

const Auth = {
  get clientId() { return store.get('clientId') || ''; },
  /** Spotify Client IDs are 32 hex characters. */
  valid: id => /^[0-9a-f]{32}$/i.test(id.trim()),
  setClientId(id) { store.set('clientId', id ? id.trim().toLowerCase() : null); },
  get signedIn() { return !!store.get('tokens'); },

  async login() {
    const verifier = randomUrlSafe(64), state = randomUrlSafe(16);
    const challenge = b64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
    store.set('pkce', { verifier, state });
    const q = new URLSearchParams({
      client_id: this.clientId, response_type: 'code', redirect_uri: REDIRECT_URI,
      code_challenge_method: 'S256', code_challenge: challenge, scope: SCOPES, state,
    });
    // the same window, not a new one: on a Home Screen app that keeps the sign-in inside the app
    location.assign('https://accounts.spotify.com/authorize?' + q);
  },

  /** Back from Spotify with ?code=… (or ?error=…). Returns null, or a message to show. */
  async handleRedirect() {
    const q = new URLSearchParams(location.search);
    if (!q.has('code') && !q.has('error')) return null;
    history.replaceState(null, '', REDIRECT_URI);
    const pkce = store.get('pkce');
    store.set('pkce', null);
    if (q.get('error')) return q.get('error') === 'access_denied' ? 'Sign-in was cancelled.' : `Spotify said: ${q.get('error')}`;
    if (!pkce || pkce.state !== q.get('state')) return 'That sign-in had expired. Try once more.';
    const r = await fetch('https://accounts.spotify.com/api/token', {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'authorization_code', code: q.get('code'), redirect_uri: REDIRECT_URI, client_id: this.clientId, code_verifier: pkce.verifier }),
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) return j.error_description === 'Invalid redirect URI'
      ? `Spotify doesn't know this address yet. Add ${REDIRECT_URI} as a Redirect URI in your Spotify app.`
      : `Spotify didn't sign you in (${j.error_description || j.error || r.status}).`;
    this.keep(j);
    return null;
  },

  keep(j) {
    const old = store.get('tokens') || {};
    store.set('tokens', { access: j.access_token, refresh: j.refresh_token || old.refresh, expires: Date.now() + (j.expires_in - 60) * 1000, scope: j.scope || old.scope || '' });
  },
  /** Whether this sign-in allows [scope]. Sign-ins from before a new feature need doing once more. */
  allows(scope) { if (Demo.on) return true; const t = store.get('tokens'); return !!t && (t.scope || '').split(' ').includes(scope); },

  refreshing: null,
  /** A valid access token, refreshed when it's about to run out. Signs out if Spotify won't refresh it. */
  async token() {
    const t = store.get('tokens');
    if (!t) throw new SpotifyError(401, 'SIGNED_OUT', 'Not signed in.');
    if (Date.now() < t.expires) return t.access;
    if (!this.refreshing) this.refreshing = (async () => {
      const r = await fetch('https://accounts.spotify.com/api/token', {
        method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: t.refresh, client_id: this.clientId }),
      });
      if (r.status === 400 || r.status === 401) { this.logout(); throw new SpotifyError(401, 'SIGNED_OUT', 'Spotify signed you out. Connect again in Profile.'); }
      if (!r.ok) throw new SpotifyError(r.status, 'REFRESH', "Couldn't reach Spotify.");
      this.keep(await r.json());
    })().finally(() => { this.refreshing = null; });
    await this.refreshing;
    return store.get('tokens').access;
  },
  logout() { store.set('tokens', null); store.set('me', null); },
};

/** The Web API. Returns parsed JSON, or null for an empty answer. */
async function api(method, path, { query, body } = {}) {
  if (Demo.on) return Demo.api(method, path, { query, body });
  const url = (path.startsWith('http') ? path : API + path) + (query ? '?' + new URLSearchParams(Object.entries(query).filter(([, v]) => v != null)) : '');
  for (let attempt = 0; attempt < 3; attempt++) {
    const r = await fetch(url, {
      method,
      headers: { Authorization: 'Bearer ' + await Auth.token(), ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (r.status === 204 || r.status === 202) return null;
    if (r.status === 429) { await new Promise(res => setTimeout(res, Math.min(8, +(r.headers.get('Retry-After') || 1)) * 1000)); continue; }
    if (r.status === 401 && attempt === 0) { const t = store.get('tokens'); if (t) { t.expires = 0; store.set('tokens', t); } continue; }
    const text = await r.text();
    let j = null;
    try { j = text ? JSON.parse(text) : null; } catch (e) { /* some player calls answer with plain text */ }
    if (r.ok) return j;
    const reason = j && j.error && j.error.reason;
    throw new SpotifyError(r.status, reason, explain(r.status, reason, j && j.error && j.error.message));
  }
  throw new SpotifyError(429, 'RATE', 'Spotify is busy. Try again in a moment.');
}

function explain(status, reason, message) {
  if (reason === 'PREMIUM_REQUIRED') return 'Controlling playback needs Spotify Premium.';
  if (reason === 'NO_ACTIVE_DEVICE' || status === 404) return 'Nothing is playing on Spotify right now. Open Spotify on a device, or pick one under Playing on.';
  if (status === 403) return /user may not be registered/i.test(message || '')
    ? "Your Spotify account isn't on this Spotify app's list. Add it under User Management on the Spotify dashboard."
    : `Spotify didn't allow that${message ? ` (${message})` : ''}.`;
  if (status >= 500) return "Spotify isn't answering properly. Try again in a moment.";
  return message || `Spotify said no (${status}).`;
}

/** Everything the screens need from Spotify, in one place. */
const S = {
  me: () => api('GET', '/me'),
  player: () => api('GET', '/me/player', { query: { additional_types: 'episode' } }),
  queue: () => api('GET', '/me/player/queue'),
  devices: async () => ((await api('GET', '/me/player/devices')) || {}).devices || [],
  recent: () => api('GET', '/me/player/recently-played', { query: { limit: 50 } }),
  topTracks: (range = 'short_term', limit = 20) => api('GET', '/me/top/tracks', { query: { limit, time_range: range } }),
  topArtists: (range = 'medium_term', limit = 12) => api('GET', '/me/top/artists', { query: { limit, time_range: range } }),
  playlists: () => api('GET', '/me/playlists', { query: { limit: 50 } }),
  liked: (offset = 0) => api('GET', '/me/tracks', { query: { limit: 50, offset } }),
  album: id => api('GET', `/albums/${id}`),
  artist: id => api('GET', `/artists/${id}`),
  artistAlbums: (id, groups) => api('GET', `/artists/${id}/albums`, { query: { include_groups: groups, limit: 20 } }),
  playlist: id => api('GET', `/playlists/${id}`),
  playlistItems: (id, offset = 0) => api('GET', `/playlists/${id}/items`, { query: { limit: 50, offset } }),
  addToPlaylist: (id, uris) => api('POST', `/playlists/${id}/items`, { body: { uris } }),
  /** Podcasts: the shows you follow, a show, and its episodes (with where you left off). */
  shows: () => api('GET', '/me/shows', { query: { limit: 50 } }),
  show: id => api('GET', `/shows/${id}`, { query: { market: Where.cc } }),
  episodes: (id, limit = 50) => api('GET', `/shows/${id}/episodes`, { query: { limit, market: Where.cc } }),
  /** At most 10 of each type: that's all Spotify gives Development Mode apps now. */
  search: (q, types = 'artist,album,track,playlist', limit = 10, offset = 0) => api('GET', '/search', { query: { q, type: types, limit, offset, market: types === 'show' ? Where.cc : null } }),
  saved: async uris => (await api('GET', '/me/library/contains', { query: { uris: uris.join(',') } })) || [],
  save: uris => api('PUT', '/me/library', { query: { uris: uris.join(',') } }),
  unsave: uris => api('DELETE', '/me/library', { query: { uris: uris.join(',') } }),

  play: (what = {}, device) => api('PUT', '/me/player/play', { query: { device_id: device }, body: Object.keys(what).length ? what : undefined }),
  pause: () => api('PUT', '/me/player/pause'),
  next: () => api('POST', '/me/player/next'),
  prev: () => api('POST', '/me/player/previous'),
  seek: ms => api('PUT', '/me/player/seek', { query: { position_ms: Math.max(0, Math.round(ms)) } }),
  shuffle: on => api('PUT', '/me/player/shuffle', { query: { state: on } }),
  repeat: mode => api('PUT', '/me/player/repeat', { query: { state: mode } }),
  volume: v => api('PUT', '/me/player/volume', { query: { volume_percent: Math.round(v) } }),
  enqueue: uri => api('POST', '/me/player/queue', { query: { uri } }),
  transfer: (id, play = true) => api('PUT', '/me/player', { body: { device_ids: [id], play } }),
};

// ---- the demo: a pretend Spotify with a handful of songs, for trying Fidelis without an account ----

const Demo = {
  get on() { return store.get('demo') === true; },
  set on(v) { store.set('demo', v ? true : null); },
  covers: {},
  cover(seed, a, b) {
    if (this.covers[seed]) return this.covers[seed];
    const c = document.createElement('canvas'); c.width = c.height = 320;
    const g = c.getContext('2d');
    const gr = g.createLinearGradient(0, 0, 320, 320); gr.addColorStop(0, a); gr.addColorStop(1, b);
    g.fillStyle = gr; g.fillRect(0, 0, 320, 320);
    for (let i = 0; i < 5; i++) {
      g.fillStyle = `rgba(255,255,255,${.06 + i * .03})`;
      g.beginPath(); g.arc(60 + ((seed * 53 + i * 71) % 220), 60 + ((seed * 31 + i * 97) % 220), 30 + i * 14, 0, Math.PI * 2); g.fill();
    }
    return (this.covers[seed] = c.toDataURL('image/jpeg', .85));
  },
  img(seed, a, b) { return [{ url: this.cover(seed, a, b), width: 320, height: 320 }]; },
  /** The demo listener's own songs come first; the rest are only found by searching, for mixes with new songs. */
  RAW: [
    ['Northern Lights', 'Aurora Vale', 'Polar Nights', 214000, '#1d6b5f', '#14213d', ['dream pop', 'ambient']],
    ['Moon Record', 'The Lyre Club', 'Fidēs', 187000, '#8b5cc7', '#e4657f', ['indie pop', 'happy']],
    ['Gliwice Rain', 'Silesian Tapes', 'Grey Mornings', 243000, '#3a7bff', '#0a587a', ['slowcore', 'sad']],
    ['Ember Hours', 'Kalina', 'Slow Fire', 201000, '#ff7a3d', '#451c12', ['deep house', 'dance']],
    ['Graphite Sky', 'Mono Atlas', 'Lines', 228000, '#55648a', '#16181e', ['post-rock', 'instrumental']],
    ['Ocean Glass', 'Riviera Kids', 'Tides', 196000, '#4fd0ff', '#062b4c', ['funk', 'disco']],
    ['Faithful', 'The Lyre Club', 'Fidēs', 232000, '#b49cff', '#282c5e', ['indie pop', 'happy']],
    ['Midnight Stamp', 'Annales', 'Sealed', 175000, '#cdae84', '#3b2a1a', ['lo-fi', 'chillhop']],
    ['Iron Bloom', 'Kestrel', 'Forge', 205000, '#6b1d2a', '#120808', ['metal', 'hard rock']],
    ['Paper Lanterns', 'Saki Mori', 'Kyoto Nights', 219000, '#ff9ab0', '#3b1d6e', ['j-pop', 'pop']],
    ['Low Tide', 'Harbour', 'Shore', 241000, '#2b6f7a', '#0b1b26', ['ambient', 'calm']],
    ['Strobe Choir', 'Neon Parish', 'Vigil', 189000, '#e43fb0', '#1d0b3b', ['techno', 'electro']],
    ['Sunday Porch', 'Old Pines', 'Porch Songs', 207000, '#d9a441', '#3b2a10', ['indie folk', 'acoustic']],
    ['Velvet Heat', 'Marisol', 'Calor', 198000, '#ff5e5e', '#3b0b12', ['nu disco', 'house']],
    ['Quiet Engine', 'Fieldnotes', 'Study', 233000, '#7fa8c8', '#1a2638', ['lo-fi', 'study']],
    ['Kraków Blue', 'Wisła Trio', 'Rynek', 252000, '#3a5bd9', '#0b1640', ['jazz', 'mellow']],
  ],
  songs() {
    if (this._songs) return this._songs;
    const slug = s => s.toLowerCase().replace(/[^a-z0-9]+/g, '');
    return (this._songs = this.RAW.map(([name, artist, album, ms, a, b], i) => ({
      id: 'demo' + i, uri: 'spotify:track:demo' + i, name, duration_ms: ms, type: 'track',
      artists: [{ id: 'art' + slug(artist), name: artist, uri: 'spotify:artist:art' + slug(artist) }],
      album: { id: 'alb' + slug(album), name: album, uri: 'spotify:album:alb' + slug(album), images: this.img(i + 1, a, b), release_date: '2026' },
    })));
  },
  /** The listener's library: the first eight songs. */
  mine() { return this.songs().slice(0, 8); },
  genresOf(artistId) { const i = this.songs().findIndex(t => t.artists[0].id === artistId); return i >= 0 ? this.RAW[i][6] : ['indie']; },
  shows() {
    if (this._shows) return this._shows;
    const raw = [['Slow History', 'The Archive', '#8f6a3a', '#2a1b0b'], ['Night Science', 'Lumen Studio', '#3a7bff', '#0b1640'], ['Two Coffees', 'Ola & Kuba', '#e4657f', '#3b0b20']];
    return (this._shows = raw.map(([name, publisher, a, b], i) => ({
      id: 'show' + i, uri: 'spotify:show:show' + i, name, publisher, type: 'show', images: this.img(60 + i, a, b),
      description: `${name}, made for the Fidelis demo.`, total_episodes: 3,
    })));
  },
  episodesOf(show) {
    const n = +show.id.slice(4);
    return [0, 1, 2].map(k => ({
      id: `ep${n}${k}`, uri: `spotify:episode:ep${n}${k}`, type: 'episode', name: [`Episode ${12 - k}: Where it began`, `Episode ${11 - k}: The long middle`, `Episode ${10 - k}: Afterwards`][k],
      description: 'A pretend episode, so the podcast screens have something to show.', duration_ms: (38 + n * 7 + k * 5) * 60000,
      release_date: new Date(Date.now() - (k * 4 + n) * 86400e3).toISOString().slice(0, 10), images: show.images,
      resume_point: { fully_played: k === 2, resume_position_ms: k === 0 && n === 1 ? 14 * 60000 : 0 }, show,
    }));
  },
  st: null,
  state() {
    if (!this.st) this.st = { i: 1, playing: true, at: Date.now(), pos: 42000, shuffle: false, repeat: 'off', volume: 64, device: 'd1', liked: { demo1: true, demo6: true } };
    const s = this.st, songs = this.songs();
    if (s.playing) {
      s.pos += Date.now() - s.at;
      if (s.ep) s.pos = Math.min(s.pos, s.ep.duration_ms);
      else while (s.pos >= songs[s.i].duration_ms) { s.pos -= songs[s.i].duration_ms; s.i = (s.i + 1) % songs.length; }
    }
    s.at = Date.now();
    return s;
  },
  devices: [
    { id: 'd1', name: "Szymon's iPhone", type: 'Smartphone', is_active: true, volume_percent: 64, supports_volume: false },
    { id: 'd2', name: 'MacBook Air', type: 'Computer', is_active: false, volume_percent: 50, supports_volume: true },
    { id: 'd3', name: 'Living room', type: 'Speaker', is_active: false, volume_percent: 30, supports_volume: true },
  ],
  async api(method, path, { query = {}, body } = {}) {
    await new Promise(r => setTimeout(r, 120));
    const s = this.state(), songs = this.songs(), mine = this.mine(), dev = this.devices.find(d => d.id === s.device);
    const page = items => ({ items, total: items.length, next: null });
    const cur = () => s.ep || songs[s.i];
    const play = () => ({ is_playing: s.playing, progress_ms: s.pos, item: cur(), shuffle_state: s.shuffle, repeat_state: s.repeat,
      device: { ...dev, volume_percent: s.volume, is_active: true }, currently_playing_type: s.ep ? 'episode' : 'track',
      context: s.ep ? null : { type: 'album', uri: songs[s.i].album.uri } });
    const at = q => {
      const i = songs.findIndex(t => t.uri === q);
      if (i >= 0) { s.i = i; s.pos = 0; s.playing = true; s.ep = null; return; }
      const ep = this.shows().flatMap(sh => this.episodesOf(sh)).find(e => e.uri === q);
      if (ep) { s.ep = ep; s.pos = ep.resume_point.resume_position_ms; s.playing = true; }
    };
    switch (method + ' ' + path) {
      case 'GET /me': return { id: 'demo', display_name: 'Demo listener', images: [] };
      case 'GET /me/player': return play();
      case 'GET /me/player/queue': return { currently_playing: cur(), queue: [1, 2, 3, 4, 5].map(k => mine[(s.i + k) % mine.length]) };
      case 'GET /me/player/devices': return { devices: this.devices.map(d => ({ ...d, is_active: d.id === s.device })) };
      case 'GET /me/player/recently-played': return page([2, 4, 0, 5, 7, 3].map((k, n) => ({ track: mine[k], played_at: new Date(Date.now() - n * 3600e3).toISOString(), context: { type: 'album', uri: mine[k].album.uri } })));
      case 'GET /me/top/tracks': return page((query.time_range === 'long_term' ? [0, 4, 2, 7] : query.time_range === 'medium_term' ? [6, 1, 3, 5, 0] : [1, 6, 0, 2, 5, 3, 4, 7]).map(k => mine[k]));
      case 'GET /me/top/artists': return page([...new Map(mine.map(t => [t.artists[0].id, t])).values()].map((t, i) => ({ ...t.artists[0], images: this.img(20 + i, '#666', '#222'), genres: this.genresOf(t.artists[0].id) })));
      case 'GET /me/playlists': return page([['Evening walk', '#1d6b5f', '#0a0f26'], ['Focus', '#55648a', '#0a0b0e'], ['Road to Kraków', '#ff7a3d', '#26110c']].map(([name, a, b], i) => ({ id: 'pl' + i, uri: 'spotify:playlist:pl' + i, name, images: this.img(40 + i, a, b), owner: { id: 'demo', display_name: 'Demo listener' }, collaborative: false, items: { total: 8 + (s.added && s.added['pl' + i] || 0) } })));
      case 'GET /me/tracks': return page(songs.filter(t => s.liked[t.id]).map(t => ({ track: t, added_at: '2026-10-01' })));
      case 'GET /me/shows': return page(this.shows().map(show => ({ show, added_at: '2026-09-01' })));
      case 'GET /search': {
        const raw = (query.q || '').toLowerCase();
        if (query.type === 'show') return { shows: page(this.shows().filter(x => !raw || (x.name + ' ' + x.publisher).toLowerCase().includes(raw.replace(/"/g, '')) || raw.length < 3)) };
        const genre = raw.match(/genre:"([^"]+)"/);
        const q = raw.replace(/^artist:"(.*)"$/, '$1');
        const hit = genre ? songs.filter((t, i) => this.RAW[i][6].includes(genre[1])) : songs.filter(t => (t.name + ' ' + t.artists[0].name + ' ' + t.album.name).toLowerCase().includes(q));
        const list = hit.slice(+query.offset || 0, (+query.offset || 0) + (+query.limit || 10));
        return { tracks: page(list), artists: page([...new Map(list.map(t => [t.artists[0].id, { ...t.artists[0], images: t.album.images }])).values()]), albums: page([...new Map(list.map(t => [t.album.id, { ...t.album, artists: t.artists, album_type: 'album' }])).values()]), playlists: page([]) };
      }
      case 'GET /me/library/contains': return query.uris.split(',').map(u => !!s.liked[u.split(':').pop()]);
      case 'PUT /me/library': query.uris.split(',').forEach(u => { s.liked[u.split(':').pop()] = true; }); return null;
      case 'DELETE /me/library': query.uris.split(',').forEach(u => { delete s.liked[u.split(':').pop()]; }); return null;
      case 'PUT /me/player/play': if (body && body.uris) at(body.uris[body.offset ? body.offset.position || 0 : 0]); else if (body && body.context_uri) { const t = songs.find(x => x.album.uri === body.context_uri) || songs[0]; at(t.uri); } else s.playing = true; if (query.device_id) s.device = query.device_id; return null;
      case 'PUT /me/player/pause': s.playing = false; return null;
      case 'POST /me/player/next': s.ep = null; s.i = (s.i + 1) % songs.length; s.pos = 0; return null;
      case 'POST /me/player/previous': if (s.pos > 3000 || s.ep) s.pos = 0; else { s.i = (s.i + songs.length - 1) % songs.length; s.pos = 0; } return null;
      case 'PUT /me/player/seek': s.pos = +query.position_ms; return null;
      case 'PUT /me/player/shuffle': s.shuffle = query.state === true || query.state === 'true'; return null;
      case 'PUT /me/player/repeat': s.repeat = query.state; return null;
      case 'PUT /me/player/volume': s.volume = +query.volume_percent; return null;
      case 'POST /me/player/queue': return null;
      case 'PUT /me/player': s.device = body.device_ids[0]; if (body.play) s.playing = true; return null;
    }
    let m;
    if ((m = path.match(/^\/albums\/([^/]+)$/))) { const tr = songs.filter(t => t.album.id === m[1]); const a = (tr[0] || songs[0]).album; return { ...a, artists: (tr[0] || songs[0]).artists, album_type: 'album', total_tracks: tr.length, tracks: page(tr.map((t, i) => ({ ...t, track_number: i + 1, disc_number: 1 }))) }; }
    if ((m = path.match(/^\/artists\/([^/]+)$/))) { const t = songs.find(x => x.artists[0].id === m[1]) || songs[0]; return { ...t.artists[0], images: t.album.images, genres: this.genresOf(m[1]) }; }
    if ((m = path.match(/^\/artists\/([^/]+)\/albums$/))) { const tr = songs.filter(t => t.artists[0].id === m[1]); return page(query.include_groups === 'album' ? [...new Map(tr.map(t => [t.album.id, { ...t.album, artists: t.artists, album_type: 'album' }])).values()] : []); }
    if ((m = path.match(/^\/playlists\/([^/]+)$/))) { const i = +m[1].slice(2); const pl = (await this.api('GET', '/me/playlists')).items[i]; return { ...pl, description: 'Made in the Fidelis demo.' }; }
    if ((m = path.match(/^\/playlists\/([^/]+)\/items$/))) {
      if (method === 'POST') { s.added = s.added || {}; s.added[m[1]] = (s.added[m[1]] || 0) + body.uris.length; return { snapshot_id: 'demo' }; }
      return page(mine.map(t => ({ item: t, added_at: '2026-10-01' })));
    }
    if ((m = path.match(/^\/shows\/([^/]+)$/))) return this.shows().find(x => x.id === m[1]) || this.shows()[0];
    if ((m = path.match(/^\/shows\/([^/]+)\/episodes$/))) return page(this.episodesOf(this.shows().find(x => x.id === m[1]) || this.shows()[0]));
    throw new SpotifyError(404, 'DEMO', 'Not in the demo.');
  },
};
