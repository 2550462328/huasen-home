// extension/popup.js — Popup state-machine controller (EXT-01..06).
//
// State machine: LOGIN ↔ FORM, with RESULT slot reserved for Plan 03.
//   - On DOMContentLoaded: getToken() → if present, enterForm(); else showLogin().
//   - LOGIN submit: api.login → on success enterForm(); on ApiError surface in #login-error.
//   - FORM enter: captureTab() prefills 标题 (editable, EXT-05), 网址 (read-only, EXT-04),
//                 favicon preview (EXT-06: chrome:// / data: / empty → '' + globe placeholder).
//   - FORM enter: loadColumns() is a hook stub Plan 03 fills; SessionExpiredError thrown by
//                 findByCode() routes us back to LOGIN with the expiry toast (EXT-03).
//   - Logout link: clearToken → showLogin (no confirm modal — non-destructive per UI-SPEC).
//
// Plan 03 surface (do not restructure this file when Plan 03 lands):
//   - module-scope `currentCapture` holds {title,url,icon} for the save flow to read.
//   - `loadColumns()` is the column-list hook (currently a no-op stub that calls findByCode
//     so the 403-with-token boot path is exercised end-to-end on FORM entry).
//   - `onSave()` is the submit hook (currently a no-op; #save-btn stays disabled).
//
// Security (T-11-01): never console.log credentials or the JWT.

import { login, findByCode, quickAdd, preview, SessionExpiredError, ApiError } from './api.js';
import { getToken, clearToken } from './storage.js';

// ===== DOM handles (resolved on DOMContentLoaded) ============================

let viewLogin;
let viewForm;
let viewResult;

let loginIdInput;
let loginPasswordInput;
let loginBtn;
let loginErrorLine;

let logoutLink;
let faviconPreview;
let siteTitleInput;
let siteUrlPill;
let siteDescriptionInput;
let descStatusEl;
let saveBtn;
let toastEl;

// Column combobox handles (Task 1).
let columnSearchInput;
let columnListEl;
let columnEmptyEl;
let emptyReloginLink;

// RESULT view handles.
let resultSiteName;
let againBtn;

// ===== Module-scope state (Plan 03 reads these) ==============================

/** {title, url, icon, description} captured from the active tab on FORM entry. icon='' when invalid. */
export const currentCapture = { title: '', url: '', icon: '', description: '' };

/** Full column list from findByCode() — { _id, id, name, ... }[]. */
let allColumns = [];
/** The bound column._id once a row is selected; null until then. */
let selectedColumnId = null;
/** Index of the keyboard-highlighted row within the currently-rendered (filtered) set. */
let activeRowIndex = -1;
/** The filtered subset currently rendered into #column-list. */
let renderedColumns = [];

let toastTimer = null;

// ===== View toggling =========================================================

export function showLogin() {
  viewLogin.hidden = false;
  viewForm.hidden = true;
  viewResult.hidden = true;
  // Move keyboard focus into the first login field for Enter-to-submit ergonomics.
  if (loginIdInput && !loginIdInput.disabled) loginIdInput.focus();
}

export function showForm() {
  viewLogin.hidden = true;
  viewForm.hidden = false;
  viewResult.hidden = true;
}

export function showResult(site) {
  viewLogin.hidden = true;
  viewForm.hidden = true;
  viewResult.hidden = false;
  // Saved site name in the confirmation caption (EXT-12). textContent only — never
  // innerHTML — so a crafted site name cannot inject markup into the popup (T-11-08).
  if (resultSiteName) {
    resultSiteName.textContent = site && site.name ? site.name : '';
  }
}

// ===== Tab capture (EXT-04/05/06) ============================================

/** Pure helper: only http(s) is treated as a fetchable favicon. chrome:// / data: / empty → false. */
export function validFavicon(u) {
  return !!u && /^https?:\/\//i.test(u);
}

/**
 * Read activeTab metadata and prefill the FORM. activeTab is in effect because
 * the popup was just invoked (RESEARCH Pitfall 3). Stores the resolved
 * capture on `currentCapture` for Plan 03's save flow.
 */
async function captureTab() {
  let tab = {};
  try {
    const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
    tab = tabs && tabs[0] ? tabs[0] : {};
  } catch (_) {
    // chrome.tabs may be unavailable in some test harnesses; fall through with empty defaults.
    tab = {};
  }

  const title = tab.title || '';
  const url = tab.url || '';
  const icon = validFavicon(tab.favIconUrl) ? tab.favIconUrl : '';

  currentCapture.title = title;
  currentCapture.url = url;
  currentCapture.icon = icon;

  // 标题 — editable (EXT-05), prefilled with captured page title.
  siteTitleInput.value = title;
  // 网址 — read-only pill (EXT-04); ellipsis + title attribute for full-URL hover.
  siteUrlPill.textContent = url;
  siteUrlPill.setAttribute('title', url);

  renderFaviconPreview(icon);
}

function renderFaviconPreview(icon) {
  // Reset the preview to the inline globe placeholder (EXT-06 default for empty/invalid).
  faviconPreview.innerHTML = '';
  if (icon) {
    const img = document.createElement('img');
    img.alt = '';
    img.src = icon;
    img.onerror = () => {
      // If the favicon URL turns out unreachable at render time, fall back to placeholder.
      renderPlaceholderGlyph();
    };
    faviconPreview.appendChild(img);
  } else {
    renderPlaceholderGlyph();
  }
}

function renderPlaceholderGlyph() {
  faviconPreview.innerHTML =
    '<svg class="placeholder-glyph" viewBox="0 0 24 24" width="20" height="20" ' +
    'fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" ' +
    'stroke-linejoin="round">' +
    '<circle cx="12" cy="12" r="9"/>' +
    '<path d="M3 12h18"/>' +
    '<path d="M12 3a14 14 0 0 1 0 18"/>' +
    '<path d="M12 3a14 14 0 0 0 0 18"/>' +
    '</svg>';
}

// ===== Toast (shared, EXT-12/14) =============================================

/**
 * Show a transient toast. variant: 'success' | 'error'.
 * Success auto-dismisses after ~2s; errors persist until next showToast/dismissed.
 */
export function showToast(variant, message) {
  if (!toastEl) return;
  if (toastTimer) {
    clearTimeout(toastTimer);
    toastTimer = null;
  }
  const isError = variant === 'error';

  // Build: single-color inline glyph (check / cross) + message text. The message
  // is set via textContent (T-11-08) so a crafted backend msg cannot inject markup.
  toastEl.innerHTML = '';
  const glyph = document.createElement('span');
  glyph.className = 'toast-glyph';
  glyph.setAttribute('aria-hidden', 'true');
  glyph.innerHTML = isError
    ? '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
      '<path d="M6 6l12 12"/><path d="M18 6L6 18"/></svg>'
    : '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
      '<path d="M5 12.5l4 4 10-11"/></svg>';
  const msgEl = document.createElement('span');
  msgEl.className = 'toast-msg';
  msgEl.textContent = message;
  toastEl.appendChild(glyph);
  toastEl.appendChild(msgEl);

  toastEl.classList.remove('is-success', 'is-error');
  toastEl.classList.add(isError ? 'is-error' : 'is-success');
  toastEl.hidden = false;
  if (!isError) {
    // Success auto-dismisses; errors persist until the next action.
    toastTimer = setTimeout(() => {
      toastEl.hidden = true;
      toastTimer = null;
    }, 2000);
  }
}

// ===== Loading lock helper (EXT-15 pattern) ==================================

function setBtnLoading(btn, loadingLabel) {
  if (!btn) return () => {};
  const labelEl = btn.querySelector('.btn-label');
  const originalLabel = labelEl ? labelEl.textContent : btn.textContent;
  btn.disabled = true;
  btn.classList.add('loading');
  if (labelEl) labelEl.textContent = loadingLabel;
  return function restore() {
    btn.disabled = false;
    btn.classList.remove('loading');
    if (labelEl) labelEl.textContent = originalLabel;
  };
}

// ===== LOGIN submit (EXT-01) =================================================

async function onLoginSubmit() {
  const id = loginIdInput.value.trim();
  const password = loginPasswordInput.value;
  loginErrorLine.textContent = '';

  if (!id || !password) {
    loginErrorLine.textContent = '请输入账号和密码。';
    return;
  }

  loginIdInput.disabled = true;
  loginPasswordInput.disabled = true;
  const restore = setBtnLoading(loginBtn, '登录中…');

  try {
    await login(id, password);
    // Reset password field on success — never keep the plaintext lying around in the DOM.
    loginPasswordInput.value = '';
    await enterForm();
  } catch (e) {
    if (e instanceof ApiError) {
      // ApiError messages are pre-formatted for direct UI display (api.js surfaces
      // backend `msg` verbatim or the non-admin pre-empt string).
      loginErrorLine.textContent = e.message || '账号或密码错误，请重试。';
    } else {
      loginErrorLine.textContent = '账号或密码错误，请重试。';
    }
  } finally {
    loginIdInput.disabled = false;
    loginPasswordInput.disabled = false;
    restore();
  }
}

// ===== FORM enter (capture + Plan-03 column hook) ============================

async function enterForm() {
  showForm();
  await captureTab();
  // Save stays disabled until a column is selected AND title is non-blank.
  if (saveBtn) saveBtn.disabled = true;
  // Reset the visible empty/list state before (re)loading.
  if (columnListEl) columnListEl.hidden = true;
  if (columnEmptyEl) columnEmptyEl.hidden = true;

  // Call preview to fetch AI description and real favicon.
  if (descStatusEl) {
    descStatusEl.textContent = '生成中…';
  }

  try {
    const previewData = await preview(currentCapture.url);
    // Success: fill description and render real favicon.
    if (siteDescriptionInput && previewData.description) {
      siteDescriptionInput.value = previewData.description;
    }
    if (previewData.icon) {
      renderFaviconPreview(previewData.icon);
      currentCapture.icon = previewData.icon;
    }
    currentCapture.description = previewData.description || '';
  } catch (e) {
    if (e instanceof SessionExpiredError) {
      // Same routing as loadColumns: clear token and return to LOGIN.
      try {
        await clearToken();
      } catch (_) {
        /* ignore */
      }
      showToast('error', '登录已过期，请重新登录。');
      showLogin();
      return;
    }
    // Other errors: fail silently, keep browser favicon and empty description.
  } finally {
    if (descStatusEl) {
      descStatusEl.textContent = '';
    }
  }

  try {
    await loadColumns();
  } catch (e) {
    if (e instanceof SessionExpiredError) {
      // api.js already called clearToken; defensive double-clear in case a future
      // refactor moves that responsibility.
      try {
        await clearToken();
      } catch (_) {
        /* ignore */
      }
      showToast('error', '登录已过期，请重新登录。');
      showLogin();
      return;
    }
    if (e instanceof ApiError) {
      showToast('error', e.message);
      return;
    }
    showToast('error', '无法连接服务器，请检查网络或稍后重试。');
  }
}

// ===== Column combobox (EXT-07/08) ==========================================

/**
 * Fetch the columns the current user can save into and render them into the list.
 *
 * findByCode() throws SessionExpiredError on 403-with-token (EXT-03). We let that
 * propagate to enterForm()'s catch which clears + routes back to LOGIN with the
 * expiry toast — so the combobox never renders against a dead session.
 */
export async function loadColumns() {
  // Reset combobox state on each (re)entry.
  selectedColumnId = null;
  activeRowIndex = -1;
  allColumns = [];
  renderedColumns = [];
  if (columnSearchInput) {
    columnSearchInput.value = '';
    columnSearchInput.setAttribute('aria-expanded', 'false');
  }

  const columns = await findByCode();
  allColumns = Array.isArray(columns) ? columns : [];

  if (allColumns.length === 0) {
    // Empty-columns case → show the empty state instead of an empty list.
    if (columnListEl) columnListEl.hidden = true;
    if (columnEmptyEl) columnEmptyEl.hidden = false;
  } else {
    if (columnEmptyEl) columnEmptyEl.hidden = true;
    renderColumnRows(allColumns);
  }

  updateSaveEnabled();
}

/** Case-insensitive substring filter on column.name (EXT-08). */
function filterColumns(query) {
  const q = (query || '').trim().toLowerCase();
  if (!q) return allColumns.slice();
  return allColumns.filter((c) => String(c.name || '').toLowerCase().includes(q));
}

/**
 * Render the given column subset into #column-list. Each row carries data-id =
 * column._id and shows column.name (textContent only — T-11-08, no innerHTML for
 * backend-supplied names). Re-applies the selected styling to the matching row.
 */
function renderColumnRows(columns) {
  renderedColumns = columns;
  activeRowIndex = -1;
  if (!columnListEl) return;

  columnListEl.innerHTML = '';

  if (columns.length === 0) {
    // No match for the current filter — keep the list visible but empty-of-rows.
    columnListEl.hidden = true;
    if (columnSearchInput) columnSearchInput.setAttribute('aria-expanded', 'false');
    return;
  }

  columns.forEach((col, idx) => {
    const li = document.createElement('li');
    li.className = 'column-row';
    li.setAttribute('role', 'option');
    li.dataset.id = col._id;
    li.dataset.index = String(idx);

    const nameSpan = document.createElement('span');
    nameSpan.className = 'row-name';
    nameSpan.textContent = col.name || ''; // textContent → no markup injection (T-11-08).
    li.appendChild(nameSpan);

    const check = document.createElement('span');
    check.className = 'row-check';
    check.setAttribute('aria-hidden', 'true');
    check.innerHTML =
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
      '<path d="M5 12.5l4 4 10-11"/></svg>';
    li.appendChild(check);

    if (col._id === selectedColumnId) {
      li.classList.add('is-selected');
      li.setAttribute('aria-selected', 'true');
    }

    li.addEventListener('click', () => selectColumn(col));
    li.addEventListener('mouseenter', () => setActiveRow(idx));

    columnListEl.appendChild(li);
  });

  columnListEl.hidden = false;
  if (columnSearchInput) columnSearchInput.setAttribute('aria-expanded', 'true');
}

/** Highlight a row for keyboard navigation (does not bind selection). */
function setActiveRow(idx) {
  activeRowIndex = idx;
  if (!columnListEl) return;
  const rows = columnListEl.querySelectorAll('.column-row');
  rows.forEach((row, i) => {
    row.classList.toggle('is-active', i === idx);
  });
  const active = rows[idx];
  if (active && typeof active.scrollIntoView === 'function') {
    active.scrollIntoView({ block: 'nearest' });
  }
}

/** Bind the chosen column: set selectedColumnId, mirror the name into the search box. */
function selectColumn(col) {
  selectedColumnId = col._id;
  if (columnSearchInput) columnSearchInput.value = col.name || '';
  // Re-render so the selected row carries the accent styling; collapse the list.
  if (columnListEl) {
    columnListEl.hidden = true;
    const rows = columnListEl.querySelectorAll('.column-row');
    rows.forEach((row) => {
      // row.dataset.id is always a string; selectedColumnId holds col._id (may be
      // numeric) — coerce so the selected-row styling actually matches (WR-02).
      const isSel = row.dataset.id === String(selectedColumnId);
      row.classList.toggle('is-selected', isSel);
      if (isSel) row.setAttribute('aria-selected', 'true');
      else row.removeAttribute('aria-selected');
    });
  }
  if (columnSearchInput) columnSearchInput.setAttribute('aria-expanded', 'false');
  updateSaveEnabled();
}

function openColumnList() {
  if (allColumns.length === 0) return; // empty-state stays shown
  renderColumnRows(filterColumns(columnSearchInput ? columnSearchInput.value : ''));
}

function closeColumnList() {
  if (columnListEl) columnListEl.hidden = true;
  activeRowIndex = -1;
  if (columnSearchInput) columnSearchInput.setAttribute('aria-expanded', 'false');
}

/** Wire the search input: filtering (input), keyboard nav (↑/↓/Enter/Esc), focus. */
function wireColumnCombobox() {
  if (!columnSearchInput) return;

  columnSearchInput.addEventListener('input', () => {
    // Typing changes the query → unbind any prior selection so save reflects intent.
    selectedColumnId = null;
    updateSaveEnabled();
    if (allColumns.length === 0) return;
    renderColumnRows(filterColumns(columnSearchInput.value));
  });

  columnSearchInput.addEventListener('focus', openColumnList);

  columnSearchInput.addEventListener('keydown', (evt) => {
    if (allColumns.length === 0) return;
    if (evt.key === 'ArrowDown') {
      evt.preventDefault();
      if (columnListEl && columnListEl.hidden) openColumnList();
      if (renderedColumns.length === 0) return;
      const next = activeRowIndex + 1 >= renderedColumns.length ? 0 : activeRowIndex + 1;
      setActiveRow(next);
    } else if (evt.key === 'ArrowUp') {
      evt.preventDefault();
      if (renderedColumns.length === 0) return;
      const prev = activeRowIndex - 1 < 0 ? renderedColumns.length - 1 : activeRowIndex - 1;
      setActiveRow(prev);
    } else if (evt.key === 'Enter') {
      if (activeRowIndex >= 0 && activeRowIndex < renderedColumns.length) {
        evt.preventDefault();
        selectColumn(renderedColumns[activeRowIndex]);
      }
    } else if (evt.key === 'Escape') {
      closeColumnList();
    }
  });

  // Click outside the combobox collapses the list.
  document.addEventListener('click', (evt) => {
    const section = document.getElementById('column-section');
    if (section && !section.contains(evt.target)) closeColumnList();
  });
}

/**
 * Enable #save-btn only when a column is selected AND the title is non-blank
 * (UI-SPEC FORM footer rule). Client-side UX guard only — authorization is
 * server-enforced (T-11-07).
 */
function updateSaveEnabled() {
  if (!saveBtn) return;
  const titleOk = !!(siteTitleInput && siteTitleInput.value.trim());
  // null is the unselected sentinel — use an explicit null check, not truthiness,
  // so a legitimate column whose _id === 0 still enables save (WR-03).
  saveBtn.disabled = !(selectedColumnId !== null && titleOk);
}

// ===== Save flow (EXT-09/12/15) + typed-error matrix (EXT-13/14/03) ==========

/**
 * Save the captured page as a bookmark via /site/quick-add.
 *   - Not-logged-in guard (EXT-13): no token → 请先登录。 + LOGIN.
 *   - Loading lock (EXT-15): button → 保存中…, disabled, .loading; restored in finally.
 *   - Success (EXT-12): 收藏成功 toast → RESULT view with the saved site name.
 *   - SessionExpiredError (403-with-token, EXT-03): 登录已过期 toast → LOGIN.
 *   - ApiError (EXT-14): surface backend msg verbatim (e.g. 名称不能为空 /
 *     URL必须以http://或https://开头 / 栏目不存在); fall back to a generic message only
 *     when the backend msg is empty.
 */
async function onSave() {
  // Guard: not logged in (EXT-13). storage.getToken resolves the persisted JWT.
  const token = await getToken();
  if (!token) {
    showToast('error', '请先登录。');
    showLogin();
    return;
  }

  // Loading lock (EXT-15) — prevents double-submit; restore() runs in finally.
  const restore = setBtnLoading(saveBtn, '保存中…');

  try {
    const payload = {
      name: siteTitleInput ? siteTitleInput.value.trim() : '',
      url: currentCapture.url,
      icon: currentCapture.icon,
      description: siteDescriptionInput ? siteDescriptionInput.value.trim() : '',
      columnId: selectedColumnId,
    };
    const site = await quickAdd(payload);
    showToast('success', '收藏成功');
    showResult(site);
  } catch (e) {
    if (e instanceof SessionExpiredError) {
      // api.js already cleared storage on the 403-with-token path (EXT-03).
      showToast('error', '登录已过期，请重新登录。');
      showLogin();
      return;
    }
    if (e instanceof ApiError) {
      // Surface the backend msg verbatim (EXT-14) — never a bare generic on its own.
      showToast('error', e.message || '保存失败，请稍后重试。');
      return;
    }
    showToast('error', '无法连接服务器，请检查网络或稍后重试。');
  } finally {
    restore();
  }
}

/**
 * RESULT → 继续收藏 (EXT-12 secondary action): return to FORM and re-capture the
 * now-active tab, then reload columns. loadColumns() already resets selectedColumnId
 * and the search box on entry. enterForm() re-runs captureTab() + loadColumns() and
 * owns the SessionExpiredError/network routing.
 */
async function onAgain() {
  await enterForm();
}

// ===== Logout (EXT-02 affordance) ============================================

async function onLogout(evt) {
  if (evt) evt.preventDefault();
  await clearToken();
  // Reset capture so the next login session starts clean.
  currentCapture.title = '';
  currentCapture.url = '';
  currentCapture.icon = '';
  showLogin();
}

// ===== Boot routing (EXT-02) =================================================

async function boot() {
  // Resolve DOM handles once.
  viewLogin = document.getElementById('view-login');
  viewForm = document.getElementById('view-form');
  viewResult = document.getElementById('view-result');

  loginIdInput = document.getElementById('login-id');
  loginPasswordInput = document.getElementById('login-password');
  loginBtn = document.getElementById('login-btn');
  loginErrorLine = document.getElementById('login-error');

  logoutLink = document.getElementById('logout-link');
  faviconPreview = document.getElementById('favicon-preview');
  siteTitleInput = document.getElementById('site-title');
  siteUrlPill = document.getElementById('site-url');
  siteDescriptionInput = document.getElementById('site-description');
  descStatusEl = document.getElementById('desc-status');
  saveBtn = document.getElementById('save-btn');
  toastEl = document.getElementById('toast');

  columnSearchInput = document.getElementById('column-search');
  columnListEl = document.getElementById('column-list');
  columnEmptyEl = document.getElementById('column-empty');
  emptyReloginLink = document.getElementById('empty-relogin-link');

  resultSiteName = document.getElementById('result-site-name');
  againBtn = document.getElementById('again-btn');

  // Wire LOGIN events — click + Enter on either input (EXT-01 ergonomics).
  loginBtn.addEventListener('click', onLoginSubmit);
  const onEnter = (evt) => {
    if (evt.key === 'Enter') {
      evt.preventDefault();
      onLoginSubmit();
    }
  };
  loginIdInput.addEventListener('keydown', onEnter);
  loginPasswordInput.addEventListener('keydown', onEnter);

  // Wire logout.
  logoutLink.addEventListener('click', onLogout);

  // Wire the empty-state re-login escape hatch (WR-01) — same as logout: clear the
  // (possibly expired) session and route to LOGIN.
  if (emptyReloginLink) emptyReloginLink.addEventListener('click', onLogout);

  // Wire the column combobox (filter/keyboard/select).
  wireColumnCombobox();

  // Title edits re-evaluate the save-enabled rule (EXT-08 footer rule).
  if (siteTitleInput) {
    siteTitleInput.addEventListener('input', updateSaveEnabled);
  }

  // Wire the save flow (EXT-09) + RESULT secondary action (EXT-12).
  if (saveBtn) saveBtn.addEventListener('click', onSave);
  if (againBtn) againBtn.addEventListener('click', onAgain);

  // Persistence-aware boot: stored JWT → straight to FORM (EXT-02).
  const token = await getToken();
  if (token) {
    await enterForm();
  } else {
    showLogin();
  }
}

// DOMContentLoaded — popup.html declares <script type="module" src="popup.js">,
// modules are deferred by default, so DOM is parsed before this runs. Wrap in
// the listener anyway for defensive correctness.
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot, { once: true });
} else {
  boot();
}
