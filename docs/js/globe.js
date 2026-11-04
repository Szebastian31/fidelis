'use strict';
// The radio globe (ui/radio/Globe.kt): a spinning Earth with its continents and a dot for every station on the map.
// Whatever sits in the circle in the middle is listed below, so there's no need to hit a tiny dot.

const TAU = Math.PI * 2;
/** How far the circle in the middle reaches, in radians at normal zoom. About 500 km across. */
const REACH = 0.04;
const DOT = '77,255,168';

const Globe = {
  land: null, world: null, lon: 0.35, lat: 0.6, explored: false,
  async loadLand() {
    if (this.land) return this.land;
    const j = await (await fetch('data/land.geojson')).json();
    const xs = [], ys = [], zs = [], starts = [];
    for (const f of j.features) {
      const g = f.geometry; if (!g) continue;
      const polys = g.type === 'Polygon' ? [g.coordinates] : g.coordinates;
      for (const p of polys) for (const ring of p) {
        if (ring.length < 3) continue;
        starts.push(xs.length);
        for (const [lo, la] of ring) { const a = la * Math.PI / 180, b = lo * Math.PI / 180; xs.push(Math.cos(a) * Math.sin(b)); ys.push(Math.sin(a)); zs.push(Math.cos(a) * Math.cos(b)); }
      }
    }
    starts.push(xs.length);
    return (this.land = { x: Float32Array.from(xs), y: Float32Array.from(ys), z: Float32Array.from(zs), starts });
  },
  async loadWorld() {
    if (this.world) return this.world;
    const list = await RadioBrowser.mapped();
    const n = list.length, x = new Float32Array(n), y = new Float32Array(n), z = new Float32Array(n);
    list.forEach((s, i) => { const a = s.lat * Math.PI / 180, b = s.lon * Math.PI / 180; x[i] = Math.cos(a) * Math.sin(b); y[i] = Math.sin(a); z[i] = Math.cos(a) * Math.cos(b); });
    return (this.world = { list, n, x, y, z });
  },
  /** The stations around the middle of the globe, nearest first; over open sea, the nearest few. */
  nearby(w, la, lo, zoom) {
    const cx = Math.cos(la) * Math.sin(lo), cy = Math.sin(la), cz = Math.cos(la) * Math.cos(lo);
    const d = new Float32Array(w.n);
    for (let i = 0; i < w.n; i++) d[i] = w.x[i] * cx + w.y[i] * cy + w.z[i] * cz;
    const order = [...d.keys()].sort((a, b) => d[b] - d[a]);
    const limit = Math.cos(REACH * 3 / zoom);
    const inside = order.filter(i => d[i] >= limit).slice(0, 40);
    const pick = inside.length ? inside : order.slice(0, 8);
    return { inside: inside.length > 0, near: pick.map(i => ({ s: w.list[i], km: Math.round(Math.acos(Math.max(-1, Math.min(1, d[i]))) * 6371) })) };
  },
};

/** The card on the Radio home that opens the globe, with a small spinning globe of its own. */
function globeCard() {
  const cv = h('canvas', { width: 160, height: 160, style: { width: '80px', height: '80px' } });
  const card = h('button.globecard', { onclick: openGlobe },
    h('div.grow', null, h('div.k', { style: { color: `rgb(${DOT})` } }, 'EXPLORE'), h('div.fname', null, 'Radio globe'), h('div.t2', null, 'Spin the Earth and tune in anywhere')), cv);
  const g = cv.getContext('2d');
  const draw = t => {
    if (!cv.isConnected) return;
    const r = 70, c = 80, phase = (t / 9000 * Math.PI) % Math.PI;
    g.clearRect(0, 0, 160, 160);
    const glow = g.createRadialGradient(c, c, 0, c, c, r * 1.15); glow.addColorStop(0, `rgba(${DOT},.25)`); glow.addColorStop(1, 'transparent');
    g.fillStyle = glow; g.fillRect(0, 0, 160, 160);
    const body = g.createRadialGradient(c - r * .4, c - r * .4, 0, c - r * .4, c - r * .4, r * 1.7); body.addColorStop(0, '#1F4A80'); body.addColorStop(1, '#081226');
    g.fillStyle = body; g.beginPath(); g.arc(c, c, r, 0, TAU); g.fill();
    g.lineWidth = 1.6;
    for (let k = 0; k < 6; k++) { const w = r * Math.abs(Math.cos(phase + k * Math.PI / 6)); g.strokeStyle = `rgba(${DOT},.45)`; g.beginPath(); g.ellipse(c, c, Math.max(w, .1), r, 0, 0, TAU); g.stroke(); }
    for (const k of [-.5, 0, .5]) { const w = r * Math.sqrt(1 - k * k); g.strokeStyle = `rgba(${DOT},.3)`; g.beginPath(); g.ellipse(c, c + r * k, w, w * .12, 0, 0, TAU); g.stroke(); }
    g.strokeStyle = 'rgba(255,255,255,.15)'; g.beginPath(); g.arc(c, c, r, 0, TAU); g.stroke();
    requestAnimationFrame(draw);
  };
  requestAnimationFrame(draw);
  return card;
}

function openGlobe() {
  const st = { lon: Globe.lon, lat: Globe.lat, zoom: 1, exploring: Globe.explored, region: null, failed: false, cy: Globe.explored ? .3 : .47 };
  const cv = h('canvas.globe');
  const title = h('div.k', { style: { color: 'var(--hi)' } }, 'EXPLORE');
  const panel = h('div.region');
  const hint = h('div.ghint', null, 'Finding the world’s radio…');
  const layer = Layers.open('globe', () => [
    cv,
    h('div.gtop', null,
      h('button.icbtn.glass', { 'aria-label': 'Back', onclick: () => Layers.close('globe') }, icon('back')),
      h('div.grow', null, title, h('div.fname.big', null, 'Radio globe')),
      h('button.icbtn.glass', { 'aria-label': 'Random place', onclick: () => {
        const w = Globe.world; if (!w || !w.n) return;
        const s = w.list[Math.floor(Math.random() * w.n)];
        go(s.lat * Math.PI / 180, s.lon * Math.PI / 180);
      } }, icon('shuffle'))),
    hint, panel,
  ], { className: 'globepage', onClose: () => { Globe.lon = st.lon; Globe.lat = st.lat; Globe.explored = st.exploring; alive = false; } });
  let alive = true;
  const g = cv.getContext('2d');
  let W = 0, H = 0, dpr = 1;
  const size = () => { dpr = window.devicePixelRatio || 1; W = layer.el.clientWidth; H = layer.el.clientHeight; cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); };
  size();
  const ro = new ResizeObserver(size); ro.observe(layer.el);

  Promise.all([Globe.loadLand().catch(() => null), Globe.loadWorld()]).then(([, w]) => {
    title.textContent = `${w.n.toLocaleString('en')} STATIONS ON THE MAP`;
    hint.textContent = st.exploring ? '' : 'Drag the globe or tap anywhere to tune in';
    if (st.exploring) settle();
  }).catch(() => { hint.replaceChildren(h('div', null, 'The globe is out of reach. Check your connection and try again.')); st.failed = true; });

  /** Swings the globe so this spot sits in the circle. */
  let anim = null;
  function go(tLat, tLon) {
    explore();
    const sLon = st.lon, sLat = st.lat, la = Math.max(-1.4, Math.min(1.4, tLat));
    let d = (tLon - sLon) % TAU; if (d > Math.PI) d -= TAU; if (d < -Math.PI) d += TAU;
    const t0 = performance.now();
    anim = t => { const v = Math.min(1, (t - t0) / 700), e = 1 - Math.pow(1 - v, 3); st.lon = sLon + d * e; st.lat = sLat + (la - sLat) * e; if (v >= 1) { anim = null; settle(); } };
  }
  function explore() {
    if (!st.exploring) { st.exploring = true; hint.textContent = ''; }
  }
  // once the globe settles, list what's in the circle
  let settleTimer = null;
  function settle() {
    clearTimeout(settleTimer);
    settleTimer = setTimeout(() => { const w = Globe.world; if (!w || !alive) return; st.region = Globe.nearby(w, st.lat, st.lon, st.zoom); showRegion(); }, 350);
  }
  function showRegion() {
    const r = st.region; if (!r || !r.near.length) { panel.replaceChildren(); panel.classList.remove('in'); return; }
    const first = r.near[0].s;
    panel.replaceChildren(
      h('div.rhead', null, h('div.k', { style: { color: 'var(--hi)' } }, r.inside ? `NEAR · ${(first.cc ? Where.name(first.cc) : first.country).toUpperCase()}` : 'OPEN SEA · NEAREST RADIO'),
        h('div.fname.big', null, first.place || (first.cc ? Where.name(first.cc) : first.country) || 'Somewhere')),
      h('div.rlist', null, r.near.map(n => h('div.rwrap', null, stationRow(n.s), h('span.km', null, n.km < 1 ? 'here' : `${n.km.toLocaleString('en')} km`)))));
    panel.classList.add('in');
  }
  /** The spot on the Earth under a tap, as lat and lon in radians. */
  function spot(px, py) {
    const r = Math.min(W, H) * .42 * st.zoom;
    const x1 = (px - W / 2) / r, y2 = (H * st.cy - py) / r, q = x1 * x1 + y2 * y2;
    if (q > 1) return null;
    const z2 = Math.sqrt(1 - q), cl = Math.cos(st.lon), sl = Math.sin(st.lon), cp = Math.cos(st.lat), sp = Math.sin(st.lat);
    const y = y2 * cp + z2 * sp, z1 = -y2 * sp + z2 * cp, x = x1 * cl + z1 * sl, z = z1 * cl - x1 * sl;
    return [Math.asin(Math.max(-1, Math.min(1, y))), Math.atan2(x, z)];
  }

  // drag to spin, pinch to zoom, tap to fly there
  const pts = new Map(); let last = null, moved = 0;
  const mid = () => { const p = [...pts.values()]; return { x: p.reduce((s, q) => s + q.x, 0) / p.length, y: p.reduce((s, q) => s + q.y, 0) / p.length, d: p.length > 1 ? Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y) : 0 }; };
  cv.addEventListener('pointerdown', e => { cv.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); last = mid(); moved = 0; anim = null; });
  cv.addEventListener('pointermove', e => {
    if (!pts.has(e.pointerId)) return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const c = mid();
    if (last) {
      const r = Math.min(W, H) * .42 * st.zoom;
      if (pts.size > 1 && last.d && c.d) st.zoom = Math.max(1, Math.min(8, st.zoom * c.d / last.d));
      const dx = c.x - last.x, dy = c.y - last.y;
      moved += Math.abs(dx) + Math.abs(dy);
      if (moved > 6) { explore(); st.lon = (st.lon - dx / r) % TAU; st.lat = Math.max(-1.4, Math.min(1.4, st.lat + dy / r)); }
    }
    last = c;
  });
  const up = e => {
    if (!pts.has(e.pointerId)) return;
    pts.delete(e.pointerId);
    if (!pts.size) {
      if (moved <= 6) { const p = spot(e.clientX - cv.getBoundingClientRect().left, e.clientY - cv.getBoundingClientRect().top); if (p) go(p[0], p[1]); }
      else settle();
    }
    last = pts.size ? mid() : null;
  };
  cv.addEventListener('pointerup', up); cv.addEventListener('pointercancel', up);
  cv.addEventListener('wheel', e => { e.preventDefault(); explore(); st.zoom = Math.max(1, Math.min(8, st.zoom * Math.exp(-e.deltaY / 500))); settle(); }, { passive: false });

  let prev = 0;
  const frame = t => {
    if (!alive) { ro.disconnect(); return; }
    if (anim) anim(t);
    else if (!st.exploring && prev) st.lon = (st.lon + (t - prev) / 1000 * .12) % TAU; // a slow spin until it's first touched
    prev = t;
    st.cy += ((st.exploring ? .3 : .47) - st.cy) * .12;
    draw();
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);

  function draw() {
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, W, H);
    const base = Math.min(W, H) * .42, r = base * st.zoom, cx = W / 2, cy = H * st.cy;
    const atmo = g.createRadialGradient(cx, cy, r * .8, cx, cy, r * 1.3); atmo.addColorStop(0, 'rgba(143,214,255,.3)'); atmo.addColorStop(1, 'transparent');
    g.fillStyle = atmo; g.beginPath(); g.arc(cx, cy, r * 1.3, 0, TAU); g.fill();
    const body = g.createRadialGradient(cx - r * .4, cy - r * .4, 0, cx - r * .4, cy - r * .4, r * 1.7); body.addColorStop(0, '#1B3A6B'); body.addColorStop(1, '#071022');
    g.fillStyle = body; g.beginPath(); g.arc(cx, cy, r, 0, TAU); g.fill();
    const cl = Math.cos(st.lon), sl = Math.sin(st.lon), cp = Math.cos(st.lat), sp = Math.sin(st.lat);

    // the continents; points round the back are pinned to the edge so the shapes stay closed
    const L = Globe.land;
    if (L) {
      const fill = new Path2D(), coast = new Path2D();
      for (let k = 0; k < L.starts.length - 1; k++) {
        const a = L.starts[k], b = L.starts[k + 1];
        let seen = false;
        for (let i = a; i < b; i++) if (L.y[i] * sp + (L.z[i] * cl + L.x[i] * sl) * cp > 0) { seen = true; break; }
        if (!seen) continue;
        let pen = false;
        for (let i = a; i < b; i++) {
          const x1 = L.x[i] * cl - L.z[i] * sl, z1 = L.z[i] * cl + L.x[i] * sl;
          let px = x1, py = L.y[i] * cp - z1 * sp;
          const vis = L.y[i] * sp + z1 * cp > 0;
          if (!vis) { const m = Math.max(Math.hypot(px, py), 1e-4); px /= m; py /= m; }
          const sx = cx + r * px, sy = cy - r * py;
          if (i === a) fill.moveTo(sx, sy); else fill.lineTo(sx, sy);
          if (vis && pen) coast.lineTo(sx, sy); else if (vis) coast.moveTo(sx, sy);
          pen = vis;
        }
        fill.closePath();
      }
      g.fillStyle = 'rgba(58,120,184,.4)'; g.fill(fill);
      g.strokeStyle = 'rgba(169,212,255,.55)'; g.lineWidth = 1; g.stroke(coast);
    }

    const w = Globe.world;
    if (w) {
      const dot = Math.min(6, 2.2 * Math.sqrt(st.zoom));
      const front = [], edge = [];
      for (let i = 0; i < w.n; i++) {
        const x1 = w.x[i] * cl - w.z[i] * sl, z1 = w.z[i] * cl + w.x[i] * sl, z2 = w.y[i] * sp + z1 * cp;
        if (z2 <= 0) continue;
        const sx = cx + r * x1, sy = cy - r * (w.y[i] * cp - z1 * sp);
        if (sx < -20 || sx > W + 20 || sy < -20 || sy > H + 20) continue;
        (z2 > .3 ? front : edge).push(sx, sy);
      }
      const paint = (pts2, col) => { g.fillStyle = col; for (let i = 0; i < pts2.length; i += 2) g.fillRect(pts2[i] - dot / 2, pts2[i + 1] - dot / 2, dot, dot); };
      paint(edge, `rgba(${DOT},.35)`); paint(front, `rgba(${DOT},.85)`);
    }
    g.strokeStyle = 'rgba(255,255,255,.1)'; g.lineWidth = 1; g.beginPath(); g.arc(cx, cy, r, 0, TAU); g.stroke();

    if (st.exploring) {
      // the listed stations light up, and the circle shows what's being listed
      if (st.region) for (const n of st.region.near) {
        const a = n.s.lat * Math.PI / 180, b = n.s.lon * Math.PI / 180, x = Math.cos(a) * Math.sin(b), y = Math.sin(a), z = Math.cos(a) * Math.cos(b);
        const x1 = x * cl - z * sl, z1 = z * cl + x * sl;
        if (y * sp + z1 * cp > 0) { g.fillStyle = '#fff'; g.beginPath(); g.arc(cx + r * x1, cy - r * (y * cp - z1 * sp), 3.5, 0, TAU); g.fill(); }
      }
      const rr = base * REACH * 3;
      g.fillStyle = 'rgba(255,255,255,.08)'; g.beginPath(); g.arc(cx, cy, rr, 0, TAU); g.fill();
      g.strokeStyle = 'rgba(255,255,255,.85)'; g.lineWidth = 1.5; g.stroke();
    }
  }
}
