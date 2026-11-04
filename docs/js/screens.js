'use strict';
// The screens: Listen (ui/home), Search, album, artist and playlist pages (ui/catalog, ui/playlists), Profile and setup.

/** Data for the screens, fetched once and kept for the visit. [get] returns undefined while it's on its way. */
const Data = {
  v: {}, loading: {}, failed: {},
  get(key, fn) {
    if (key in this.v) return this.v[key];
    if (!this.loading[key]) {
      this.loading[key] = true;
      fn().then(r => { this.v[key] = r; }, e => { this.failed[key] = e instanceof SpotifyError ? e.message : "Couldn't reach Spotify."; this.v[key] = null; })
        .finally(() => { delete this.loading[key]; App.render(); });
    }
    return undefined;
  },
  drop(prefix) { Object.keys(this.v).forEach(k => { if (k.startsWith(prefix)) { delete this.v[k]; delete this.failed[k]; } }); },
};

/** Each tab keeps its own stack of pages, so Back goes where you came from. */
const Nav = {
  tab: ['playlists', 'search', 'listen', 'mood', 'profile'].includes(store.get('tab')) ? store.get('tab') : 'playlists', stacks: { playlists: [], search: [], listen: [], mood: [], profile: [] }, contextName: {},
  get stack() { return this.stacks[this.tab] || (this.stacks[this.tab] = []); },
  push(kind, id, extra) { this.stack.push({ kind, id, extra, top: 0 }); App.render(true); },
  back() { this.stack.pop(); App.render(); },
  go(tab) {
    if (tab === this.tab && this.stack.length) this.stacks[tab] = [];
    this.tab = tab; store.set('tab', tab); App.render(true);
  },
};

const loading = () => h('div.note.c', { style: { padding: '40px 0' } }, h('span.spin'), 'Loading…');
const failed = key => h('div.note.c', { style: { padding: '40px 20px' } }, Data.failed[key] || "Couldn't load this.");
const back = label => h('button.back', { onclick: () => Nav.back() }, icon('back'), label);

function greeting() {
  const hr = new Date().getHours();
  return hr < 5 ? 'Good night' : hr < 12 ? 'Good morning' : hr < 18 ? 'Good afternoon' : 'Good evening';
}

// ---- Playlists: your library, For you first (ui/playlists/PlaylistsScreen.kt) ----
const Lib = { mine: false };
function playlistsScreen() {
  const top = Data.get('top', () => S.topTracks());
  const recent = Data.get('recent', () => S.recent());
  const artists = Data.get('artists', () => S.topArtists('medium_term', 12));
  const pls = Data.get('playlists', () => S.playlists());
  const me = Data.get('me', () => S.me());
  const first = me && me.display_name ? me.display_name.split(' ')[0] : '';
  const lists = ((pls && pls.items) || []).filter(Boolean);
  lists.forEach(p => { Nav.contextName[p.uri] = p.name; });
  const shelfList = Pins.order(lists);

  const out = h('div', null,
    h('div.hdr', null, h('div.k', null, greeting().toUpperCase() + (first ? `, ${first.toUpperCase()}` : '')), h('h1', null, 'Playlists')),
    h('div.chips', { style: { paddingBottom: '6px' } },
      h('button.chip' + (!Lib.mine ? '.on' : ''), { onclick: () => { Lib.mine = false; App.render(true); } }, 'For you'),
      h('button.chip' + (Lib.mine ? '.on' : ''), { onclick: () => { Lib.mine = true; App.render(true); } }, 'Your playlists')));
  const liked = () => h('button.tile', { onclick: () => Nav.push('liked') }, h('div.cover.sq.likedCover', null, icon('heart')), h('div.t1', null, 'Liked Songs'), h('div.t2', null, 'Playlist'));

  if (Lib.mine) {
    out.append(h('div.grid', { style: { marginTop: '12px' } }, liked(), shelfList.map(playlistTile)),
      pls === undefined ? loading() : h('div.note.pad', null, 'Tap ⋮ on any playlist to pin it to the top.'));
    return out;
  }
  if (top === undefined && recent === undefined) { out.append(loading()); return out; }

  if (shelfList.length) out.append(h('section', null,
    h('div.shead.col', null, h('h2', null, 'Your playlists'), h('span.sub', null, Pins.list.length ? 'Pinned first' : 'Tap ⋮ on any playlist to pin it here')),
    h('div.shelf.small', null, liked(), shelfList.slice(0, 20).map(playlistTile),
      h('button.tile.seeall', { onclick: () => { Lib.mine = true; App.render(true); } }, h('div.cover.sq', null, icon('back', 'flip')), h('div.t1', null, 'See all')))));

  const rec = recents(recent, lists);
  if (rec.length) out.append(h('section', null, h('div.shead', null, h('h2', null, 'Recents')),
    h('div.recents', null, rec.map(r => h('button.rtile', { onclick: r.open }, coverEl(r.images, 56, r.round ? 'circle' : 'sq'), h('div.t1', null, r.name))))));

  const quick = top && top.items ? top.items.slice(0, 8) : [];
  if (quick.length) out.append(h('section', null,
    h('div.shead.col', null, h('h2', null, 'Quick picks'), h('span.sub', null, 'Your most played, last 4 weeks')),
    h('div.quick', null, quick.map((t, i) => songRow(t, { list: quick, index: i })))));
  const seen = new Set();
  const again = recent && recent.items ? recent.items.map(x => x.track).filter(t => t && t.album && !seen.has(t.album.id) && seen.add(t.album.id)).slice(0, 12) : [];
  out.append(
    shelf('Listen again', again.map(t => tile(t.album.images, t.name, names(t.artists), () => Player.playTrack(t, { context: t.album.uri }))), h('span.sub', null, 'Recently played songs')) || '',
    shelf('Heavy rotation', (artists && artists.items || []).map(a => tile(a.images, a.name, 'Artist', () => Nav.push('artist', a.id), true)), h('span.sub', null, 'Your top artists, last 6 months')) || '',
    h('section', null, h('div.shead', null, h('h2', null, 'All your playlists')), h('div.grid', null, liked(), shelfList.map(playlistTile))));
  if (pls === undefined) out.append(loading());
  else if (!lists.length) out.append(h('div.note.pad', null, "No playlists yet, or Spotify couldn't be reached."));
  return out;
}

/** What you played lately, as places to go back to: its album, playlist or artist, newest first. */
function recents(recent, lists) {
  const out = [], seen = new Set();
  for (const x of (recent && recent.items) || []) {
    const t = x.track; if (!t) continue;
    const c = x.context, uri = c && c.uri;
    if (uri && seen.has(uri)) continue;
    if (c && c.type === 'playlist') {
      const id = uri.split(':').pop(), p = lists.find(l => l.id === id);
      if (!p) continue;
      seen.add(uri); out.push({ name: p.name, images: p.images, open: () => Nav.push('playlist', id) });
    } else if (c && c.type === 'artist') {
      const a = t.artists.find(z => z.uri === uri) || t.artists[0];
      seen.add(uri); out.push({ name: a.name, images: t.album.images, round: true, open: () => Nav.push('artist', a.id) });
    } else if (t.album && !seen.has(t.album.uri)) {
      seen.add(t.album.uri); out.push({ name: t.album.name, images: t.album.images, open: () => Nav.push('album', t.album.id) });
    }
    if (out.length >= 8) break;
  }
  return out;
}

/** A playlist tile: tap to open, ⋮ to pin it or play it. */
function playlistTile(p) {
  const pinned = Pins.has(p.id);
  return h('div.tile.pl', { onclick: () => Nav.push('playlist', p.id) },
    coverEl(p.images, 140, 'sq'), pinned && h('span.pin', null, icon('pin')),
    h('div.row.tl', null, h('div.grow', null, h('div.t1', null, p.name), h('div.t2', null, (p.items || p.tracks || {}).total != null ? songs((p.items || p.tracks).total) : '')),
      h('button.icbtn.sm', { 'aria-label': 'More', onclick: e => { e.stopPropagation(); playlistMenu(p); } }, icon('more'))));
}
function playlistMenu(p) {
  const pinned = Pins.has(p.id);
  sheet('menu', () => [
    h('div.row.mhead', null, coverEl(p.images, 52, 'sq'), h('div.grow', null, h('div.t1', null, p.name), h('div.t2', null, p.owner ? p.owner.display_name : ''))),
    h('button.mi', { onclick: () => { Layers.close('menu'); Pins.toggle(p.id); App.render(); } }, icon('pin'), pinned ? 'Unpin' : 'Pin to top'),
    h('button.mi', { onclick: () => { Layers.close('menu'); Player.playContext(p.uri); } }, icon('play'), 'Play'),
    h('button.mi', { onclick: () => { Layers.close('menu'); Nav.push('playlist', p.id); } }, icon('queue'), 'Open playlist'),
  ]);
}

/** Pinned playlists, kept on this phone: they come first in Your playlists. */
const Pins = {
  get list() { return store.get('pins') || []; },
  has(id) { return this.list.includes(id); },
  toggle(id) { const l = this.list; store.set('pins', l.includes(id) ? l.filter(x => x !== id) : [id, ...l]); toast(l.includes(id) ? 'Unpinned.' : 'Pinned to the top.'); },
  order(lists) { const p = this.list; return [...p.map(id => lists.find(l => l.id === id)).filter(Boolean), ...lists.filter(l => !p.includes(l.id))]; },
};

// ---- Search ----
const Find = { q: '', timer: null, recent: store.get('recentSearches') || [] };
function searchScreen() {
  const input = h('input.searchIn', {
    type: 'search', value: Find.q, placeholder: 'Artists, songs, albums', autocapitalize: 'off', autocomplete: 'off', enterkeyhint: 'search',
    oninput: e => {
      Find.q = e.target.value;
      clearTimeout(Find.timer);
      Find.timer = setTimeout(() => App.render(), 350);
    },
    onkeydown: e => { if (e.key === 'Enter') { e.target.blur(); remember(Find.q); App.render(); } },
  });
  const q = Find.q.trim();
  const out = h('div', null, h('div.hdr', null, h('h1', null, 'Search')), h('div.searchBox', null, icon('search'), input,
    q && h('button.icbtn', { 'aria-label': 'Clear', onclick: () => { Find.q = ''; App.render(); setTimeout(() => document.querySelector('.searchIn').focus(), 0); } }, icon('close'))));
  if (!q) {
    if (Find.recent.length) out.append(h('section', null, h('div.shead', null, h('h2', null, 'Recent searches')),
      h('div.chips', null, Find.recent.map(s => h('button.chip', { onclick: () => { Find.q = s; App.render(); } }, s)))));
    else out.append(h('div.note', { style: { padding: '8px 22px' } }, 'Find an artist, an album or a song, and play it on any of your devices.'));
    return out;
  }
  const r = Data.get('search:' + q.toLowerCase(), () => S.search(q));
  if (r === undefined) { out.append(loading()); return out; }
  if (!r) { out.append(failed('search:' + q.toLowerCase())); return out; }
  const tracks = (r.tracks && r.tracks.items || []).filter(Boolean);
  const artists = (r.artists && r.artists.items || []).filter(Boolean);
  const albums = (r.albums && r.albums.items || []).filter(Boolean);
  const playlists = (r.playlists && r.playlists.items || []).filter(Boolean);
  if (!tracks.length && !artists.length && !albums.length) { out.append(h('div.note', { style: { padding: '8px 22px' } }, `Nothing on Spotify for “${q}”.`)); return out; }
  const pick = () => remember(q);
  out.append(
    shelf('Artists', artists.map(a => tile(a.images, a.name, 'Artist', () => { pick(); Nav.push('artist', a.id); }, true))) || '',
    tracks.length ? h('section', null, h('div.shead', null, h('h2', null, 'Songs')), tracks.map((t, i) => h('div', { onclick: pick }, songRow(t, { list: tracks, index: i })))) : '',
    shelf('Albums', albums.map(a => tile(a.images, a.name, `${a.album_type === 'single' ? 'Single' : 'Album'} · ${names(a.artists)}`, () => { pick(); Nav.push('album', a.id); }))) || '',
    shelf('Playlists', playlists.map(p => tile(p.images, p.name, p.owner && p.owner.display_name, () => { pick(); Nav.contextName[p.uri] = p.name; Nav.push('playlist', p.id); }))) || '',
    h('div.note', { style: { padding: '4px 22px 0' } }, 'Spotify shows apps like Fidelis up to 10 results of each kind.'));
  return out;
}
function remember(q) {
  q = q.trim(); if (!q) return;
  Find.recent = [q, ...Find.recent.filter(s => s.toLowerCase() !== q.toLowerCase())].slice(0, 8);
  store.set('recentSearches', Find.recent);
}

// ---- album, artist, playlist, liked songs ----
function detailHeader({ images, round, kicker, title, sub, onPlay, onShuffle, saveUri }) {
  const saved = saveUri ? Data.get('saved:' + saveUri, async () => (await S.saved([saveUri]))[0]) : undefined;
  return h('div.dhead', null,
    coverEl(images, 260, round ? 'circle' : 'sq'),
    h('div.k', null, kicker), h('h1', null, title), sub && h('div.t2', null, sub),
    h('div.acts', null,
      onPlay && h('button.btn.grad', { onclick: onPlay }, icon('play'), 'Play'),
      onShuffle && h('button.btn.glass', { onclick: onShuffle }, icon('shuffle'), 'Shuffle'),
      saveUri && h('button.icbtn.glass.heart' + (saved ? '.on' : ''), {
        'aria-label': saved ? 'Remove from your library' : 'Save to your library',
        onclick: async () => {
          if (await attempt(() => (saved ? S.unsave([saveUri]) : S.save([saveUri]))) === undefined) return;
          Data.v['saved:' + saveUri] = !saved; if (saveUri.includes(':playlist:') || saveUri.includes(':album:')) Data.drop('playlists');
          toast(saved ? 'Removed from your library.' : 'Saved to your library.'); App.render();
        },
      }, icon(saved ? 'heart' : 'heartLine'))));
}

function albumPage(id) {
  const a = Data.get('album:' + id, () => S.album(id));
  if (a === undefined) return loading();
  if (!a) return failed('album:' + id);
  const tracks = (a.tracks && a.tracks.items || []).filter(Boolean).map(t => ({ ...t, album: { id: a.id, name: a.name, uri: a.uri, images: a.images } }));
  const discs = new Set(tracks.map(t => t.disc_number)).size > 1;
  const length = tracks.reduce((s, t) => s + t.duration_ms, 0);
  Nav.contextName[a.uri] = a.name;
  return h('div', null,
    detailHeader({ images: a.images, kicker: (a.album_type === 'single' ? 'SINGLE' : a.album_type === 'compilation' ? 'COMPILATION' : 'ALBUM') + (a.release_date ? ' · ' + a.release_date.slice(0, 4) : ''),
      title: a.name, sub: `${names(a.artists)} · ${songs(tracks.length)} · ${total(length)}`,
      onPlay: () => Player.playContext(a.uri), onShuffle: () => Player.playContext(a.uri, true), saveUri: a.uri }),
    h('div.list', null, tracks.map((t, i) => [
      discs && (i === 0 || tracks[i - 1].disc_number !== t.disc_number) && h('div.k.disc', null, `DISC ${t.disc_number}`),
      songRow(t, { context: a.uri, number: t.track_number }),
    ])),
    a.artists && a.artists[0] && h('button.more', { onclick: () => Nav.push('artist', a.artists[0].id) }, `More by ${a.artists[0].name}`, icon('back', 'flip')));
}

function artistPage(id) {
  const a = Data.get('artist:' + id, () => S.artist(id));
  const albums = Data.get('artistAlbums:' + id, () => S.artistAlbums(id, 'album'));
  const singles = Data.get('artistSingles:' + id, () => S.artistAlbums(id, 'single'));
  if (a === undefined) return loading();
  if (!a) return failed('artist:' + id);
  // Spotify no longer shares an artist's top songs; a search for their name is the closest there is
  const songs = Data.get('artistSongs:' + id, async () => {
    const r = await S.search(`artist:"${a.name}"`, 'track', 10);
    return ((r.tracks && r.tracks.items) || []).filter(t => t && t.artists.some(x => x.id === id));
  });
  return h('div', null,
    detailHeader({ images: a.images, round: true, kicker: 'ARTIST', title: a.name, sub: (a.genres || []).slice(0, 3).join(' · '),
      onPlay: () => Player.playContext(a.uri), onShuffle: () => Player.playContext(a.uri, true), saveUri: a.uri }),
    songs && songs.length ? h('section', null, h('div.shead', null, h('h2', null, 'Songs')), songs.map((t, i) => songRow(t, { list: songs, index: i }))) : songs === undefined ? loading() : '',
    shelf('Albums', (albums && albums.items || []).filter(Boolean).map(x => tile(x.images, x.name, (x.release_date || '').slice(0, 4), () => Nav.push('album', x.id)))) || '',
    shelf('Singles & EPs', (singles && singles.items || []).filter(Boolean).map(x => tile(x.images, x.name, (x.release_date || '').slice(0, 4), () => Nav.push('album', x.id)))) || '');
}

function playlistPage(id) {
  const p = Data.get('playlist:' + id, () => S.playlist(id));
  if (p === undefined) return loading();
  if (!p) return failed('playlist:' + id);
  Nav.contextName[p.uri] = p.name;
  const me = Data.v.me;
  const mine = p.owner && me && p.owner.id === me.id || p.collaborative || Demo.on;
  const items = mine ? Data.get('playlistItems:' + id, () => S.playlistItems(id)) : null;
  const tracks = items && items.items ? items.items.map(x => x.item || x.track).filter(t => t && t.uri && t.type !== 'episode') : [];
  const count = (p.items || p.tracks || {}).total;
  const desc = (p.description || '').replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/&#x27;|&#39;/g, "'").replace(/&quot;/g, '"');
  return h('div', null,
    detailHeader({ images: p.images, kicker: 'PLAYLIST', title: p.name, sub: [p.owner && p.owner.display_name, count != null ? songs(count) : null].filter(Boolean).join(' · '),
      onPlay: () => Player.playContext(p.uri), onShuffle: () => Player.playContext(p.uri, true), saveUri: mine ? null : p.uri }),
    desc && h('div.note', { style: { padding: '0 22px 10px' } }, desc),
    !mine ? h('div.note', { style: { padding: '6px 22px' } }, "Spotify only shows apps the songs of playlists you made or collaborate on. Play still plays it all, in Spotify.")
      : items === undefined ? loading()
        : h('div.list', null, tracks.map(t => songRow(t, { context: p.uri }))));
}

function likedPage() {
  const first = Data.get('liked:0', () => S.liked(0));
  if (first === undefined) return loading();
  if (!first) return failed('liked:0');
  const pages = [first];
  for (let o = 50; Data.v['liked:' + o]; o += 50) pages.push(Data.v['liked:' + o]);
  const tracks = pages.flatMap(p => p.items.map(x => x.track)).filter(Boolean);
  const last = pages[pages.length - 1], more = !!last.next;
  return h('div', null,
    h('div.dhead', null, h('div.cover.sq.likedCover', null, icon('heart')), h('div.k', null, 'PLAYLIST'), h('h1', null, 'Liked Songs'), h('div.t2', null, songs(first.total)),
      h('div.acts', null,
        h('button.btn.grad', { onclick: () => tracks[0] && Player.playTrack(tracks[0], { list: tracks, index: 0 }) }, icon('play'), 'Play'),
        h('button.btn.glass', { onclick: async () => { await attempt(() => S.shuffle(true), true); const i = Math.floor(Math.random() * tracks.length); if (tracks[i]) Player.playTrack(tracks[i], { list: tracks, index: i }); } }, icon('shuffle'), 'Shuffle'))),
    h('div.list', null, tracks.map((t, i) => songRow(t, { list: tracks, index: i }))),
    more && h('button.more', { onclick: () => { const o = pages.length * 50; Data.get('liked:' + o, () => S.liked(o)); } }, 'Show more'));
}

// ---- Profile ----
function profileScreen() {
  const me = Data.get('me', () => S.me());
  const pic = me && me.images && me.images[0] && me.images[0].url;
  const id = Auth.clientId;
  const skinTile = ([k, s]) => h('button.skin' + (k === Look.name ? '.on' : ''), { onclick: () => { Look.apply(k); App.render(); } },
    h('div.sw', { style: { background: `radial-gradient(circle at 30% 30%, ${s.glow1}, transparent 60%), radial-gradient(circle at 75% 70%, ${s.glow3}, transparent 60%), linear-gradient(${s.top}, ${s.mid} 60%, ${s.bottom})` } },
      h('i', { style: { background: `linear-gradient(135deg, ${s.start}, ${s.end})` } })),
    h('span', null, s.label));
  return h('div', null,
    h('div.hdr', null, h('h1', null, 'Profile')),
    h('div.card.acct', null,
      h('div.av', null, pic ? h('img', { src: pic, alt: '' }) : (me && me.display_name || 'F').charAt(0).toUpperCase()),
      h('div.grow', null, h('div.t1', null, me ? me.display_name || me.id : Demo.on ? 'Demo listener' : '…'), h('div.t2', null, Demo.on ? 'The demo: nothing here is your real Spotify' : 'Signed in to Spotify')),
      h('button.chip', { onclick: () => { if (Demo.on) Demo.on = false; else Auth.logout(); Data.v = {}; Player.set(null); App.render(); } }, Demo.on ? 'Leave the demo' : 'Sign out')),

    h('div.sec', null, 'LOOK'),
    h('div.card', null, h('div.skins', null, Object.entries(SKINS).map(skinTile)),
      h('label.toggle', null, h('div.grow', null, h('div.t1', null, 'Follow the music'), h('div.t2', null, "The sky takes on the playing cover's colour")),
        h('input', { type: 'checkbox', checked: Look.follow, onchange: e => { store.set('follow', e.target.checked); Look.tint(Player.tint); } }), h('span.sw2'))),

    h('div.sec', null, 'YOUR SPOTIFY APP'),
    h('div.card', null,
      h('div.t1', null, Demo.on ? 'None, in the demo' : `Client ID …${id.slice(-6)}`),
      h('div.t2', null, 'Fidelis signs in through a Spotify app of your own. The same one works for Fidelis on Android, with both Redirect URIs added.'),
      h('div.copy', null, h('code', null, REDIRECT_URI), h('button.icbtn', { 'aria-label': 'Copy', onclick: () => copy(REDIRECT_URI) }, icon('copy'))),
      h('div.acts', null,
        h('a.btn.glass', { href: DASHBOARD, target: '_blank', rel: 'noopener' }, 'Spotify dashboard'),
        h('button.btn.glass', { onclick: () => { Auth.logout(); Auth.setClientId(null); Demo.on = false; Data.v = {}; Player.set(null); App.render(); } }, 'Change Client ID'))),

    h('div.sec', null, 'ON THIS IPHONE'),
    h('div.card', null,
      !isStandalone() && h('div', { style: { marginBottom: '10px' } }, h('div.t1', null, 'Add to Home Screen'), h('div.t2', null, 'In Safari: Share, then Add to Home Screen. Fidelis opens like an app, full screen.')),
      h('div.t1', null, 'How it plays'),
      h('div.t2', null, "Fidelis is a remote for the Spotify app: the music plays in Spotify, on this phone or any speaker, TV or computer it can see. Controlling it needs Spotify Premium. The radio and podcasts from the web play right here in Fidelis. Last.fm scrobbling is in Fidelis for Android.")),
    h('div.note.c', { style: { padding: '18px 22px' } }, 'Fidelis 2.1.0 · web · ', h('a', { href: 'https://github.com/Szebastian31/fidelis', target: '_blank', rel: 'noopener' }, 'GitHub')));
}

async function copy(text) {
  try { await navigator.clipboard.writeText(text); toast('Copied.'); } catch (e) { toast(text); }
}

// ---- setup: your own Spotify app, then signing in ----
function setupScreen(message) {
  const st = { id: Auth.clientId };
  const input = h('input.field', { value: st.id, placeholder: 'Client ID', autocapitalize: 'off', autocomplete: 'off', spellcheck: false,
    oninput: e => { st.id = e.target.value; go.disabled = !Auth.valid(st.id); problem.textContent = st.id.trim().length >= 32 && !Auth.valid(st.id) ? "That doesn't look like a Client ID. It's 32 letters and numbers." : ''; } });
  const problem = h('div.warn');
  const go = h('button.btn.grad.wide', { disabled: !Auth.valid(st.id), onclick: () => { Auth.setClientId(st.id); Auth.login(); } }, 'Connect to Spotify');
  const step = (n, text, ...extra) => h('div.step', null, h('b', null, n), h('div.grow', null, h('div', null, text), ...extra));
  return h('div.setup', null,
    h('img.logo', { src: 'icons/icon-512.png', alt: '' }),
    h('h1', null, 'Fidelis'),
    h('div.tag', null, 'A faithful remote for your Spotify.'),
    message && h('div.card.msg', null, message),
    h('div.card', null,
      h('div.t2', { style: { marginBottom: '12px' } }, 'Fidelis signs in to Spotify through an app of your own. Making one is free and takes two minutes. Already made one for Fidelis on Android? Use the same one and add this second Redirect URI.'),
      step('1', 'On the Spotify dashboard, tap Create app. Any name and description will do.',
        h('a.btn.glass', { href: DASHBOARD, target: '_blank', rel: 'noopener' }, 'Open the Spotify dashboard')),
      step('2', 'Add this Redirect URI, and tick Web API:',
        h('div.copy', null, h('code', null, REDIRECT_URI), h('button.icbtn', { 'aria-label': 'Copy', onclick: () => copy(REDIRECT_URI) }, icon('copy')))),
      step('3', "Save, open the app's Settings and paste its Client ID here:", input, problem),
      go,
      h('div.t2', { style: { marginTop: '12px' } }, "If Spotify says your account isn't allowed, add it under User Management in the same app. Controlling playback needs Spotify Premium.")),
    h('button.link', { onclick: () => { Demo.on = true; App.start(); } }, 'Try the demo first'),
    !isStandalone() && isIOS() && h('div.t2.c', { style: { padding: '0 28px 30px' } }, 'Tip: tap Share, then Add to Home Screen, and sign in from there. It opens like an app.'));
}
