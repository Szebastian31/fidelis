'use strict';
// The shell (ui/navigation/AppNavHost.kt): five tabs in a glass capsule, the mini player above them, and starting up.

const TABS = [['playlists', 'Playlists', 'playlists'], ['search', 'Search', 'search'], ['listen', 'Listen', 'radio'], ['mood', 'Mood', 'mood'], ['profile', 'Profile', 'profile']];

const App = {
  shown: null, message: null,
  /** [fresh]: a new page, so start at its top. */
  render(fresh = false) {
    const main = document.getElementById('main');
    const ready = Demo.on || (Auth.clientId && Auth.signedIn);
    document.body.classList.toggle('ready', !!ready);
    if (!ready) {
      main.replaceChildren(setupScreen(this.message));
      document.getElementById('tabs').replaceChildren();
      renderMini();
      this.shown = 'setup';
      return;
    }
    const page = Nav.stack[Nav.stack.length - 1];
    const key = Nav.tab + ':' + (page ? page.kind + page.id : '');
    // keep the place in the list while data arrives; a new page starts at the top
    const keep = document.activeElement && document.activeElement.matches('.searchIn') ? document.activeElement : null;
    const caret = keep ? keep.selectionStart : 0;
    const top = main.scrollTop;
    let screen;
    if (page) {
      const label = Nav.stack.length > 1 ? 'Back' : TABS.find(t => t[0] === Nav.tab)[1];
      screen = h('div.page', null, back(label),
        ({ album: albumPage, artist: artistPage, playlist: playlistPage, liked: likedPage, radioBrowse: radioBrowsePage, show: showPage, mix: mixPage }[page.kind] || likedPage)(page.id));
    } else screen = { playlists: playlistsScreen, search: searchScreen, listen: listenScreen, mood: moodScreen, profile: profileScreen }[Nav.tab]();
    main.replaceChildren(screen);
    main.scrollTop = !fresh && key === this.shown ? top : 0;
    this.shown = key;
    if (keep) { const s = main.querySelector('.searchIn'); if (s) { s.focus(); s.setSelectionRange(caret, caret); } }

    document.getElementById('tabs').replaceChildren(h('div.capsule', null, ...TABS.map(([k, label, ic]) => h('button.tab' + (k === Nav.tab ? '.on' : ''), { onclick: () => Nav.go(k) }, icon(ic), h('span', null, label)))));
    renderMini();
  },

  start() {
    Data.v = {}; this.message = null;
    this.render(true);
    Player.refresh();
  },
};

(async () => {
  Look.apply();
  const msg = await Auth.handleRedirect().catch(() => "Couldn't reach Spotify to finish signing in. Try once more.");
  if (msg) App.message = msg;
  App.render(true);
  if (Demo.on || Auth.signedIn) Player.refresh();
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) clearTimeout(Player.timer);
    else if (Demo.on || Auth.signedIn) Player.refresh();
  });
  if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => {});
})();
