// extension/api.js — Backend client for the popup.
//
// Single source of the backend contract:
//   - JWT lives in a request header named literally 'token' (lowercase, raw JWT,
//     NO scheme prefix). Do not use the standard HTTP auth header.
//     [Verified: backend/src/main/java/com/huasen/common/filter/JwtAuthFilter.java:49 +
//      frontend/portal/src/network/intercept.js:23]
//   - Plaintext application/json. RequestParamsFilter only RSA-decrypts when
//     body has secretMethod == 'rsa', so plaintext passes through untouched.
//   - 403-with-token == session expired (clear + force re-login). Backend never
//     returns 401 for the auth flow this extension uses; expired/invalid token
//     yields huasenJWT_code=0 → the admin guard returns 403.
//   - Login failure is HTTP 400 (not 401). Surface backend `msg` verbatim (EXT-14).
//
// IMPORTANT (T-11-02): API_BASE is the SINGLE prod-swap point.
// Dev: http://localhost:8080 is acceptable (loopback).
// Prod MUST be HTTPS — sending plaintext credentials/JWT over http:// in production
// is a HIGH-severity disclosure; also update manifest.json host_permissions to match.
//
// SECURITY (T-11-01): NEVER console.log the token value. The catch handlers below
// only log error messages, never request bodies or headers.

import { getToken, setToken, setCode, clearToken } from './storage.js';

export const API_BASE = 'http://localhost:8080';

/** Thrown when the backend returns 403 while a token was present (session expired/invalid). */
export class SessionExpiredError extends Error {
  constructor(message) {
    super(message);
    this.name = 'SessionExpiredError';
  }
}

/** Thrown for all other backend or network failures. `message` is suitable for direct UI display. */
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

const NETWORK_FAIL_MSG = '无法连接服务器，请检查网络或稍后重试。';
const SESSION_EXPIRED_MSG = '登录已过期，请重新登录';

async function safeJson(res) {
  try {
    return await res.json();
  } catch (_) {
    return {};
  }
}

/**
 * POST /user/login — public, no token.
 * @param {string} id       account
 * @param {string} password plaintext password
 * @returns {Promise<object>} body.data on success: { token, code, name, ... }
 * @throws {ApiError} on 400 (bad credentials) or missing code (code < 0).
 */
export async function login(id, password) {
  let res;
  try {
    res = await fetch(API_BASE + '/user/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, password }),
    });
  } catch (_) {
    throw new ApiError(NETWORK_FAIL_MSG);
  }
  const body = await safeJson(res);
  if (res.status !== 200) {
    throw new ApiError(body.msg || '登录失败', res.status);
  }
  // Backend /site/quick-add allows any logged-in user (code >= 0). Only reject
  // when the login response carries no usable code at all.
  if (!body.data || typeof body.data.code !== 'number' || body.data.code < 0) {
    throw new ApiError('该账号无收藏权限（需要登录）', 200);
  }
  await setToken(body.data.token);
  await setCode(body.data.code);
  return body.data;
}

/**
 * Internal: POST a JSON body with the token header attached. Returns the raw Response.
 */
async function authedPost(path, body) {
  const token = await getToken();
  let res;
  try {
    res = await fetch(API_BASE + path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Header key is exactly 'token', value is the raw JWT. Do NOT use the
        // standard HTTP auth-scheme header — backend filter only reads 'token'.
        token: token || '',
      },
      body: JSON.stringify(body || {}),
    });
  } catch (_) {
    throw new ApiError(NETWORK_FAIL_MSG);
  }
  return res;
}

/**
 * POST /column/findByCode — list columns the current user can save into.
 * Backend reads only HttpServletRequest; no body fields are required.
 * @returns {Promise<Array>} body.data — array of { _id, id, name, ... }
 * @throws {SessionExpiredError} on 403 with token (defensive: this endpoint has
 *         no admin guard, so 403 here would be unusual, but treat consistently).
 * @throws {ApiError} on other non-200.
 */
export async function findByCode() {
  const token = await getToken();
  const res = await authedPost('/column/findByCode', {});
  const body = await safeJson(res);
  if (res.status === 403) {
    if (token) {
      await clearToken();
      throw new SessionExpiredError(SESSION_EXPIRED_MSG);
    }
    throw new ApiError(body.msg || '权限不足', 403);
  }
  if (res.status !== 200) {
    throw new ApiError(body.msg || '获取栏目失败', res.status);
  }
  return body.data || [];
}

/**
 * POST /site/preview — crawl the target URL, return real favicon URL + AI 描述.
 * Non-blocking on the backend: failure fields come back as ''. Used to prefill the
 * FORM's 描述 textarea and the favicon preview before the user saves.
 * @param {string} url  the page URL to preview
 * @returns {Promise<{icon: string, description: string}>} body.data
 * @throws {SessionExpiredError} on 403 while a token was present (EXT-03 expiry).
 * @throws {ApiError} on other non-200.
 */
export async function preview(url) {
  const token = await getToken();
  const res = await authedPost('/site/preview', { url });
  const body = await safeJson(res);
  if (res.status === 403) {
    if (token) {
      await clearToken();
      throw new SessionExpiredError(SESSION_EXPIRED_MSG);
    }
    throw new ApiError(body.msg || '权限不足', 403);
  }
  if (res.status !== 200) {
    throw new ApiError(body.msg || '预览失败', res.status);
  }
  return body.data || { icon: '', description: '' };
}

/**
 * POST /site/quick-add — atomic create-site + bind-column (Phase 10).
 * @param {object} payload  { name, url, icon, description, columnId } — field names verbatim
 * @returns {Promise<object>} body.data — the created Site
 * @throws {SessionExpiredError} on 403 while a token was present (EXT-03 expiry).
 * @throws {ApiError} on 400 (validation) or other non-200; backend `msg` is surfaced
 *                    verbatim (EXT-14 — never a generic "保存失败").
 */
export async function quickAdd({ name, url, icon, description, columnId }) {
  const token = await getToken();
  const res = await authedPost('/site/quick-add', { name, url, icon, description, columnId });
  const body = await safeJson(res);
  if (res.status === 403) {
    if (token) {
      await clearToken();
      throw new SessionExpiredError(SESSION_EXPIRED_MSG);
    }
    throw new ApiError(body.msg || '权限不足', 403);
  }
  if (res.status !== 200) {
    throw new ApiError(body.msg || '保存失败', res.status);
  }
  return body.data;
}
