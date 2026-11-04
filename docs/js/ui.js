'use strict';
// Small helpers every screen uses, the five looks (ui/theme/Theme.kt), icons, sheets and the cover's colour.

/** h('div.card', { onclick }, kids…). Strings become text; null, false and undefined are skipped. */
function h(sel, props, ...kids) {
  const [tag, ...cls] = sel.split('.');
  const el = document.createElement(tag || 'div');
  if (cls.length) el.className = cls.join(' ');
  if (props != null && (typeof props !== 'object' || props instanceof Node || Array.isArray(props))) { kids.unshift(props); props = null; }
  if (props) for (const [k, v] of Object.entries(props)) {
    if (v == null || v === false) continue;
    if (k === 'style' && typeof v === 'object') { for (const [sk, sv] of Object.entries(v)) if (sv != null) { if (sk.startsWith('--')) el.style.setProperty(sk, sv); else el.style[sk] = typeof sv === 'number' && !/opacity|zIndex|flex/.test(sk) ? sv + 'px' : sv; } }
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className += (el.className ? ' ' : '') + v;
    else if (k === 'html') el.innerHTML = v;
    else if (k in el && !['list', 'type', 'form'].includes(k)) el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const k of kids.flat(Infinity)) if (k != null && k !== false && k !== true) el.append(k instanceof Node ? k : String(k));
  return el;
}

const SKINS = {
  night: { label: 'Night', top: '#0A0F26', mid: '#161D47', bottom: '#282C5E', glow1: 'rgba(143,214,255,.22)', glow2: 'rgba(180,156,255,.19)', glow3: 'rgba(228,101,127,.15)', start: '#8B5CC7', end: '#E4657F', hi: '#B49CFF', wash: 1 },
  ember: { label: 'Ember', top: '#0D0705', mid: '#26110C', bottom: '#451C12', glow1: 'rgba(255,159,74,.32)', glow2: 'rgba(255,90,95,.25)', glow3: 'rgba(255,210,122,.18)', start: '#FF7A3D', end: '#E0436B', hi: '#FFC08A', wash: .85 },
  graphite: { label: 'Graphite', top: '#0A0B0E', mid: '#16181E', bottom: '#252932', glow1: 'rgba(200,212,236,.2)', glow2: 'rgba(150,170,204,.15)', glow3: 'rgba(180,160,220,.12)', start: '#55648A', end: '#8B98B8', hi: '#DDE3EE', wash: 1 },
  ocean: { label: 'Ocean', top: '#020E1C', mid: '#062B4C', bottom: '#0A587A', glow1: 'rgba(90,210,255,.25)', glow2: 'rgba(80,120,255,.19)', glow3: 'rgba(255,210,150,.14)', start: '#3A7BFF', end: '#4FD0FF', hi: '#A6DCFF', wash: 1 },
  midnight: { label: 'Midnight', top: '#000000', mid: '#04050A', bottom: '#0A0C16', glow1: 'rgba(130,110,255,.19)', glow2: 'rgba(80,160,255,.12)', glow3: 'rgba(255,120,170,.1)', start: '#8B5CC7', end: '#E4657F', hi: '#B49CFF', wash: .45 },
};
const Look = {
  get name() { return store.get('skin') || 'night'; },
  get skin() { return SKINS[this.name] || SKINS.night; },
  get follow() { return store.get('follow') !== false; },
  apply(name = this.name) {
    store.set('skin', name);
    const s = SKINS[name], r = document.documentElement.style;
    for (const k of ['top', 'mid', 'bottom', 'glow1', 'glow2', 'glow3', 'start', 'end', 'hi']) r.setProperty('--' + k, s[k]);
    r.setProperty('--washAmount', String(s.wash));
    document.querySelector('meta[name=theme-color]').content = s.top;
  },
  /** The playing cover's colour, flowing into the sky (Profile → Follow the music). */
  tint(rgb) {
    const r = document.documentElement.style;
    if (rgb && this.follow) { r.setProperty('--wash', `rgb(${rgb.join(',')})`); document.body.classList.add('washed'); }
    else document.body.classList.remove('washed');
  },
};

/** One colour from a cover: the most colourful of its average tones, or null for grey covers. */
const Tint = {
  cache: {},
  of(url) {
    if (!url) return Promise.resolve(null);
    if (url in this.cache) return Promise.resolve(this.cache[url]);
    return new Promise(res => {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        try {
          const c = document.createElement('canvas'); c.width = c.height = 24;
          const g = c.getContext('2d', { willReadFrequently: true });
          g.drawImage(img, 0, 0, 24, 24);
          const px = g.getImageData(0, 0, 24, 24).data;
          let best = null, bestScore = -1;
          // 4×4 blocks: their average colours, scored by how colourful and how bright
          for (let by = 0; by < 6; by++) for (let bx = 0; bx < 6; bx++) {
            let r = 0, gg = 0, b = 0;
            for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) { const i = ((by * 4 + y) * 24 + bx * 4 + x) * 4; r += px[i]; gg += px[i + 1]; b += px[i + 2]; }
            r /= 16; gg /= 16; b /= 16;
            const mx = Math.max(r, gg, b), mn = Math.min(r, gg, b), sat = mx ? (mx - mn) / mx : 0, lum = mx / 255;
            const score = sat * (.35 + lum) * (lum > .12 ? 1 : .2);
            if (score > bestScore) { bestScore = score; best = [r, gg, b]; }
          }
          if (bestScore < .08) return res((this.cache[url] = null));
          // lifted towards a glow: the same hue, never muddy
          const mx = Math.max(...best), k = mx < 150 ? 150 / Math.max(mx, 1) : 1;
          res((this.cache[url] = best.map(v => Math.round(Math.min(255, v * k)))));
        } catch (e) { res((this.cache[url] = null)); } // a cover the browser won't let us read
      };
      img.onerror = () => res((this.cache[url] = null));
      img.src = url;
    });
  },
};

const I = (d, extra = '') => `<svg viewBox="0 0 24 24" aria-hidden="true"${extra}>${d}</svg>`;
const ICON = {
  playlists: I('<path d="M4 6.5h12M4 11.5h12M4 16.5h7" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/><path d="M15.5 17.2a2.3 2.3 0 1 0 0 .1zM17.8 17.2V9.5l3 .9" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>'),
  mood: I('<path d="M12 3.5l1.9 4.9 4.9 1.9-4.9 1.9L12 17.1l-1.9-4.9-4.9-1.9 4.9-1.9zM18.5 15.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>'),
  sparkle: I('<path d="M12 3.5l1.9 4.9 4.9 1.9-4.9 1.9L12 17.1l-1.9-4.9-4.9-1.9 4.9-1.9z" fill="currentColor"/><path d="M18.5 15.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z" fill="currentColor"/>'),
  radio: I('<rect x="3" y="8" width="18" height="12" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M7 8 16.5 3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="15.5" cy="14" r="2.6" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M6.5 12.5h3M6.5 15.5h3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  pin: I('<path d="M9 3.5h6l-1 5.5 3.5 3.5v1.5h-11V12.5L10 9zM12 14v6.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round"/>'),
  share: I('<path d="M12 3.5v11M8 7.5l4-4 4 4M6.5 11H6a1.5 1.5 0 0 0-1.5 1.5V19A1.5 1.5 0 0 0 6 20.5h12a1.5 1.5 0 0 0 1.5-1.5v-6.5A1.5 1.5 0 0 0 18 11h-.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'),
  image: I('<rect x="3.5" y="4.5" width="17" height="15" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="9" cy="9.5" r="1.7" fill="currentColor"/><path d="m4 17 5-4.5 4 3.5 3-2.5 4 3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>'),
  trash: I('<path d="M4.5 6.5h15M9.5 6.5V4.5h5v2M6.5 6.5l1 13h9l1-13M10 10v6M14 10v6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'),
  stop: I('<rect x="6.5" y="6.5" width="11" height="11" rx="2" fill="currentColor"/>'),
  play: I('<path d="M8 5.6v12.8c0 .8.9 1.3 1.6.9l10-6.4c.6-.4.6-1.4 0-1.8l-10-6.4C8.9 4.3 8 4.8 8 5.6z" fill="currentColor"/>'),
  pause: I('<rect x="6" y="5" width="4" height="14" rx="1.4" fill="currentColor"/><rect x="14" y="5" width="4" height="14" rx="1.4" fill="currentColor"/>'),
  next: I('<path d="M5 6.3v11.4c0 .8.9 1.3 1.5.8l8.2-5.7c.6-.4.6-1.2 0-1.6L6.5 5.5C5.9 5 5 5.5 5 6.3z" fill="currentColor"/><rect x="16.5" y="5" width="2.6" height="14" rx="1.2" fill="currentColor"/>'),
  prev: I('<path d="M19 6.3v11.4c0 .8-.9 1.3-1.5.8l-8.2-5.7c-.6-.4-.6-1.2 0-1.6l8.2-5.7c.6-.5 1.5 0 1.5.8z" fill="currentColor"/><rect x="4.9" y="5" width="2.6" height="14" rx="1.2" fill="currentColor"/>'),
  shuffle: I('<path d="M3 7h3.5c2 0 3.2 1 4.3 2.6l2.4 4.8c1 1.6 2.3 2.6 4.3 2.6H21M3 17h3.5c1.6 0 2.7-.6 3.6-1.7M21 7h-3.5c-1.6 0-2.7.6-3.6 1.7M18.5 4.5 21 7l-2.5 2.5M18.5 14.5 21 17l-2.5 2.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'),
  repeat: I('<path d="M17 3.5 20 6.5l-3 3M20 6.5H8a4 4 0 0 0-4 4V11M7 20.5 4 17.5l3-3M4 17.5h12a4 4 0 0 0 4-4V13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'),
  repeat1: I('<path d="M17 3.5 20 6.5l-3 3M20 6.5H8a4 4 0 0 0-4 4V11M7 20.5 4 17.5l3-3M4 17.5h12a4 4 0 0 0 4-4V13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M11.2 10.4 12.6 9.5v5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>'),
  heart: I('<path d="M12 20.3s-7.8-4.6-7.8-10.4A4.4 4.4 0 0 1 12 7.2a4.4 4.4 0 0 1 7.8 2.7c0 5.8-7.8 10.4-7.8 10.4z" fill="currentColor"/>'),
  heartLine: I('<path d="M12 20.3s-7.8-4.6-7.8-10.4A4.4 4.4 0 0 1 12 7.2a4.4 4.4 0 0 1 7.8 2.7c0 5.8-7.8 10.4-7.8 10.4z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>'),
  down: I('<path d="m6 9.5 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'),
  back: I('<path d="m14.5 6-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'),
  more: I('<circle cx="12" cy="5.5" r="1.7" fill="currentColor"/><circle cx="12" cy="12" r="1.7" fill="currentColor"/><circle cx="12" cy="18.5" r="1.7" fill="currentColor"/>'),
  lyrics: I('<path d="M5 5.5h14a1.5 1.5 0 0 1 1.5 1.5v8.5A1.5 1.5 0 0 1 19 17h-8l-4.3 3.3c-.3.2-.7 0-.7-.4V17H5a1.5 1.5 0 0 1-1.5-1.5V7A1.5 1.5 0 0 1 5 5.5z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M8 10h8M8 13h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  queue: I('<path d="M4 6.5h11M4 11.5h11M4 16.5h7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M15 15.5v-1a2.5 2.5 0 1 0 0 0zM17.5 14.5V7l3 1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'),
  devices: I('<rect x="3" y="5" width="12" height="9" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M6 18h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><rect x="16.5" y="8" width="5" height="11" rx="1.4" fill="none" stroke="currentColor" stroke-width="1.8"/>'),
  phone: I('<rect x="7" y="3" width="10" height="18" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M11 18h2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  computer: I('<rect x="4" y="5" width="16" height="11" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M2.5 19h19" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  speaker: I('<rect x="6" y="3" width="12" height="18" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="12" cy="14" r="3" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="12" cy="7.3" r="1.1" fill="currentColor"/>'),
  tv: I('<rect x="3" y="5" width="18" height="12" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M9 20h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  listen: I('<path d="M4 11.2 12 4.5l8 6.7V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>'),
  search: I('<circle cx="10.8" cy="10.8" r="6.3" fill="none" stroke="currentColor" stroke-width="1.9"/><path d="m15.5 15.5 4.5 4.5" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>'),
  profile: I('<circle cx="12" cy="8.5" r="3.8" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M4.5 20c1.3-3.6 4-5.3 7.5-5.3s6.2 1.7 7.5 5.3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  plus: I('<path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>'),
  check: I('<path d="m5 12.5 4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'),
  close: I('<path d="m6 6 12 12M18 6 6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>'),
  volume: I('<path d="M4 9.5v5h3.5L12 18.5v-13L7.5 9.5z" fill="currentColor"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  copy: I('<rect x="8.5" y="8.5" width="11" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M15.5 8.5V6a1.5 1.5 0 0 0-1.5-1.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5" fill="none" stroke="currentColor" stroke-width="1.8"/>'),
  open: I('<path d="M14 4.5h5.5V10M19.5 4.5 11 13M10 6H6a1.5 1.5 0 0 0-1.5 1.5V18A1.5 1.5 0 0 0 6 19.5h10.5A1.5 1.5 0 0 0 18 18v-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'),
};
const icon = (name, cls = '') => h('span.ic' + (cls ? '.' + cls : ''), { html: ICON[name] });
const deviceIcon = type => ({ Smartphone: 'phone', Computer: 'computer', Speaker: 'speaker', TV: 'tv', CastVideo: 'tv', CastAudio: 'speaker', AVR: 'speaker', STB: 'tv', Tablet: 'phone', Automobile: 'speaker' }[type] || 'devices');

/** Cover art: the image closest to [size] pixels, from Spotify's list. */
function art(images, size = 300) {
  if (!images || !images.length) return null;
  return [...images].sort((a, b) => Math.abs((a.width || 640) - size) - Math.abs((b.width || 640) - size))[0].url;
}
const coverEl = (images, size, cls = '') => {
  const url = art(images, size * 2);
  return h('div.cover' + (cls ? '.' + cls : ''), null, url && h('img', { src: url, alt: '', loading: 'lazy', decoding: 'async' }));
};
const names = artists => (artists || []).map(a => a.name).join(', ');
const mmss = ms => { const s = Math.max(0, Math.floor(ms / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
const total = ms => { const m = Math.round(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`; };
const songs = n => (n === 1 ? '1 song' : `${n} songs`);

/** A message at the bottom of the screen, with an optional action. */
function toast(msg, action, label) {
  const box = document.getElementById('toast');
  const t = h('div', null, h('span', null, msg), action && h('button', { onclick: () => { t.remove(); action(); } }, label));
  box.append(t);
  setTimeout(() => t.remove(), action ? 7000 : 3600);
}
/** Runs a Spotify call; a failure becomes a message instead of a crash. */
async function attempt(fn, quiet = false) {
  try { return await fn(); } catch (e) {
    if (!quiet) toast(e instanceof SpotifyError ? e.message : "Couldn't reach Spotify. Check your connection.");
    if (e instanceof SpotifyError && e.reason === 'SIGNED_OUT') App.render();
    return undefined;
  }
}

/** Overlay layers (the player, lyrics, sheets): each is rebuilt by its own refresh. */
const Layers = {
  list: [],
  open(key, build, { className = '', onClose } = {}) {
    this.close(key, true);
    const el = h('div', { class: className });
    const layer = { key, el, onClose, refresh: () => {
      const tops = [...el.querySelectorAll('.scroll')].map(s => s.scrollTop);
      el.replaceChildren(...[build(layer)].flat().filter(Boolean));
      [...el.querySelectorAll('.scroll')].forEach((s, i) => { if (tops[i]) s.scrollTop = tops[i]; });
    } };
    layer.refresh();
    document.getElementById('overlay').append(el);
    requestAnimationFrame(() => el.classList.add('in'));
    this.list.push(layer);
    return layer;
  },
  close(key, now = false) {
    const i = this.list.findIndex(l => l.key === key);
    if (i < 0) return;
    const [l] = this.list.splice(i, 1);
    if (now) l.el.remove();
    else { l.el.classList.remove('in'); setTimeout(() => l.el.remove(), 280); }
    if (l.onClose) l.onClose();
  },
  has(key) { return this.list.some(l => l.key === key); },
  get(key) { return this.list.find(l => l.key === key); },
};

/** A glass sheet from the bottom: tap outside or swipe it down to close. */
function sheet(key, build, opts = {}) {
  const layer = Layers.open(key, l => h('div.sheet', { onclick: e => e.stopPropagation() }, h('div.handle'), build(l)), { className: 'scrim', ...opts });
  layer.el.addEventListener('click', () => Layers.close(key));
  let y0 = null;
  layer.el.addEventListener('touchstart', e => { const s = layer.el.querySelector('.sheet'); if (s && s.scrollTop <= 0) y0 = e.touches[0].clientY; }, { passive: true });
  layer.el.addEventListener('touchmove', e => { if (y0 == null) return; const d = e.touches[0].clientY - y0; const s = layer.el.querySelector('.sheet'); if (s && d > 0) s.style.transform = `translateY(${d}px)`; }, { passive: true });
  layer.el.addEventListener('touchend', e => {
    if (y0 == null) return;
    const d = e.changedTouches[0].clientY - y0; y0 = null;
    const s = layer.el.querySelector('.sheet');
    if (d > 110) Layers.close(key); else if (s) s.style.transform = '';
  });
  return layer;
}

/** "⋮" on any song: play it, queue it, like it, or go to its artist or album. */
/**
 * "⋮" on any song: play it, queue it, like it, add it to a playlist, share it, start a radio from it, or go to its
 * artist or album. [extra] adds rows of its own first, as [label, icon, action].
 */
function trackMenu(t, { list, index, context, extra = [] } = {}) {
  const uri = t.uri, song = uri && uri.startsWith('spotify:track:');
  const go = fn => () => { Layers.close('menu'); fn(); };
  sheet('menu', () => [
    h('div.row.mhead', null, coverEl(t.album ? t.album.images : t.images, 52, 'sq'),
      h('div.grow', null, h('div.t1', null, t.name), h('div.t2', null, names(t.artists)))),
    extra.map(([label, ic, fn]) => h('button.mi', { onclick: go(fn) }, icon(ic), label)),
    !extra.length && h('button.mi', { onclick: go(() => Player.playTrack(t, { list, index, context })) }, icon('play'), 'Play now'),
    h('button.mi', { onclick: go(async () => { if (await attempt(() => S.enqueue(uri)) !== undefined) { toast('Added to the queue.'); Player.refreshSoon(); } }) }, icon('queue'), 'Add to queue'),
    song && h('button.mi', { onclick: go(async () => { const [on] = await attempt(() => S.saved([uri]), true) || [false]; if (await attempt(() => (on ? S.unsave([uri]) : S.save([uri]))) === undefined) return; toast(on ? 'Removed from Liked Songs.' : 'Added to Liked Songs.'); Player.likedNow(uri, !on); }) }, icon('heartLine'), 'Like or unlike'),
    song && h('button.mi', { onclick: go(() => addToPlaylist(t)) }, icon('plus'), 'Add to playlist'),
    h('button.mi', { onclick: go(() => shareSong(t)) }, icon('share'), 'Share'),
    song && h('button.mi', { onclick: go(() => songRadio(t)) }, icon('radio'), 'Start song radio'),
    t.artists && t.artists[0] && t.artists[0].id && h('button.mi', { onclick: go(() => { Layers.close('player'); Nav.push('artist', t.artists[0].id); }) }, icon('profile'), `Go to ${t.artists[0].name}`),
    t.album && t.album.id && h('button.mi', { onclick: go(() => { Layers.close('player'); Nav.push('album', t.album.id); }) }, icon('listen'), 'Go to the album'),
    h('a.mi', { href: spotifyLink(uri), target: '_blank', rel: 'noopener', onclick: () => Layers.close('menu') }, icon('open'), 'Open in Spotify'),
  ]);
}

/** A song in a list: cover (or number), title, artist, length and ⋮. */
function songRow(t, { index, list, context, number, showCover = true } = {}) {
  if (!t) return null;
  const playing = Player.item && Player.item.uri === t.uri;
  return h('div.song' + (playing ? '.on' : ''), { onclick: () => Player.playTrack(t, { list, index, context }) },
    number != null ? h('span.num', null, playing ? icon('volume') : String(number)) : showCover && coverEl(t.album && t.album.images, 46, 'sq'),
    h('div.grow', null, h('div.t1', null, t.name), h('div.t2', null, t.explicit && h('b.e', null, 'E'), names(t.artists))),
    t.duration_ms > 0 && h('span.dur', null, mmss(t.duration_ms)),
    h('button.icbtn', { 'aria-label': 'More', onclick: e => { e.stopPropagation(); trackMenu(t, { list, index, context }); } }, icon('more')));
}

/** A square tile for an album, playlist or song in a row that scrolls sideways. */
function tile(images, title, sub, onclick, round = false) {
  return h('button.tile' + (round ? '.round' : ''), { onclick }, coverEl(images, 140, round ? 'circle' : 'sq'),
    h('div.t1', null, title), sub && h('div.t2', null, sub));
}
const shelf = (title, kids, extra) => kids.length ? h('section', null, h('div.shead', null, h('h2', null, title), extra), h('div.shelf', null, kids)) : null;

const isStandalone = () => window.navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;
const isIOS = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

// Anything that breaks shows itself at the top, so it can be reported instead of failing quietly.
window.addEventListener('error', e => showError(e.message + (e.filename ? ` (${e.filename.split('/').pop().split('?')[0]}:${e.lineno})` : '')));
window.addEventListener('unhandledrejection', e => { if (!(e.reason instanceof SpotifyError)) showError(String(e.reason && (e.reason.stack || e.reason.message) || e.reason)); });
function showError(msg) {
  let el = document.getElementById('err');
  if (!el) { el = h('div', { id: 'err', onclick: () => el.remove() }); document.body.append(el); }
  el.textContent = `Something went wrong: ${msg}\nTap to hide.`;
}
