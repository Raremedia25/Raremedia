/* Server-paginated table for list endpoints (?page&size&sort&q + filters → { content, page, size, totalElements, totalPages, first, last }).
 *
 *   const dt = new DataTable(el, {
 *     url: '/api/sales',
 *     columns: [{ key: 'productName', label: 'Product', sortable: true, render: row => html }, ...],
 *     query: () => ({ from, to }),          // extra filters, re-read on every load
 *     sort: 'soldAt,desc',
 *     search: true,                          // built-in search box bound to ?q=
 *     onAction: (action, row, button) => {}, // clicks on [data-action] inside a row
 *     onRow: row => {},                      // click anywhere else on a row
 *     onLoad: data => {}                     // after every successful load
 *   });
 *   dt.reload();
 *
 * Cells without render() are HTML-escaped; render() output is trusted HTML — escape inside it.
 */
window.DataTable = (function () {
  const PAGE_SIZES = [10, 20, 50, 100];

  class DataTable {
    constructor(container, opts) {
      this.el = container;
      this.opts = Object.assign({ pageSize: 20, search: true, sort: '', pageSizes: PAGE_SIZES, rowKey: 'id', searchPlaceholder: 'Search…' }, opts);
      this.state = { page: 0, size: this.opts.pageSize, sort: this.opts.sort, q: '' };
      this.data = null;
      this._build();
      this._wire();
    }

    _build() {
      const o = this.opts;
      const head = o.columns.map(c => {
        const cls = ['text-nowrap', c.className || '', c.sortable ? 'tt-sortable' : ''].join(' ').trim();
        return '<th scope="col" class="' + cls + '"' + (c.sortable ? ' data-sort="' + c.key + '"' : '') + '>' +
          UI.escapeHtml(c.label) + (c.sortable ? ' <i class="bi tt-sort-icon"></i>' : '') + '</th>';
      }).join('');

      this.el.classList.add('tt-datatable');
      this.el.innerHTML =
        '<div class="d-flex flex-wrap align-items-center gap-2 mb-2 tt-dt-toolbar">' +
          (o.search ? '<div class="input-group input-group-sm tt-dt-search"><span class="input-group-text"><i class="bi bi-search"></i></span>' +
            '<input type="search" class="form-control" data-role="search" placeholder="' + UI.escapeHtml(o.searchPlaceholder) + '"></div>' : '') +
          '<div class="tt-dt-filters d-flex flex-wrap align-items-center gap-2"></div>' +
          '<div class="ms-auto d-flex align-items-center gap-2">' +
            '<button type="button" class="btn btn-outline-secondary btn-sm" data-role="refresh" title="Refresh"><i class="bi bi-arrow-clockwise"></i></button>' +
            '<div class="tt-dt-toolbar-slot d-flex gap-2"></div>' +
          '</div>' +
        '</div>' +
        '<div class="table-responsive border rounded">' +
          '<table class="table table-hover align-middle mb-0"><thead class="table-light"><tr>' + head + '</tr></thead>' +
          '<tbody></tbody></table>' +
        '</div>' +
        '<div class="d-flex flex-wrap align-items-center gap-2 mt-2 small tt-dt-footer">' +
          '<span class="text-secondary" data-role="summary"></span>' +
          '<div class="ms-auto d-flex align-items-center gap-2">' +
            '<label class="text-secondary d-none d-sm-inline">Rows per page</label>' +
            '<select class="form-select form-select-sm w-auto" data-role="size">' +
              o.pageSizes.map(s => '<option value="' + s + '"' + (s === this.state.size ? ' selected' : '') + '>' + s + '</option>').join('') +
            '</select>' +
            '<nav><ul class="pagination pagination-sm mb-0" data-role="pager"></ul></nav>' +
          '</div>' +
        '</div>';

      this.tbody = this.el.querySelector('tbody');
      this.filtersSlot = this.el.querySelector('.tt-dt-filters');
      this.toolbarSlot = this.el.querySelector('.tt-dt-toolbar-slot');
    }

    _wire() {
      const search = this.el.querySelector('[data-role=search]');
      if (search) {
        let timer;
        search.addEventListener('input', () => {
          clearTimeout(timer);
          timer = setTimeout(() => { this.state.q = search.value.trim(); this.state.page = 0; this.reload(); }, 300);
        });
      }
      this.el.querySelector('[data-role=refresh]').addEventListener('click', () => this.reload());
      this.el.querySelector('[data-role=size]').addEventListener('change', e => {
        this.state.size = Number(e.target.value); this.state.page = 0; this.reload();
      });
      this.el.querySelector('thead').addEventListener('click', e => {
        const th = e.target.closest('th[data-sort]');
        if (!th) return;
        const key = th.dataset.sort;
        const [curKey, curDir] = (this.state.sort || '').split(',');
        const dir = curKey === key && curDir !== 'desc' ? 'desc' : 'asc';
        this.state.sort = key + ',' + dir;
        this.state.page = 0;
        this.reload();
      });
      this.el.querySelector('[data-role=pager]').addEventListener('click', e => {
        const a = e.target.closest('[data-page]');
        if (!a || a.parentElement.classList.contains('disabled')) return;
        e.preventDefault();
        this.state.page = Number(a.dataset.page);
        this.reload();
      });
      this.tbody.addEventListener('click', e => {
        const tr = e.target.closest('tr[data-key]');
        if (!tr || !this.data) return;
        const row = this.data.content.find(r => String(r[this.opts.rowKey]) === tr.dataset.key);
        const btn = e.target.closest('[data-action]');
        if (btn && this.opts.onAction) { e.preventDefault(); this.opts.onAction(btn.dataset.action, row, btn); return; }
        if (!btn && this.opts.onRow && !e.target.closest('a,button,input,select')) this.opts.onRow(row);
      });
    }

    async reload(resetPage) {
      if (resetPage) this.state.page = 0;
      const query = Object.assign({ page: this.state.page, size: this.state.size, sort: this.state.sort, q: this.state.q },
        this.opts.query ? this.opts.query() : {});
      this._setLoading(true);
      const token = this._token = {};
      try {
        const data = await Api.get(this.opts.url, { query });
        if (token !== this._token) return;
        this.data = data;
        if (data.totalPages > 0 && this.state.page >= data.totalPages) { this.state.page = data.totalPages - 1; return this.reload(); }
        this._renderRows();
        this._renderFooter();
        if (this.opts.onLoad) this.opts.onLoad(data);
      } catch (err) {
        if (token !== this._token) return;
        this.data = null;
        this._renderError(err);
      } finally {
        if (token === this._token) this._setLoading(false);
      }
    }

    _setLoading(on) {
      this.el.classList.toggle('tt-dt-loading', on);
      if (on && !this.data) {
        this.tbody.innerHTML = '<tr><td colspan="' + this.opts.columns.length + '" class="text-center text-secondary py-4">' +
          '<span class="spinner-border spinner-border-sm me-2"></span>Loading…</td></tr>';
      }
    }

    _renderRows() {
      if (!this.data) return;
      this._markSort();
      const cols = this.opts.columns;
      if (!this.data.content.length) {
        this.tbody.innerHTML = '<tr><td colspan="' + cols.length + '" class="text-center text-secondary py-4">' +
          '<i class="bi bi-inbox me-2"></i>Nothing to show</td></tr>';
        return;
      }
      this.tbody.innerHTML = this.data.content.map(row => {
        const cells = cols.map(c => {
          const v = c.render ? c.render(row) : UI.escapeHtml(row[c.key] == null ? '' : row[c.key]);
          return '<td class="' + (c.className || '') + '">' + (v == null ? '' : v) + '</td>';
        }).join('');
        return '<tr data-key="' + UI.escapeHtml(row[this.opts.rowKey]) + '"' + (this.opts.onRow ? ' role="button"' : '') + '>' + cells + '</tr>';
      }).join('');
    }

    _renderError(err) {
      this.tbody.innerHTML = '<tr><td colspan="' + this.opts.columns.length + '" class="text-center text-danger py-4">' +
        '<i class="bi bi-exclamation-circle me-2"></i>' + UI.escapeHtml(Api.messageFor(err)) +
        ' <button type="button" class="btn btn-link btn-sm p-0 align-baseline" data-role="retry">Retry</button></td></tr>';
      this.tbody.querySelector('[data-role=retry]').addEventListener('click', () => this.reload());
      this.el.querySelector('[data-role=summary]').textContent = '';
      this.el.querySelector('[data-role=pager]').innerHTML = '';
    }

    _markSort() {
      const [key, dir] = (this.state.sort || '').split(',');
      this.el.querySelectorAll('th[data-sort]').forEach(th => {
        const icon = th.querySelector('.tt-sort-icon');
        const active = th.dataset.sort === key;
        th.classList.toggle('tt-sorted', active);
        icon.className = 'bi tt-sort-icon ' + (active ? (dir === 'desc' ? 'bi-sort-down' : 'bi-sort-up') : 'bi-arrow-down-up opacity-25');
      });
    }

    _renderFooter() {
      const d = this.data;
      const summary = this.el.querySelector('[data-role=summary]');
      const pager = this.el.querySelector('[data-role=pager]');
      if (!d) { summary.textContent = ''; pager.innerHTML = ''; return; }
      const from = d.totalElements === 0 ? 0 : d.page * d.size + 1;
      const to = Math.min(d.totalElements, (d.page + 1) * d.size);
      summary.textContent = 'Showing ' + UI.number(from) + '–' + UI.number(to) + ' of ' + UI.number(d.totalElements);

      const items = [];
      const li = (label, page, disabled, active) =>
        '<li class="page-item' + (disabled ? ' disabled' : '') + (active ? ' active' : '') + '">' +
        '<a class="page-link" href="#" data-page="' + page + '">' + label + '</a></li>';
      items.push(li('&laquo;', d.page - 1, d.first, false));
      const start = Math.max(0, Math.min(d.page - 2, d.totalPages - 5));
      const end = Math.min(d.totalPages, start + 5);
      for (let p = start; p < end; p++) items.push(li(String(p + 1), p, false, p === d.page));
      items.push(li('&raquo;', d.page + 1, d.last || d.totalPages === 0, false));
      pager.innerHTML = items.join('');
    }

    /** Adds filter controls (an element or HTML string) next to the search box. */
    addFilter(elOrHtml) {
      if (typeof elOrHtml === 'string') this.filtersSlot.insertAdjacentHTML('beforeend', elOrHtml);
      else this.filtersSlot.appendChild(elOrHtml);
      return this.filtersSlot.lastElementChild;
    }

    /** Adds a button (e.g. "Add") to the right-hand toolbar. */
    addToolbarButton(html) {
      this.toolbarSlot.insertAdjacentHTML('beforeend', html);
      return this.toolbarSlot.lastElementChild;
    }
  }

  return DataTable;
})();
