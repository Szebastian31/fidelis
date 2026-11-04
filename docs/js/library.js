'use strict';
// The song sheet's extras (ui/theme/TrackSheet.kt, ui/playlists/AddToPlaylist.kt, ui/theme/Share.kt): add to one of
// your playlists, share a story card, and a radio built from a song.

/** A sign-in from before a feature: Spotify needs asking once more to allow it. */
function reconnectFor(what) {
  confirmBox('Allow this once more', `Fidelis needs a fresh Spotify sign-in to ${what}. You'll come straight back.`, 'Sign in again', () => Auth.login());
}

/** A small glass dialog with Cancel and one action. */
function confirmBox(title, body, action, onYes) {
  const l = Layers.open('confirm', () => h('div.dialog', { onclick: e => e.stopPropagation() },
    h('h2', null, title), h('div.t2', null, body),
    h('div.acts', null,
      h('button.btn.glass', { onclick: () => Layers.close('confirm') }, 'Cancel'),
      h('button.btn.grad', { onclick: () => { Layers.close('confirm'); onYes(); } }, action))), { className: 'scrim center' });
  l.el.addEventListener('click', () => Layers.close('confirm'));
}

/** The playlists a song can go into: the ones you made or collaborate on. Tap one to add it. */
function addToPlaylist(t) {
  if (!Auth.allows('playlist-modify-private') && !Auth.allows('playlist-modify-public')) return reconnectFor('add songs to your playlists');
  const st = { busy: null };
  sheet('add', () => {
    const pls = Data.get('playlists', () => S.playlists());
    const me = Data.v.me;
    const mine = ((pls && pls.items) || []).filter(p => p && (Demo.on || p.collaborative || (me && p.owner && p.owner.id === me.id)));
    return [
      h('div.row.mhead', null, coverEl(t.album && t.album.images, 52, 'sq'), h('div.grow', null, h('div.t1', null, t.name), h('div.t2', null, names(t.artists)))),
      h('div.k', { style: { padding: '6px 6px 8px' } }, 'ADD TO PLAYLIST'),
      pls === undefined ? h('div.note', null, 'Loading your playlists…')
        : !mine.length ? h('div.note', null, 'No playlists to add to. Spotify only lets apps add songs to playlists you made or collaborate on.')
          : mine.map(p => h('button.mi.pl', { disabled: !!st.busy, onclick: async () => {
            st.busy = p.id; Layers.get('add').refresh();
            const ok = await attempt(() => S.addToPlaylist(p.id, [t.uri]));
            st.busy = null;
            if (ok !== undefined) { Layers.close('add'); toast(`Added to ${p.name}.`); Data.drop('playlistItems:' + p.id); Data.drop('playlist:' + p.id); }
            else if (Layers.has('add')) Layers.get('add').refresh();
          } }, coverEl(p.images, 44, 'sq'), h('div.grow', null, h('div.t1', null, p.name), h('div.t2', null, st.busy === p.id ? 'Adding…' : songs((p.items || p.tracks || {}).total || 0))))),
    ];
  });
}

/** open.spotify.com address for any Spotify uri, so every app shows the song's own preview. */
function spotifyLink(uri) {
  const p = (uri || '').split(':');
  return p.length >= 3 && p[0] === 'spotify' ? `https://open.spotify.com/${p[p.length - 2]}/${p[p.length - 1]}` : uri;
}

/** Spotify-style sharing: a card in the cover's colour, then the share sheet with the card, or just the link. */
function shareSong(t) {
  const title = t.name, artist = names(t.artists || []) || (t.show && t.show.name) || '';
  const link = spotifyLink(t.uri), url = art(t.album ? t.album.images : t.images, 640);
  const st = { color: [139, 92, 199] };
  Tint.of(url).then(rgb => { if (rgb) { st.color = rgb; const l = Layers.get('share'); if (l) l.refresh(); } });
  sheet('share', () => {
    const c = `rgb(${st.color})`;
    return [
      h('div.k.c', { style: { padding: '2px 0 10px' } }, 'SHARE'),
      h('div.scard', { style: { '--c': c } },
        h('div.cover.sq', null, url && h('img', { src: url, alt: '' })),
        h('div.fname.c', null, title), artist && h('div.t2.c', null, artist), h('div.k.c', { style: { marginTop: '12px' } }, 'FIDELIS')),
      h('div.targets', null,
        h('button.target', { onclick: () => { copy(link); Layers.close('share'); } }, h('span', null, icon('copy')), 'Copy link'),
        h('button.target', { onclick: async () => {
          const file = await storyImage(url, title, artist, st.color).catch(() => null);
          Layers.close('share');
          const data = { title, text: artist ? `${title} · ${artist}` : title, url: link };
          if (file && navigator.canShare && navigator.canShare({ files: [file] })) {
            try { await navigator.share({ files: [file], text: `${data.text}\n${link}` }); } catch (e) { /* closed */ }
          } else if (navigator.share) { try { await navigator.share(data); } catch (e) { /* closed */ } }
          else { copy(link); }
        } }, h('span', null, icon('image')), 'Story card'),
        h('button.target', { onclick: async () => { Layers.close('share'); if (navigator.share) { try { await navigator.share({ title, text: artist ? `${title} · ${artist}` : title, url: link }); } catch (e) { /* closed */ } } else copy(link); } },
          h('span', null, icon('more')), 'More')),
    ];
  });
}

/** A 1080×1920 story card: the cover on its own colour, title, artist and a quiet Fidelis mark. */
async function storyImage(url, title, artist, rgb) {
  const W = 1080, H = 1920, c = document.createElement('canvas');
  c.width = W; c.height = H;
  const g = c.getContext('2d');
  const top = rgb.map(v => Math.round(v + (255 - v) * .08)), bottom = rgb.map(v => Math.round(v * .4));
  const bg = g.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, `rgb(${top})`); bg.addColorStop(1, `rgb(${bottom})`);
  g.fillStyle = bg; g.fillRect(0, 0, W, H);
  const size = 780, left = (W - size) / 2, y = 400;
  g.save(); g.shadowColor = 'rgba(0,0,0,.6)'; g.shadowBlur = 70; g.shadowOffsetY = 34;
  g.fillStyle = 'rgba(0,0,0,.2)'; g.beginPath(); g.roundRect(left, y, size, size, 44); g.fill(); g.restore();
  if (url) {
    const img = await new Promise(res => { const i = new Image(); i.crossOrigin = 'anonymous'; i.onload = () => res(i); i.onerror = () => res(null); i.src = url; });
    if (img) { g.save(); g.beginPath(); g.roundRect(left, y, size, size, 44); g.clip(); g.drawImage(img, left, y, size, size); g.restore(); }
  }
  await document.fonts.ready.catch(() => {});
  const line = (text, px, weight, alpha, at) => {
    if (!text) return;
    g.font = `${weight} ${px}px Manrope, -apple-system, sans-serif`; g.textAlign = 'center'; g.fillStyle = `rgba(255,255,255,${alpha})`;
    let s = text;
    while (g.measureText(s).width > W - 160 && s.length > 1) s = s.slice(0, -2) + '…';
    g.fillText(s, W / 2, at);
  };
  line(title, 68, 800, 1, y + size + 150);
  line(artist, 46, 600, .85, y + size + 222);
  line('Listening on Fidelis', 34, 600, .6, H - 170);
  const blob = await new Promise(res => c.toBlob(res, 'image/png'));
  return new File([blob], 'fidelis-story.png', { type: 'image/png' });
}

/**
 * A radio from a song: the song first, then songs in its artist's genres and more by the artist, shuffled.
 * (Fidelis for Android asks Last.fm for similar songs; Spotify's own recommendations are closed to new apps.)
 */
async function songRadio(t) {
  const a = t.artists && t.artists[0];
  if (!a) return;
  toast(`Building a radio from “${t.name}”…`);
  try {
    const artist = a.id ? await S.artist(a.id).catch(() => null) : null;
    const genres = ((artist && artist.genres) || []).slice(0, 3);
    const pages = await Promise.all([
      S.search(`artist:"${a.name}"`, 'track', 10).catch(() => null),
      ...genres.flatMap(g => [0, 10].map(o => S.search(`genre:"${g}"`, 'track', 10, o).catch(() => null))),
    ]);
    const seen = new Set([t.uri]), perArtist = {};
    const pool = pages.flatMap(p => (p && p.tracks && p.tracks.items) || []).filter(x => {
      if (!x || seen.has(x.uri)) return false;
      const k = x.artists[0] && x.artists[0].id;
      perArtist[k] = (perArtist[k] || 0) + 1;
      return perArtist[k] <= 3 && seen.add(x.uri);
    }).sort(() => Math.random() - .5).slice(0, 49);
    if (!pool.length) return toast(`Couldn't find songs like “${t.name}”.`);
    Player.play({ uris: [t.uri, ...pool.map(x => x.uri)] });
  } catch (e) { toast("Couldn't reach Spotify."); }
}
