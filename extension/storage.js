// extension/storage.js — JWT + permission code persistence on chrome.storage.local.
//
// Why storage.local (not the volatile session-scoped storage area): EXT-01
// requires the JWT to survive a full browser restart. The session-scoped area
// is wiped when Chrome closes; `chrome.storage.local` is persistent
// (RESEARCH §Pitfall 5).
//
// This module is loaded as an ES module from popup.html (declare
// <script type="module" src="popup.js"></script>; popup.js will
// `import { getToken, setToken, clearToken, getCode, setCode } from './storage.js'`).
// Vanilla JS only — no build step.

const TOKEN_KEY = 'huasenToken';
const CODE_KEY = 'huasenCode';

/** Returns the stored JWT (raw string) or null. */
export function getToken() {
  return chrome.storage.local.get(TOKEN_KEY).then((o) => o[TOKEN_KEY] || null);
}

/** Persists the JWT (raw string from /user/login data.token). */
export function setToken(token) {
  return chrome.storage.local.set({ [TOKEN_KEY]: token });
}

/** Removes both the JWT and the permission code from local storage. */
export function clearToken() {
  return chrome.storage.local.remove([TOKEN_KEY, CODE_KEY]);
}

/** Returns the stored user permission code (Number) or null. */
export function getCode() {
  return chrome.storage.local.get(CODE_KEY).then((o) =>
    typeof o[CODE_KEY] === 'number' ? o[CODE_KEY] : null,
  );
}

/** Persists the user permission code (from /user/login data.code). */
export function setCode(code) {
  return chrome.storage.local.set({ [CODE_KEY]: code });
}
