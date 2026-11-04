'use strict';
// What's playing, and the screens that show it: the mini player, the full player (ui/player/), lyrics and Playing on.

const Player = {
  st: null, at: 0, item: null, liked: {}, queue: null, tint: null, timer: null,
  hold: 0, // just seeked: keep the bar where it was put until Spotify catches up
  lyricsOffset: store.get('lyricsOffset') || 0,

  get playing() { return !!(this.st && this.st.is_playing); },
  get device() { return this.st && this.st.device; },
  get duration() { return this.item ? this.item.duration_ms : 0; },
  position() {
    if (!this.st) return 0;
    const p = this.st.progress_ms + (this.st.is_playing ? Date.now() - this.at : 0);
    return Math.min(Math.max(0, p), this.duration || p);
  },

  async refresh() {
    const asked = Date.now();
    const s = await attempt(() => S.player(), true);
    // an answer to a question asked before the last tap describes the old state: wait for the next one
    if (s !== undefined && asked >= this.tapped) this.set(s);
  },
  tapped: 0,
  set(s) {
    const before = this.item ? this.item.uri : null, was = this.signature();
    if (Date.now() < this.hold && s && this.st) s.progress_ms = this.position();
    this.st = s; this.at = Date.now();
    this.item = s && s.item ? s.item : null;
    const now = this.item ? this.item.uri : null;
    if (now !== before) this.changed();
    if (this.signature() !== was || now !== before) this.redraw();
    this.schedule();
  },
  /** The parts of the state that change what's drawn; the position is drawn by the ticker. */
  signature() {
    const s = this.st;
    return s ? [s.is_playing, s.shuffle_state, s.repeat_state, s.device && s.device.id, s.device && s.device.volume_percent, this.item && this.item.uri, this.item && this.liked[this.item.uri]].join('|') : '';
  },
  async changed() {
    const t = this.item;
    this.queue = null;
    if (!t) { Look.tint(null); return; }
    const url = art(t.album ? t.album.images : t.images, 300);
    Tint.of(url).then(rgb => { if (this.item === t) { this.tint = rgb; Look.tint(rgb); this.redraw(); } });
    if (t.type === 'track' && !(t.uri in this.liked)) {
      attempt(() => S.saved([t.uri]), true).then(r => { if (r && this.item === t) { this.liked[t.uri] = !!r[0]; this.redraw(); } });
    }
    if (Layers.has('player')) this.loadQueue();
    if (Layers.has('lyrics')) openLyrics();
  },
  async loadQueue() {
    const q = await attempt(() => S.queue(), true);
    if (q) { this.queue = (q.queue || []).filter(Boolean); this.redraw(); }
  },
  likedNow(uri, on) { this.liked[uri] = on; this.redraw(); },
  redraw() {
    renderMini();
    const p = Layers.get('player'); if (p) p.refresh();
  },

  /** Spotify is asked again every few seconds while Fidelis is on screen; not at all when it's hidden. */
  schedule(ms) {
    clearTimeout(this.timer);
    if (document.hidden || !Auth.signedIn && !Demo.on) return;
    this.timer = setTimeout(() => this.refresh(), ms ?? (this.playing ? 3000 : 7000));
  },
  refreshSoon() { this.schedule(350); setTimeout(() => this.refresh(), 1600); },

  // ---- commands: the screen changes at once, then Spotify confirms ----
  async run(fn, change) {
    this.tapped = Date.now();
    if (change && this.st) { change(this.st); this.at = Date.now(); this.redraw(); }
    const r = await attempt(fn);
    this.tapped = Date.now();
    if (r === undefined && change) this.refresh(); else this.refreshSoon();
  },
  toggle() {
    if (!this.st) return this.start();
    const on = this.playing;
    this.st.progress_ms = this.position();
    this.run(() => (on ? S.pause() : S.play()), s => { s.is_playing = !on; });
  },
  next() { this.run(() => S.next(), s => { s.progress_ms = 0; }); },
  prev() { this.run(() => S.prev(), s => { s.progress_ms = 0; }); },
  seek(ms) { this.hold = Date.now() + 1500; this.run(() => S.seek(ms), s => { s.progress_ms = ms; }); },
  shuffle() { const on = !(this.st && this.st.shuffle_state); this.run(() => S.shuffle(on), s => { s.shuffle_state = on; }); },
  repeat() {
    const next = { off: 'context', context: 'track', track: 'off' }[(this.st && this.st.repeat_state) || 'off'];
    this.run(() => S.repeat(next), s => { s.repeat_state = next; });
  },
  volume(v) { this.run(() => S.volume(v), s => { if (s.device) s.device.volume_percent = v; }); },
  async like() {
    const t = this.item; if (!t) return;
    const on = !this.liked[t.uri];
    this.likedNow(t.uri, on);
    if (await attempt(() => (on ? S.save([t.uri]) : S.unsave([t.uri]))) === undefined) this.likedNow(t.uri, !on);
  },

  /** Plays a song: inside its album or playlist when there is one, else with the list it was in. */
  async playTrack(t, { list, index, context } = {}) {
    let what;
    if (context) what = { context_uri: context, offset: { uri: t.uri } };
    else if (list && list.length > 1) {
      const i = Math.max(0, index ?? list.findIndex(x => x && x.uri === t.uri));
      const from = Math.max(0, i - 20), uris = list.slice(from, from + 50).filter(Boolean).map(x => x.uri);
      what = { uris, offset: { position: i - from } };
    } else what = { uris: [t.uri] };
    await this.play(what);
  },
  playContext(uri, shuffle = false) {
    return this.play({ context_uri: uri }, shuffle);
  },
  async play(what, shuffle = false) {
    this.tapped = Date.now();
    try {
      if (shuffle) await S.shuffle(true).catch(() => {});
      await S.play(what);
    } catch (e) {
      if (!(e instanceof SpotifyError)) return toast("Couldn't reach Spotify.");
      if (e.status !== 404) return toast(e.message);
      // nothing active: start it on a device Spotify can see, or ask which one
      const devs = await attempt(() => S.devices(), true) || [];
      const d = devs.find(x => !x.is_restricted && x.type === 'Smartphone') || devs.find(x => !x.is_restricted);
      if (!d) { openDevices(); return; }
      if (await attempt(() => S.play(what, d.id)) === undefined) return;
    }
    this.refreshSoon();
  },
  /** The play button with nothing loaded: carry on with the last song played. */
  async start() {
    const r = await attempt(() => S.recent(), true);
    const t = r && r.items && r.items[0] && r.items[0].track;
    if (t) this.playTrack(t, { context: t.album && t.album.uri }); else openDevices();
  },
};

// ---- the ticker: the progress bars, times and lyrics follow the song between Spotify's answers ----
setInterval(() => {
  if (!Player.item) return;
  const p = Player.position(), d = Player.duration || 1;
  document.querySelectorAll('[data-prog]').forEach(el => { el.style.transform = `scaleX(${Math.min(1, p / d)})`; });
  document.querySelectorAll('[data-now]').forEach(el => { if (!el.closest('.scrubbing')) el.textContent = mmss(p); });
  document.querySelectorAll('input[data-seek]').forEach(el => { if (!el.closest('.scrubbing')) { el.value = String(Math.round(p / d * 1000)); el.style.setProperty('--p', (p / d * 100) + '%'); } });
  Lyric.tick(p);
}, 250);

// ---- the mini player above the tabs: tap to open, swipe for the next or previous song ----
function renderMini() {
  const box = document.getElementById('mini');
  const t = Player.item;
  document.body.classList.toggle('hasMini', !!t);
  if (!t) { box.replaceChildren(); return; }
  const imgs = t.album ? t.album.images : t.images;
  const card = h('div.mini', { style: { '--tint': Player.tint ? `rgb(${Player.tint})` : 'var(--start)' } },
    coverEl(imgs, 46, 'sq'),
    h('div.grow', null, h('div.t1', null, t.name), h('div.t2', null, Player.device ? `${names(t.artists || [t.show])} · ${Player.device.name}` : names(t.artists))),
    h('button.playbtn.small', { 'aria-label': Player.playing ? 'Pause' : 'Play', onclick: e => { e.stopPropagation(); Player.toggle(); } }, icon(Player.playing ? 'pause' : 'play')),
    h('div.line', null, h('i', { 'data-prog': '' })));
  swipeable(card, () => Player.next(), () => Player.prev(), () => openPlayer());
  box.replaceChildren(card);
}

/** Horizontal swipes on [el]: left for [onLeft], right for [onRight]; a plain tap for [onTap]. */
function swipeable(el, onLeft, onRight, onTap) {
  let x0 = null, y0 = 0, dx = 0, moved = false;
  el.addEventListener('pointerdown', e => { if (e.target.closest('button')) return; x0 = e.clientX; y0 = e.clientY; dx = 0; moved = false; });
  el.addEventListener('pointermove', e => {
    if (x0 == null) return;
    dx = e.clientX - x0;
    if (!moved && Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(e.clientY - y0)) { moved = true; try { el.setPointerCapture(e.pointerId); } catch (err) { /* already gone */ } }
    if (moved) { el.style.transform = `translateX(${dx}px)`; el.style.opacity = String(1 - Math.min(.6, Math.abs(dx) / 300)); }
  });
  const end = () => {
    if (x0 == null) return;
    x0 = null;
    el.style.transition = 'transform .25s, opacity .25s';
    if (moved && Math.abs(dx) > 70) {
      el.style.transform = `translateX(${dx < 0 ? -120 : 120}px)`; el.style.opacity = '0';
      (dx < 0 ? onLeft : onRight)();
      setTimeout(() => { el.style.transition = 'none'; el.style.transform = `translateX(${dx < 0 ? 60 : -60}px)`; requestAnimationFrame(() => { el.style.transition = 'transform .3s, opacity .3s'; el.style.transform = ''; el.style.opacity = ''; }); }, 200);
    } else { el.style.transform = ''; el.style.opacity = ''; if (!moved && onTap) onTap(); }
    setTimeout(() => { el.style.transition = ''; }, 500);
  };
  el.addEventListener('pointerup', end);
  el.addEventListener('pointercancel', () => { x0 = null; el.style.transform = ''; el.style.opacity = ''; });
}

// ---- the full player ----
function openPlayer() {
  if (!Player.item) return;
  Player.loadQueue();
  const layer = Layers.open('player', () => playerView(), { className: 'player' });
  // swipe down on the cover area to close, like the Android player
  let y0 = null;
  layer.el.addEventListener('touchstart', e => { const sc = layer.el.querySelector('.scroll'); if (sc && sc.scrollTop <= 0 && !e.target.closest('input')) y0 = e.touches[0].clientY; }, { passive: true });
  layer.el.addEventListener('touchmove', e => { if (y0 == null) return; const d = e.touches[0].clientY - y0; if (d > 0) layer.el.style.transform = `translateY(${d * .8}px)`; }, { passive: true });
  layer.el.addEventListener('touchend', e => {
    if (y0 == null) return;
    const d = e.changedTouches[0].clientY - y0; y0 = null;
    if (d > 140) Layers.close('player'); else layer.el.style.transform = '';
  });
}

function playerView() {
  const t = Player.item;
  if (!t) { setTimeout(() => Layers.close('player'), 0); return h('div'); }
  const st = Player.st || {}, dev = Player.device;
  const imgs = t.album ? t.album.images : t.images;
  const ctx = st.context, from = !ctx ? null : ctx.type === 'album' ? 'ALBUM' : ctx.type === 'playlist' ? 'PLAYLIST' : ctx.type === 'artist' ? 'ARTIST' : null;
  const liked = !!Player.liked[t.uri];
  const repeatOn = st.repeat_state && st.repeat_state !== 'off';
  const tint = Player.tint ? `rgb(${Player.tint})` : 'var(--start)';

  const coverBox = h('div.big', null, coverEl(imgs, 640, 'sq'));
  swipeable(coverBox, () => Player.next(), () => Player.prev(), () => coverBox.classList.toggle('full'));

  const seek = h('input.seek', { type: 'range', min: 0, max: 1000, value: 0, 'data-seek': '', 'aria-label': 'Position' });
  const timeNow = h('span', { 'data-now': '' }, mmss(Player.position()));
  const bar = h('div.scrub', null, seek, h('div.times', null, timeNow, h('span', null, mmss(Player.duration))));
  seek.addEventListener('input', () => { bar.classList.add('scrubbing'); const ms = seek.value / 1000 * Player.duration; timeNow.textContent = mmss(ms); seek.style.setProperty('--p', seek.value / 10 + '%'); });
  seek.addEventListener('change', () => { bar.classList.remove('scrubbing'); Player.seek(seek.value / 1000 * Player.duration); });

  const vol = dev && dev.supports_volume !== false && dev.volume_percent != null && h('div.vol', null, icon('volume'),
    h('input', { type: 'range', min: 0, max: 100, value: dev.volume_percent, style: { '--p': dev.volume_percent + '%' },
      oninput: e => e.target.style.setProperty('--p', e.target.value + '%'), onchange: e => Player.volume(+e.target.value) }));

  const q = Player.queue;
  return h('div.scroll', { style: { '--tint': tint } },
    h('div.hero', null,
      h('div.ptop', null,
        h('button.icbtn', { 'aria-label': 'Close', onclick: () => Layers.close('player') }, icon('down')),
        h('div.grow.from', null, from && h('div.k', null, `PLAYING FROM ${from}`), h('div.fname', null, from === 'ALBUM' && t.album ? t.album.name : from ? (Nav.contextName[ctx.uri] || '') : 'Spotify')),
        h('button.icbtn', { 'aria-label': 'More', onclick: () => t.type === 'track' && trackMenu(t) }, icon('more'))),
      coverBox,
      h('div.ttl', null,
        h('div.grow', null,
          h('div.name', null, t.name),
          h('button.artist', { onclick: () => { const a = t.artists && t.artists[0]; if (a && a.id) { Layers.close('player'); Nav.push('artist', a.id); } } }, names(t.artists) || (t.show && t.show.name) || '')),
        t.type === 'track' && h('button.icbtn.heart' + (liked ? '.on' : ''), { 'aria-label': liked ? 'Remove from Liked Songs' : 'Save to Liked Songs', onclick: () => Player.like() }, icon(liked ? 'heart' : 'heartLine'))),
      bar,
      h('div.controls', null,
        h('button.icbtn.mode' + (st.shuffle_state ? '.on' : ''), { 'aria-label': 'Shuffle', onclick: () => Player.shuffle() }, icon('shuffle')),
        h('button.icbtn.skip', { 'aria-label': 'Previous', onclick: () => Player.prev() }, icon('prev')),
        h('button.playbtn', { 'aria-label': Player.playing ? 'Pause' : 'Play', onclick: () => Player.toggle() }, icon(Player.playing ? 'pause' : 'play')),
        h('button.icbtn.skip', { 'aria-label': 'Next', onclick: () => Player.next() }, icon('next')),
        h('button.icbtn.mode' + (repeatOn ? '.on' : ''), { 'aria-label': 'Repeat', onclick: () => Player.repeat() }, icon(st.repeat_state === 'track' ? 'repeat1' : 'repeat'))),
      vol,
      h('div.tools', null,
        h('button.pill.dev', { onclick: () => openDevices() }, icon(dev ? deviceIcon(dev.type) : 'devices'), h('span', null, dev ? dev.name : 'Choose a device')),
        h('div.grow'),
        t.type === 'track' && h('button.icbtn.glass', { 'aria-label': 'Lyrics', onclick: () => openLyrics() }, icon('lyrics')),
        h('button.icbtn.glass', { 'aria-label': 'Up next', onclick: e => e.target.closest('.scroll').querySelector('.upnext').scrollIntoView({ behavior: 'smooth' }) }, icon('queue')))),
    h('div.upnext', null,
      h('div.k', null, q && q.length ? `UP NEXT · ${q.length}${q.some(x => x.duration_ms) ? ' · ' + total(q.reduce((s, x) => s + (x.duration_ms || 0), 0)).toUpperCase() : ''}` : 'UP NEXT'),
      q == null ? h('div.note', null, 'Loading…')
        : !q.length ? h('div.note', null, st.shuffle_state ? 'Spotify picks what plays next.' : 'Nothing queued. Use ⋮ › Add to queue on any song.')
          : q.slice(0, 20).map(x => songRow(x, {})),
      h('div.note', null, 'Spotify only lets apps add to the queue, not reorder or remove from it.')));
}

// ---- lyrics ----
const Lyric = {
  lines: null, cur: -1, el: null, auto: true, lastTouch: 0,
  tick(p) {
    if (!this.lines || !this.el) return;
    const t = p + this.offset() + 120; // a touch early, so the glow lands on the word
    let i = -1;
    for (let k = 0; k < this.lines.length; k++) { if (this.lines[k].t <= t) i = k; else break; }
    if (i === this.cur) return;
    this.cur = i;
    const rows = this.el.querySelectorAll('.lline');
    rows.forEach((r, k) => { r.classList.toggle('on', k === i); r.classList.toggle('past', k < i); });
    if (i >= 0 && Date.now() - this.lastTouch > 3000) {
      const row = rows[i], box = this.el;
      box.scrollTo({ top: row.offsetTop - box.clientHeight * .32, behavior: 'smooth' });
    }
  },
  offset() { return Player.lyricsOffset; },
};

async function openLyrics() {
  const t = Player.item;
  if (!t || t.type !== 'track') return;
  const state = { found: null };
  Lyric.lines = null; Lyric.cur = -1;
  const layer = Layers.open('lyrics', () => lyricsView(t, state), { className: 'lyrics', onClose: () => { Lyric.el = null; Lyric.lines = null; } });
  state.found = await Lyrics.find(t);
  if (Player.item !== t || !Layers.has('lyrics')) return;
  if (state.found.kind === 'synced') Lyric.lines = state.found.lines;
  layer.refresh();
}

function lyricsView(t, state) {
  const f = state.found;
  const nudge = d => { Player.lyricsOffset = d === 0 ? 0 : Player.lyricsOffset + d; store.set('lyricsOffset', Player.lyricsOffset); Lyric.cur = -1; Layers.get('lyrics').refresh(); };
  let body;
  if (!f) body = h('div.lnote', null, 'Finding lyrics…');
  else if (f.kind === 'none') body = h('div.lnote', null, 'No lyrics for this song yet.');
  else if (f.kind === 'instrumental') body = h('div.lnote', null, '♪  Instrumental');
  else if (f.kind === 'plain') body = h('div.lscroll', null, h('div.k', { style: { marginBottom: '16px' } }, 'NOT SYNCED TO THE SONG'), f.lines.map(l => h('div.lplain', null, l || ' ')));
  else {
    body = h('div.lscroll.synced', null, f.lines.map(l => h('button.lline', { onclick: () => { Lyric.lastTouch = 0; Player.seek(Math.max(0, l.t - Player.lyricsOffset)); } }, l.text || '♪')));
    ['touchstart', 'wheel'].forEach(ev => body.addEventListener(ev, () => { Lyric.lastTouch = Date.now(); }, { passive: true }));
    Lyric.el = body;
    requestAnimationFrame(() => Lyric.tick(Player.position()));
  }
  const off = Player.lyricsOffset;
  return [
    h('div.ltop', null,
      h('button.icbtn.glass', { 'aria-label': 'Close lyrics', onclick: () => Layers.close('lyrics') }, icon('close')),
      h('div.grow', null, h('div.t1', null, t.name), h('div.t2', null, names(t.artists))),
      coverEl(t.album && t.album.images, 44, 'sq')),
    body,
    h('div.lfoot', null,
      h('div.grow.k', null, f && f.kind !== 'none' ? 'LYRICS FROM LRCLIB' : ''),
      f && f.kind === 'synced' && [
        h('button.nudge', { 'aria-label': 'Show lyrics later', onclick: () => nudge(-250) }, '−'),
        h('button.offs', { onclick: () => nudge(0) }, off > 0 ? `${(off / 1000).toFixed(2)}s early` : off < 0 ? `${(-off / 1000).toFixed(2)}s late` : 'In time'),
        h('button.nudge', { 'aria-label': 'Show lyrics earlier', onclick: () => nudge(250) }, '+'),
      ]),
  ];
}

// ---- Playing on: the devices Spotify can play on, and moving playback between them ----
async function openDevices() {
  const state = { list: null };
  const layer = sheet('devices', () => devicesView(state));
  const load = async () => { state.list = await attempt(() => S.devices()) || []; if (Layers.has('devices')) layer.refresh(); };
  state.reload = load;
  load();
}

function devicesView(state) {
  const cur = Player.device;
  const list = state.list;
  return [
    h('div.shead', null, h('h2', null, 'Playing on'), h('button.chip', { onclick: () => { state.list = null; Layers.get('devices').refresh(); state.reload(); } }, 'Refresh')),
    list == null ? h('div.note', null, 'Looking for your devices…')
      : !list.length ? h('div', null,
        h('div.note', null, "Spotify only lists devices where it's open. Open the Spotify app on this iPhone (or on a computer or speaker) for a moment, then come back and tap Refresh."),
        h('a.btn', { href: 'spotify:', onclick: () => setTimeout(() => state.reload(), 4000) }, 'Open Spotify'))
        : list.map(d => {
          const on = d.is_active || (cur && cur.id === d.id);
          return h('button.devrow' + (on ? '.on' : ''), {
            disabled: d.is_restricted,
            onclick: async () => {
              if (on) return Layers.close('devices');
              Layers.close('devices');
              Player.tapped = Date.now();
              if (await attempt(() => S.transfer(d.id, true)) !== undefined) { Player.tapped = Date.now(); toast(`Playing on ${d.name}.`); Player.refreshSoon(); }
            },
          }, icon(deviceIcon(d.type)),
          h('div.grow', null, h('div.t1', null, d.name), h('div.t2', null, on ? 'Playing here now' : d.is_restricted ? "Spotify doesn't allow controlling this one" : d.type)),
          on && icon('check'));
        }),
    h('div.note', null, 'Fidelis is a remote: the music plays in Spotify, on whichever device you pick here.'),
  ];
}
