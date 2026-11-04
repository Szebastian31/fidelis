'use strict';
// Synced lyrics from LRCLIB, matched to the song's title, artist and length (integration/lyrics/LrcLib.kt).

const Lyrics = {
  cache: {},
  /** The parts of a title that change between releases but not the words: "(Remastered 2011)", "- Live", "feat. …". */
  clean: t => t.replace(/\s*[([](feat\.?|ft\.?|with|remaster(ed)?|live|radio edit|mono|stereo|deluxe|bonus)[^)\]]*[)\]]/gi, '')
    .replace(/\s+-\s+(remaster(ed)?|live|radio edit|mono|stereo|single version|\d{4} remaster).*$/i, '').trim(),
  norm: s => s.toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim(),

  /** { kind: 'synced', lines: [{ t, text }] } | { kind: 'plain', lines: [text] } | { kind: 'instrumental' } | { kind: 'none' } */
  async find(track) {
    const key = track.uri;
    if (this.cache[key]) return this.cache[key];
    const title = this.clean(track.name), artist = track.artists[0] ? track.artists[0].name : '';
    const secs = Math.round(track.duration_ms / 1000);
    const got = await this.lookup(title, artist, track.album ? track.album.name : '', secs);
    return (this.cache[key] = got);
  },

  async lookup(title, artist, album, secs) {
    if (Demo.on) return { kind: 'synced', lines: [[0, '♪'], [4, 'This is the demo of Fidelis'], [9, 'the words follow the song'], [14, 'and the line you sing glows'], [19, 'tap any line to jump there'], [26, '♪'], [32, 'with your own Spotify'], [38, 'every song finds its lyrics'], [44, 'from LRCLIB'], [52, '♪']].map(([t, text]) => ({ t: t * 1000 + 30000, text })) };
    const pick = r => {
      if (!r) return null;
      if (r.instrumental) return { kind: 'instrumental' };
      if (r.syncedLyrics) return { kind: 'synced', lines: this.parse(r.syncedLyrics) };
      if (r.plainLyrics) return { kind: 'plain', lines: r.plainLyrics.split('\n') };
      return null;
    };
    try {
      const q = new URLSearchParams({ track_name: title, artist_name: artist, album_name: album, duration: secs });
      const r = await fetch('https://lrclib.net/api/get?' + q);
      if (r.ok) { const got = pick(await r.json()); if (got) return got; }
    } catch (e) { /* offline or blocked: try the search */ }
    try {
      const r = await fetch('https://lrclib.net/api/search?' + new URLSearchParams({ track_name: title, artist_name: artist }));
      if (r.ok) {
        const want = this.norm(title);
        const list = (await r.json()).filter(x => this.norm(x.trackName || '') === want || this.norm(x.trackName || '').startsWith(want))
          .sort((a, b) => (!!b.syncedLyrics - !!a.syncedLyrics) || Math.abs(a.duration - secs) - Math.abs(b.duration - secs));
        const close = list.find(x => Math.abs(x.duration - secs) <= 4) || null;
        const got = pick(close);
        if (got) return got;
      }
    } catch (e) { /* nothing more to try */ }
    return { kind: 'none' };
  },

  parse(lrc) {
    const out = [];
    for (const line of lrc.split('\n')) {
      const stamps = [...line.matchAll(/\[(\d+):(\d+(?:[.:]\d+)?)\]/g)];
      if (!stamps.length) continue;
      const text = line.replace(/\[[^\]]*\]/g, '').trim();
      for (const m of stamps) out.push({ t: Math.round((+m[1] * 60 + parseFloat(m[2].replace(':', '.'))) * 1000), text });
    }
    return out.sort((a, b) => a.t - b.t);
  },
};
