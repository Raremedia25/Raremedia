/* Application shell: loads the session, injects sidebar + topbar, then fires `app:ready` with the user.
 *
 * Page contract:  <body data-page="products" data-title="Products">
 *                 <div id="app-content" hidden> ...page markup... </div>
 * Items marked admin: true are only shown to the administrator; workers see the rest.
 */
window.Layout = (function () {
  const NAV = [
    { key: 'dashboard', href: '/index.html', icon: 'bi-speedometer2', label: 'Dashboard' },
    { key: 'products', icon: 'bi-box-seam', label: 'Products', children: [
      { key: 'products', href: '/products.html', label: 'All Products' },
      { key: 'products-add', href: '/products.html?add=1', label: 'Add Product', admin: true }
    ] },
    { key: 'sales', icon: 'bi-cart-check', label: 'Sales', children: [
      { key: 'sell', href: '/sell.html', label: 'Sell Items' },
      { key: 'sales', href: '/sales.html', label: 'Sales History' }
    ] },
    { key: 'stock', href: '/stock.html', icon: 'bi-clipboard-data', label: 'Stock' },
    { key: 'reports', href: '/reports.html', icon: 'bi-graph-up', label: 'Reports', admin: true },
    { key: 'workers', href: '/workers.html', icon: 'bi-people', label: 'Workers', admin: true },
    { key: 'settings', href: '/settings.html', icon: 'bi-gear', label: 'Settings', admin: true }
  ];

  const esc = s => UI.escapeHtml(s);
  const visible = item => !item.admin || Auth.isAdmin;

  function link(item, activeKey, sub) {
    const active = item.key === activeKey;
    return '<a class="nav-link' + (sub ? ' nav-sub' : '') + (active ? ' active' : '') + '" href="' + item.href + '">' +
      (item.icon ? '<i class="bi ' + item.icon + '"></i>' : '<i class="bi bi-dot"></i>') + '<span>' + esc(item.label) + '</span></a>';
  }

  function renderNav(activeKey) {
    return NAV.filter(visible).map(item => {
      if (!item.children) return link(item, activeKey, false);
      const children = item.children.filter(visible);
      const open = children.some(c => c.key === activeKey);
      return '<div class="nav-group' + (open ? ' open' : '') + '">' +
        '<div class="nav-link nav-group-title"><i class="bi ' + item.icon + '"></i><span>' + esc(item.label) + '</span></div>' +
        children.map(c => link(c, activeKey, true)).join('') +
      '</div>';
    }).join('');
  }

  function shell(activeKey, title) {
    const me = Auth.me;
    return (
      '<div class="tt-app">' +
        '<aside class="tt-sidebar">' +
          '<a class="tt-logo" href="/index.html"><i class="bi bi-cpu"></i><span id="tt-company">THEO TECH LTD</span></a>' +
          '<div class="tt-tagline">Electronics Sales &amp; Stock</div>' +
          '<nav class="nav flex-column">' + renderNav(activeKey) + '</nav>' +
          '<div class="mt-auto p-3 small text-secondary"><i class="bi bi-person-circle me-1"></i>' + esc(me.fullName) +
            ' <span class="badge ' + (Auth.isAdmin ? 'text-bg-primary' : 'text-bg-secondary') + ' ms-1">' + (Auth.isAdmin ? 'Admin' : 'Worker') + '</span></div>' +
        '</aside>' +
        '<div class="tt-backdrop" data-action="close-sidebar"></div>' +
        '<div class="tt-main">' +
          '<header class="tt-topbar">' +
            '<button class="btn btn-outline-secondary d-lg-none" data-action="toggle-sidebar" aria-label="Menu"><i class="bi bi-list"></i></button>' +
            '<h1 class="tt-page-title">' + esc(title) + '</h1>' +
            '<div class="ms-auto d-flex align-items-center gap-2">' +
              '<a class="btn btn-success btn-sm d-none d-sm-inline-flex" href="/sell.html"><i class="bi bi-cart-plus me-1"></i>Sell</a>' +
              '<button class="btn btn-outline-secondary btn-sm" data-action="toggle-theme" title="Light / dark"><i data-theme-icon class="bi bi-moon-stars"></i></button>' +
              '<div class="dropdown">' +
                '<button class="btn btn-outline-secondary btn-sm dropdown-toggle" data-bs-toggle="dropdown"><i class="bi bi-person-circle me-1"></i>' + esc(me.username) + '</button>' +
                '<ul class="dropdown-menu dropdown-menu-end">' +
                  (Auth.isAdmin ? '<li><a class="dropdown-item" href="/settings.html"><i class="bi bi-gear me-2"></i>Settings</a></li>' : '') +
                  '<li><a class="dropdown-item" href="/change-password.html"><i class="bi bi-key me-2"></i>Change password</a></li>' +
                  '<li><hr class="dropdown-divider"></li>' +
                  '<li><button class="dropdown-item" data-action="logout"><i class="bi bi-box-arrow-right me-2"></i>Log out</button></li>' +
                '</ul>' +
              '</div>' +
            '</div>' +
          '</header>' +
          '<main class="tt-content" id="tt-main-slot"></main>' +
        '</div>' +
      '</div>');
  }

  /** Never leave the user with a blank page: say what went wrong and offer a way out. */
  function showFailure(err) {
    const message = (err && err.name === 'ApiError') ? Api.messageFor(err) : 'The page could not be loaded.';
    const detail = err && err.code && err.code !== 'UNKNOWN' ? ' (' + err.code + (err.status ? ', HTTP ' + err.status : '') + ')' : '';
    document.body.insertAdjacentHTML('afterbegin',
      '<div class="container py-5" style="max-width:520px">' +
        '<div class="alert alert-danger"><h5 class="alert-heading"><i class="bi bi-exclamation-triangle me-2"></i>Cannot load the page</h5>' +
          '<p class="mb-3">' + esc(message) + '<span class="text-secondary small">' + esc(detail) + '</span></p>' +
          '<button class="btn btn-danger btn-sm me-2" onclick="location.reload()"><i class="bi bi-arrow-clockwise me-1"></i>Retry</button>' +
          '<a class="btn btn-outline-secondary btn-sm" href="/login.html">Sign in again</a>' +
        '</div></div>');
  }

  function wire() {
    document.body.addEventListener('click', e => {
      const t = e.target.closest('[data-action]');
      if (!t) return;
      switch (t.dataset.action) {
        case 'toggle-theme': Theme.toggle(); break;
        case 'logout': Auth.logout(); break;
        case 'toggle-sidebar': document.body.classList.toggle('tt-sidebar-open'); break;
        case 'close-sidebar': document.body.classList.remove('tt-sidebar-open'); break;
      }
    });
    document.addEventListener('settings:changed', e => { if (e.detail && e.detail.companyName) setCompany(e.detail.companyName); });
  }

  function setCompany(name) {
    const el = document.getElementById('tt-company');
    if (el && name) el.textContent = name;
    document.title = document.title.replace(/·.*$/, '· ' + name);
  }

  async function init() {
    const body = document.body;
    const pageKey = body.dataset.page;
    const title = body.dataset.title || '';
    try {
      await Auth.load();
    } catch (e) {
      if (e && e.status === 401) return; // Api is already redirecting to the login page
      console.error(e);
      showFailure(e);
      return;
    }
    if (Auth.me.mustChangePassword && !location.pathname.endsWith('/change-password.html')) {
      location.replace('/change-password.html');
      return;
    }
    if (body.dataset.admin !== undefined && !Auth.isAdmin) {
      location.replace('/index.html');
      return;
    }
    try {
      const content = document.getElementById('app-content');
      const wrapper = document.createElement('div');
      wrapper.innerHTML = shell(pageKey, title);
      body.insertBefore(wrapper.firstElementChild, body.firstChild);
      document.getElementById('tt-main-slot').appendChild(content);
      content.hidden = false;
      if (!Auth.isAdmin) content.querySelectorAll('[data-admin]').forEach(el => el.remove());
      wire();
      Theme.refreshIcons();
      document.dispatchEvent(new CustomEvent('app:ready', { detail: { me: Auth.me, isAdmin: Auth.isAdmin } }));
    } catch (e) {
      console.error(e);
      showFailure(e);
      return;
    }
    try { const s = await Api.get('/api/settings', { silent: true }); setCompany(s.companyName); } catch (e) { /* default name stays */ }
  }

  return { init, NAV };
})();

document.addEventListener('DOMContentLoaded', () => Layout.init());
