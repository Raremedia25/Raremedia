/* fetch() wrapper for the THEO TECH API.
 *  - sends the XSRF-TOKEN cookie back as X-XSRF-TOKEN on every non-GET request
 *  - unwraps the { success, data, code, message, errors } envelope
 *  - throws ApiError with a stable `code` the UI turns into a message
 *  - 401 → redirect to login (except on the login call itself)
 *  - 403 → toast, never a redirect
 *  - non-GET requests are never retried: a retried POST /api/sales would be a duplicate sale
 */
window.Api = (function () {
  class ApiError extends Error {
    constructor(status, code, message, errors) {
      super(message || code);
      this.name = 'ApiError';
      this.status = status;
      this.code = code || ('HTTP_' + status);
      this.errors = errors || null;
    }
  }

  const MESSAGES = {
    UNKNOWN: 'Something went wrong. Please try again.',
    NETWORK: 'Cannot reach the server. Check the connection and do not re-enter this operation.',
    UNAUTHENTICATED: 'Your session has expired. Please sign in again.',
    BAD_CREDENTIALS: 'Incorrect username or password.',
    ACCOUNT_LOCKED: 'This account is temporarily locked after too many failed attempts. Try again in 15 minutes.',
    ACCOUNT_DISABLED: 'This account has been disabled.',
    AUTH_FAILED: 'Sign-in failed.',
    CSRF_INVALID: 'Security token expired. Reload the page and try again.',
    FORBIDDEN: 'You do not have permission to do that.',
    PASSWORD_CHANGE_REQUIRED: 'You must change your password before continuing.',
    VALIDATION_FAILED: 'Please correct the highlighted fields.',
    MALFORMED_REQUEST: 'The request was not understood.',
    NOT_FOUND: 'The requested item was not found.',
    METHOD_NOT_ALLOWED: 'This operation is not allowed.',
    STALE_RECORD: 'Someone else changed this record. Reload and try again.',
    DATA_INTEGRITY: 'This operation conflicts with existing data.',
    INTERNAL_ERROR: 'Internal error. Please try again or contact the administrator.',
    DUPLICATE_PRODUCT: 'A product with that name already exists.',
    DUPLICATE_CATEGORY: 'A category with that name already exists.',
    CATEGORY_IN_USE: 'This category still has products. Move or delete them first.',
    INSUFFICIENT_STOCK: 'Insufficient stock.',
    DUPLICATE_USERNAME: 'That username is already taken.'
  };

  function cookie(name) {
    const m = document.cookie.match('(?:^|; )' + name.replace(/[.$?*|{}()[\]\\/+^]/g, '\\$&') + '=([^;]*)');
    return m ? decodeURIComponent(m[1]) : null;
  }

  function clean(obj) {
    const out = {};
    Object.keys(obj || {}).forEach(k => {
      const v = obj[k];
      if (v !== undefined && v !== null && v !== '') out[k] = v;
    });
    return out;
  }

  function goToLogin() {
    if (location.pathname.endsWith('/login.html')) return;
    const next = encodeURIComponent(location.pathname + location.search);
    location.replace('/login.html?next=' + next);
  }

  async function request(method, url, opts) {
    opts = opts || {};
    const init = {
      method,
      credentials: 'same-origin',
      headers: Object.assign({ 'Accept': 'application/json' }, opts.headers || {})
    };
    if (method !== 'GET' && method !== 'HEAD') {
      const xsrf = cookie('XSRF-TOKEN');
      if (xsrf) init.headers['X-XSRF-TOKEN'] = xsrf;
    }
    if (opts.form) {
      init.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
      init.body = new URLSearchParams(opts.form).toString();
    } else if (opts.formData) {
      init.body = opts.formData; // the browser sets the multipart boundary
    } else if (opts.body !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(opts.body);
    }
    if (opts.query) {
      const qs = new URLSearchParams(clean(opts.query)).toString();
      if (qs) url += (url.includes('?') ? '&' : '?') + qs;
    }

    let res;
    try {
      res = await fetch(url, init);
    } catch (e) {
      throw new ApiError(0, 'NETWORK', e.message);
    }

    let payload = null;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('json')) {
      try { payload = await res.json(); } catch (e) { payload = null; }
    }

    if (res.status === 401 && !url.startsWith('/api/auth/login')) {
      goToLogin();
      throw new ApiError(401, 'UNAUTHENTICATED');
    }

    if (!res.ok || (payload && payload.success === false)) {
      const err = new ApiError(res.status, payload && payload.code, payload && payload.message, payload && payload.errors);
      if (err.code === 'PASSWORD_CHANGE_REQUIRED' && !location.pathname.endsWith('/change-password.html')) {
        location.replace('/change-password.html');
      } else if (res.status === 403 && !opts.silent && window.UI) {
        UI.toast(messageFor(err), 'danger');
      }
      throw err;
    }
    return payload ? payload.data : null;
  }

  /** Human message for an error. */
  function messageFor(err) {
    if (!err) return MESSAGES.UNKNOWN;
    if (err.code === 'INSUFFICIENT_STOCK' && err.errors && err.errors.available !== undefined) {
      const n = Number(err.errors.available);
      return 'Insufficient stock. Only ' + n + ' unit' + (n === 1 ? '' : 's') + ' available.';
    }
    return MESSAGES[err.code] || MESSAGES.UNKNOWN;
  }

  return {
    ApiError,
    messageFor,
    get: (url, opts) => request('GET', url, opts),
    post: (url, body, opts) => request('POST', url, Object.assign({}, opts, { body })),
    put: (url, body, opts) => request('PUT', url, Object.assign({}, opts, { body })),
    del: (url, opts) => request('DELETE', url, opts),
    postForm: (url, form, opts) => request('POST', url, Object.assign({}, opts, { form })),
    upload: (url, formData, opts) => request('POST', url, Object.assign({}, opts, { formData }))
  };
})();
