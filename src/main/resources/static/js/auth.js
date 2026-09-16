/* Session state for the signed-in administrator. */
window.Auth = (function () {
  let me = null;

  async function load() {
    me = await Api.get('/api/auth/me');
    return me;
  }

  async function logout() {
    try {
      await Api.post('/api/auth/logout', null, { silent: true });
    } catch (e) { /* session is gone either way */ }
    location.replace('/login.html');
  }

  return {
    load, logout,
    get me() { return me; },
    /** The administrator may do everything; workers sell and look. */
    get isAdmin() { return !!me && me.roles.indexOf('ADMIN') >= 0; }
  };
})();
