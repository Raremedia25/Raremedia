/* Shared UI helpers: toasts, confirm dialog, modal forms, formatting (RWF, dates), form errors, date periods. */
window.UI = (function () {
  const moneyFmt = new Intl.NumberFormat('en-RW', { maximumFractionDigits: 0 });
  const numberFmt = new Intl.NumberFormat('en-RW');
  const dateFmt = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' });
  const dateTimeFmt = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  const timeFmt = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

  const VALIDATION = {
    required: 'This field is required',
    invalid: 'Invalid value',
    maxLength: 'Too long',
    min: 'Must be zero or more',
    positive: 'Must be at least 1',
    belowSold: 'Stock cannot be lower than the quantity already sold',
    username: '3-40 characters: letters, digits, dots, dashes or underscores',
    imageType: 'Only JPEG, PNG, WebP or GIF pictures are accepted',
    imageSize: 'The picture must be smaller than 2 MB',
    passwordLength: 'Password must be between 8 and 100 characters',
    passwordComplexity: 'Password must contain both letters and digits',
    passwordIncorrect: 'The current password is incorrect',
    passwordSameAsOld: 'The new password must be different from the current one',
    passwordMismatch: 'The two passwords do not match'
  };

  const STOCK = {
    AVAILABLE: ['AVAILABLE', 'success'],
    LOW: ['LOW STOCK', 'warning text-dark'],
    OUT_OF_STOCK: ['OUT OF STOCK', 'danger']
  };

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function toastContainer() {
    let c = document.getElementById('tt-toasts');
    if (!c) {
      c = document.createElement('div');
      c.id = 'tt-toasts';
      c.className = 'toast-container position-fixed top-0 end-0 p-3 tt-toast-container';
      document.body.appendChild(c);
    }
    return c;
  }

  function toast(message, type, delay) {
    type = type || 'primary';
    const el = document.createElement('div');
    el.className = 'toast align-items-center text-bg-' + type + ' border-0';
    el.setAttribute('role', 'alert');
    el.innerHTML =
      '<div class="d-flex"><div class="toast-body">' + escapeHtml(message) + '</div>' +
      '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>';
    toastContainer().appendChild(el);
    const t = new bootstrap.Toast(el, { delay: delay || (type === 'danger' ? 6000 : 3500) });
    el.addEventListener('hidden.bs.toast', () => el.remove());
    t.show();
  }

  function confirm(message, opts) {
    opts = opts || {};
    return new Promise(resolve => {
      const wrap = document.createElement('div');
      wrap.innerHTML =
        '<div class="modal fade" tabindex="-1"><div class="modal-dialog modal-dialog-centered"><div class="modal-content">' +
        '<div class="modal-header"><h5 class="modal-title">' + escapeHtml(opts.title || 'Please confirm') + '</h5>' +
        '<button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>' +
        '<div class="modal-body">' + escapeHtml(message) + '</div>' +
        '<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>' +
        '<button type="button" class="btn btn-' + (opts.variant || 'danger') + '" data-role="ok">' + escapeHtml(opts.okLabel || 'Yes') + '</button></div>' +
        '</div></div></div>';
      const el = wrap.firstElementChild;
      document.body.appendChild(el);
      const modal = new bootstrap.Modal(el);
      let ok = false;
      el.querySelector('[data-role=ok]').addEventListener('click', () => { ok = true; modal.hide(); });
      el.addEventListener('hidden.bs.modal', () => { el.remove(); resolve(ok); });
      modal.show();
    });
  }

  /**
   * Modal with a form. `body` is HTML placed inside a <form>; the footer holds Cancel + Submit.
   *   UI.modal({ title: 'Add product', body: html, size: 'lg', submitLabel: 'Save',
   *              onSubmit: async (values, form, handle) => { await Api.post(...); handle.hide(); } });
   * onSubmit errors: ApiError with `errors` → field highlights; anything else → message box.
   * Returns { el, form, modal, hide(), setError(msg) }.
   */
  function modal(opts) {
    const wrap = document.createElement('div');
    wrap.innerHTML =
      '<div class="modal fade" tabindex="-1" data-bs-backdrop="static"><div class="modal-dialog modal-dialog-scrollable' +
      (opts.size ? ' modal-' + opts.size : '') + '"><form class="modal-content" novalidate>' +
      '<div class="modal-header"><h5 class="modal-title">' + escapeHtml(opts.title || '') + '</h5>' +
      '<button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>' +
      '<div class="modal-body"><div class="alert alert-danger py-2" data-role="error" hidden></div>' + (opts.body || '') + '</div>' +
      (opts.noFooter ? '' :
        '<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">' + (opts.onSubmit ? 'Cancel' : 'Close') + '</button>' +
        (opts.onSubmit ? '<button type="submit" class="btn btn-' + (opts.variant || 'primary') + '" data-role="submit"><i class="bi bi-check2 me-1"></i>' + escapeHtml(opts.submitLabel || 'Save') + '</button>' : '') +
        '</div>') +
      '</form></div></div>';
    const el = wrap.firstElementChild;
    const form = el.querySelector('form');
    const errorBox = el.querySelector('[data-role=error]');
    document.body.appendChild(el);
    const bsModal = new bootstrap.Modal(el);
    const handle = {
      el, form, modal: bsModal,
      hide: () => bsModal.hide(),
      setError: msg => { errorBox.textContent = msg || ''; errorBox.hidden = !msg; }
    };
    form.addEventListener('submit', async e => {
      e.preventDefault();
      if (!opts.onSubmit) return;
      handle.setError(null);
      clearFieldErrors(form);
      const btn = form.querySelector('[data-role=submit]');
      setLoading(btn, true);
      try {
        await opts.onSubmit(formValues(form), form, handle);
      } catch (err) {
        if (err && err.name === 'ApiError' && err.errors && Object.keys(err.errors).length) {
          setFieldErrors(form, err.errors);
          const unmatched = Object.keys(err.errors).filter(k => !form.querySelector('[name="' + k + '"]'));
          if (unmatched.length) handle.setError(Api.messageFor(err));
        } else if (err && err.name === 'ApiError') {
          handle.setError(Api.messageFor(err));
        } else {
          console.error(err);
          handle.setError(VALIDATION.invalid);
        }
      } finally {
        setLoading(btn, false);
      }
    });
    el.addEventListener('shown.bs.modal', () => {
      const first = form.querySelector('input:not([type=hidden]):not([disabled]),select,textarea');
      if (first) first.focus();
    });
    el.addEventListener('hidden.bs.modal', () => { el.remove(); if (opts.onHidden) opts.onHidden(); });
    bsModal.show();
    return handle;
  }

  /** Reads a form into an object: checkboxes → boolean, numbers → Number, '' → null. */
  function formValues(form) {
    const out = {};
    Array.from(form.elements).forEach(el => {
      if (!el.name || el.disabled) return;
      if (el.type === 'checkbox') {
        out[el.name] = el.checked;
      } else if (el.type === 'radio') {
        if (el.checked) out[el.name] = el.value;
        else if (out[el.name] === undefined) out[el.name] = null;
      } else if (el.type === 'number') {
        out[el.name] = el.value === '' ? null : Number(el.value);
      } else {
        const v = typeof el.value === 'string' ? el.value.trim() : el.value;
        out[el.name] = v === '' ? null : v;
      }
    });
    return out;
  }

  /** Fills form fields from an object (inverse of formValues, for edit dialogs). */
  function fillForm(form, values) {
    Object.keys(values || {}).forEach(name => {
      const v = values[name];
      const els = form.querySelectorAll('[name="' + name + '"]');
      els.forEach(el => {
        if (el.type === 'checkbox') el.checked = !!v;
        else if (el.type === 'radio') el.checked = String(v) === el.value;
        else el.value = v == null ? '' : v;
      });
    });
  }

  function money(v) {
    if (v == null || v === '') return '—';
    return 'RWF ' + moneyFmt.format(Math.round(Number(v)));
  }
  function number(v) { return v == null ? '—' : numberFmt.format(Number(v)); }
  function toDate(v) {
    if (v instanceof Date) return v;
    if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v)) return new Date(v + 'T00:00:00');
    return new Date(v);
  }
  function date(v) { return v ? dateFmt.format(toDate(v)) : '—'; }
  function dateTime(v) { return v ? dateTimeFmt.format(toDate(v)) : '—'; }
  function time(v) { return v ? timeFmt.format(toDate(v)) : '—'; }

  function badge(text, variant, extraClass) {
    return '<span class="badge text-bg-' + (variant || 'secondary') + ' ' + (extraClass || '') + '">' + escapeHtml(text) + '</span>';
  }

  /** Product picture as <img>, or a neutral placeholder when there is none. cls: 'tt-thumb' | 'tt-thumb-lg'. */
  function thumb(p, cls) {
    cls = cls || 'tt-thumb';
    if (p && p.imageUrl) return '<img src="' + escapeHtml(p.imageUrl) + '" class="' + cls + ' rounded border bg-white" alt="">';
    return '<span class="' + cls + ' rounded border bg-body-tertiary d-inline-flex align-items-center justify-content-center text-secondary"><i class="bi bi-image"></i></span>';
  }

  /** AVAILABLE / LOW STOCK / OUT OF STOCK badge. */
  function stockBadge(status) {
    const s = STOCK[status] || [status || '', 'secondary'];
    return '<span class="badge text-bg-' + s[1] + '">' + escapeHtml(s[0]) + '</span>';
  }

  // ---- date periods (local time; the browser runs in the shop's time zone) ---------------------

  function ymd(d) {
    const p = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
  }

  /** From/to calendar dates (YYYY-MM-DD, both inclusive; '' = open) for today | week | month | all. */
  function periodDates(name) {
    const today = new Date();
    if (name === 'today') return { from: ymd(today), to: ymd(today) };
    if (name === 'week') {
      const monday = new Date(today);
      monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
      return { from: ymd(monday), to: ymd(today) };
    }
    if (name === 'month') return { from: ymd(new Date(today.getFullYear(), today.getMonth(), 1)), to: ymd(today) };
    return { from: '', to: '' };
  }

  /** Converts inclusive calendar dates into the API's ISO range: from = start of day, to = start of the next day. */
  function dateRange(fromDate, toDate) {
    const out = {};
    if (fromDate) out.from = new Date(fromDate + 'T00:00:00').toISOString();
    if (toDate) {
      const d = new Date(toDate + 'T00:00:00');
      d.setDate(d.getDate() + 1);
      out.to = d.toISOString();
    }
    return out;
  }

  /** ISO range for a named period. */
  function period(name) {
    const d = periodDates(name);
    return dateRange(d.from, d.to);
  }

  function clearFieldErrors(form) {
    form.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
    form.querySelectorAll('.invalid-feedback[data-generated]').forEach(el => el.remove());
  }

  /** errors = { fieldName: "validationKey" } → marks inputs and shows messages. */
  function setFieldErrors(form, errors) {
    clearFieldErrors(form);
    if (!errors) return;
    Object.keys(errors).forEach(name => {
      const input = form.querySelector('[name="' + name + '"]');
      if (!input) return;
      input.classList.add('is-invalid');
      const fb = document.createElement('div');
      fb.className = 'invalid-feedback d-block';
      fb.setAttribute('data-generated', '1');
      fb.textContent = VALIDATION[errors[name]] || errors[name];
      const anchor = input.closest('[data-error-anchor]') || input.closest('.input-group') || input;
      anchor.insertAdjacentElement('afterend', fb);
    });
  }

  function setLoading(btn, loading) {
    if (!btn) return;
    if (loading) {
      btn.dataset.originalHtml = btn.innerHTML;
      btn.disabled = true;
      btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>' + btn.innerHTML;
    } else {
      btn.disabled = false;
      if (btn.dataset.originalHtml !== undefined) btn.innerHTML = btn.dataset.originalHtml;
    }
  }

  function showError(err) {
    if (err && err.name === 'ApiError') toast(Api.messageFor(err), 'danger');
    else { console.error(err); toast('Something went wrong. Please try again.', 'danger'); }
  }

  /** Builds <option>s for a select. items: [{value,label}]. */
  function options(items, selected, emptyLabel) {
    const parts = [];
    if (emptyLabel) parts.push('<option value="">' + escapeHtml(emptyLabel) + '</option>');
    (items || []).forEach(it => {
      parts.push('<option value="' + escapeHtml(it.value) + '"' + (String(it.value) === String(selected) ? ' selected' : '') + '>' + escapeHtml(it.label) + '</option>');
    });
    return parts.join('');
  }

  return { escapeHtml, toast, confirm, modal, formValues, fillForm, money, number, date, dateTime, time, badge, stockBadge, thumb,
           options, clearFieldErrors, setFieldErrors, setLoading, showError, periodDates, dateRange, period, VALIDATION };
})();
