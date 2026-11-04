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
  'playlist-read-private', 'playlist-read-collaborative',
].join(' ');

const store = {
  get: k => { try { return JSON.parse(localStorage.getItem('fidelis.' + k)); } catch (e) { return null; } },
  set: (k, v) => { if (v == null) localStorage.removeItem('fidelis.' + k); else localStorage.setItem('fidelis.' + k, JSON.stringify(v)); },
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
    store.set('tokens', { access: j.access_token, refresh: j.refresh_token || old.refresh, expires: Date.now() + (j.expires_in - 60) * 1000 });
  },

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
  recent: () => api('GET', '/me/player/recently-played', { query: { limit: 30 } }),
  topTracks: () => api('GET', '/me/top/tracks', { query: { limit: 20, time_range: 'short_term' } }),
  topArtists: () => api('GET', '/me/top/artists', { query: { limit: 12, time_range: 'medium_term' } }),
  playlists: () => api('GET', '/me/playlists', { query: { limit: 50 } }),
  liked: (offset = 0) => api('GET', '/me/tracks', { query: { limit: 50, offset } }),
  album: id => api('GET', `/albums/${id}`),
  artist: id => api('GET', `/artists/${id}`),
  artistAlbums: (id, groups) => api('GET', `/artists/${id}/albums`, { query: { include_groups: groups, limit: 20 } }),
  playlist: id => api('GET', `/playlists/${id}`),
  playlistItems: (id, offset = 0) => api('GET', `/playlists/${id}/items`, { query: { limit: 50, offset } }),
  /** At most 10 of each type: that's all Spotify gives Development Mode apps now. */
  search: (q, types = 'artist,album,track,playlist', limit = 10) => api('GET', '/search', { query: { q, type: types, limit } }),
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
  songs() {
    if (this._songs) return this._songs;
    const raw = [
      ['Northern Lights', 'Aurora Vale', 'Polar Nights', 214000, '#1d6b5f', '#14213d'],
      ['Moon Record', 'The Lyre Club', 'Fidēs', 187000, '#8b5cc7', '#e4657f'],
      ['Gliwice Rain', 'Silesian Tapes', 'Grey Mornings', 243000, '#3a7bff', '#0a587a'],
      ['Ember Hours', 'Kalina', 'Slow Fire', 201000, '#ff7a3d', '#451c12'],
      ['Graphite Sky', 'Mono Atlas', 'Lines', 228000, '#55648a', '#16181e'],
      ['Ocean Glass', 'Riviera Kids', 'Tides', 196000, '#4fd0ff', '#062b4c'],
      ['Faithful', 'The Lyre Club', 'Fidēs', 232000, '#b49cff', '#282c5e'],
      ['Midnight Stamp', 'Annales', 'Sealed', 175000, '#cdae84', '#3b2a1a'],
    ];
    return (this._songs = raw.map(([name, artist, album, ms, a, b], i) => ({
      id: 'demo' + i, uri: 'spotify:track:demo' + i, name, duration_ms: ms, type: 'track',
      artists: [{ id: 'art' + artist.length, name: artist, uri: 'spotify:artist:art' + artist.length }],
      album: { id: 'alb' + album.length + album.charCodeAt(0), name: album, uri: 'spotify:album:alb' + album.length + album.charCodeAt(0), images: this.img(i + 1, a, b), release_date: '2026' },
    })));
  },
  st: null,
  state() {
    if (!this.st) this.st = { i: 1, playing: true, at: Date.now(), pos: 42000, shuffle: false, repeat: 'off', volume: 64, device: 'd1', liked: { demo1: true, demo6: true } };
    const s = this.st, songs = this.songs();
    if (s.playing) {
      s.pos += Date.now() - s.at;
      while (s.pos >= songs[s.i].duration_ms) { s.pos -= songs[s.i].duration_ms; s.i = (s.i + 1) % songs.length; }
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
    const s = this.state(), songs = this.songs(), dev = this.devices.find(d => d.id === s.device);
    const page = items => ({ items, total: items.length, next: null });
    const play = () => ({ is_playing: s.playing, progress_ms: s.pos, item: songs[s.i], shuffle_state: s.shuffle, repeat_state: s.repeat,
      device: { ...dev, volume_percent: s.volume, is_active: true }, currently_playing_type: 'track',
      context: { type: 'album', uri: songs[s.i].album.uri } });
    const at = (q, list) => { const i = songs.findIndex(t => t.uri === q); if (i >= 0) { s.i = i; s.pos = 0; s.playing = true; } };
    switch (method + ' ' + path) {
      case 'GET /me': return { id: 'demo', display_name: 'Demo listener', images: [] };
      case 'GET /me/player': return play();
      case 'GET /me/player/queue': return { currently_playing: songs[s.i], queue: [1, 2, 3, 4, 5].map(k => songs[(s.i + k) % songs.length]) };
      case 'GET /me/player/devices': return { devices: this.devices.map(d => ({ ...d, is_active: d.id === s.device })) };
      case 'GET /me/player/recently-played': return page([2, 4, 0, 5, 7, 3].map((k, n) => ({ track: songs[k], played_at: new Date(Date.now() - n * 3600e3).toISOString() })));
      case 'GET /me/top/tracks': return page([1, 6, 0, 2, 5, 3, 4, 7].map(k => songs[k]));
      case 'GET /me/top/artists': return page([...new Map(songs.map(t => [t.artists[0].id, t])).values()].map((t, i) => ({ ...t.artists[0], images: this.img(20 + i, '#666', t.album.images ? '#222' : '#000'), genres: ['indie'] })));
      case 'GET /me/playlists': return page([['Evening walk', '#1d6b5f', '#0a0f26'], ['Focus', '#55648a', '#0a0b0e'], ['Road to Kraków', '#ff7a3d', '#26110c']].map(([name, a, b], i) => ({ id: 'pl' + i, uri: 'spotify:playlist:pl' + i, name, images: this.img(40 + i, a, b), owner: { id: 'demo', display_name: 'Demo listener' }, items: { total: 8 } })));
      case 'GET /me/tracks': return page(songs.filter(t => s.liked[t.id]).map(t => ({ track: t, added_at: '2026-10-01' })));
      case 'GET /search': { const q = (query.q || '').toLowerCase().replace(/^artist:"(.*)"$/, '$1'); const hit = songs.filter(t => (t.name + ' ' + t.artists[0].name + ' ' + t.album.name).toLowerCase().includes(q));
        return { tracks: page(hit), artists: page([...new Map(hit.map(t => [t.artists[0].id, { ...t.artists[0], images: t.album.images }])).values()]), albums: page([...new Map(hit.map(t => [t.album.id, { ...t.album, artists: t.artists, album_type: 'album' }])).values()]), playlists: page([]) }; }
      case 'GET /me/library/contains': return query.uris.split(',').map(u => !!s.liked[u.split(':').pop()]);
      case 'PUT /me/library': query.uris.split(',').forEach(u => { s.liked[u.split(':').pop()] = true; }); return null;
      case 'DELETE /me/library': query.uris.split(',').forEach(u => { delete s.liked[u.split(':').pop()]; }); return null;
      case 'PUT /me/player/play': if (body && body.uris) at(body.uris[body.offset ? body.offset.position || 0 : 0]); else if (body && body.context_uri) { const t = songs.find(x => x.album.uri === body.context_uri) || songs[0]; at(t.uri); } else s.playing = true; if (query.device_id) s.device = query.device_id; return null;
      case 'PUT /me/player/pause': s.playing = false; return null;
      case 'POST /me/player/next': s.i = (s.i + 1) % songs.length; s.pos = 0; return null;
      case 'POST /me/player/previous': if (s.pos > 3000) s.pos = 0; else { s.i = (s.i + songs.length - 1) % songs.length; s.pos = 0; } return null;
      case 'PUT /me/player/seek': s.pos = +query.position_ms; return null;
      case 'PUT /me/player/shuffle': s.shuffle = query.state === true || query.state === 'true'; return null;
      case 'PUT /me/player/repeat': s.repeat = query.state; return null;
      case 'PUT /me/player/volume': s.volume = +query.volume_percent; return null;
      case 'POST /me/player/queue': return null;
      case 'PUT /me/player': s.device = body.device_ids[0]; if (body.play) s.playing = true; return null;
    }
    let m;
    if ((m = path.match(/^\/albums\/([^/]+)$/))) { const tr = songs.filter(t => t.album.id === m[1]); const a = (tr[0] || songs[0]).album; return { ...a, artists: (tr[0] || songs[0]).artists, album_type: 'album', total_tracks: tr.length, tracks: page(tr.map((t, i) => ({ ...t, track_number: i + 1, disc_number: 1 }))) }; }
    if ((m = path.match(/^\/artists\/([^/]+)$/))) { const t = songs.find(x => x.artists[0].id === m[1]) || songs[0]; return { ...t.artists[0], images: t.album.images, genres: ['indie', 'dream pop'] }; }
    if ((m = path.match(/^\/artists\/([^/]+)\/albums$/))) { const tr = songs.filter(t => t.artists[0].id === m[1]); return page(query.include_groups === 'album' ? [...new Map(tr.map(t => [t.album.id, { ...t.album, artists: t.artists, album_type: 'album' }])).values()] : []); }
    if ((m = path.match(/^\/playlists\/([^/]+)$/))) { const i = +m[1].slice(2); const pl = (await this.api('GET', '/me/playlists')).items[i]; return { ...pl, description: 'Made in the Fidelis demo.' }; }
    if ((m = path.match(/^\/playlists\/([^/]+)\/items$/))) return page(songs.map(t => ({ item: t, added_at: '2026-10-01' })));
    throw new SpotifyError(404, 'DEMO', 'Not in the demo.');
  },
};
