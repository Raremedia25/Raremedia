/* Light/dark theme via Bootstrap 5.3 data-bs-theme. Loaded in <head> so there is no flash. */
(function () {
  const KEY = 'theo.theme';
  function stored() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function systemPref() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  function apply(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    document.querySelectorAll('[data-theme-icon]').forEach(el => {
      el.className = theme === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
    });
  }
  const initial = stored() || systemPref();
  apply(initial);

  window.Theme = {
    get current() { return document.documentElement.getAttribute('data-bs-theme') || 'light'; },
    set(theme) {
      apply(theme);
      try { localStorage.setItem(KEY, theme); } catch (e) { /* private mode */ }
    },
    toggle() { this.set(this.current === 'dark' ? 'light' : 'dark'); },
    refreshIcons() { apply(this.current); }
  };
})();
