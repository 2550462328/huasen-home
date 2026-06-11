# 花森导航收藏 — Chrome 扩展

> Manifest V3 popup-only 扩展。点击图标，登录花森后台账号（管理员），把当前页一键收藏到指定栏目。

**当前阶段：** Plan 11-01 已完成 — 提供清单 (manifest.json)、JWT 持久化层 (storage.js)、后端 API 客户端 (api.js)、占位图标和本测试脚本。Plan 11-02 起补齐 popup UI。

---

## 如何加载（开发者模式）

1. 启动后端：`cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`，确保 `http://localhost:8080` 可访问，且至少有一个权限 `code >= 3` 的管理员账号。
2. Chrome 地址栏访问 `chrome://extensions`，右上角打开 **开发者模式**。
3. 点击 **加载已解压的扩展程序**，选择本目录 `extension/`。
4. 确认条目为 `花森导航收藏 1.0.0`，**没有红色 manifest 错误**，**没有权限警告横幅**。
5. 锁定到工具栏（pin），便于点击。

> Plan 11-01 阶段 popup.html 尚未实现，点击图标会提示找不到 popup 文件 — 这是预期行为，不阻塞 manifest 加载。后续 plan 会补齐 UI。

---

## Wave 0 手动测试脚本

下面的清单覆盖 v1.2 全部需求 EXT-01..EXT-15。每条用例由 **Setup → Steps → Expect** 三段表达，可在浏览器里复现。**契约关键点**：后端 JWT 头名为 `token`（不是 `Authorization: Bearer`），过期/无效 token 返回 **403**（不是 401），登录失败返回 **400**。

| ID | 需求摘要 | Setup | Steps | Expect |
|----|----------|-------|-------|--------|
| EXT-01 | 登录持久化（chrome.storage.local 跨重启） | 后端启动；插件已加载 | 1) 打开任意 http(s) 页；2) 点图标 → 登录界面输入管理员账号密码；3) 完全退出 Chrome；4) 重新启动 Chrome；5) 再次点图标 | 直接进入收藏界面，**无需重新登录**（JWT 仍在 chrome.storage.local） |
| EXT-02 | popup 关闭后再开仍登录 | 已通过 EXT-01 登录 | 1) 关闭 popup；2) 立即重开 popup | 直接显示收藏界面，无登录提示 |
| EXT-03 | 403-with-token = 会话过期 → 清除 JWT 并跳登录 | 已登录 | 1) 在 `chrome://extensions` → 详情 → "服务工作进程"/"检查视图：popup" 打开 DevTools；2) Application → Storage → Extension storage → 修改 `huasenToken` 为乱码；3) 点保存 | 弹出 toast "登录已过期，请重新登录"，自动回到登录界面，`huasenToken` 已被清空 |
| EXT-04 | 抓取当前页 title / URL / favicon | 已登录 | 1) 任意 http(s) 网页（带 favicon）；2) 点图标 | 标题 / 网址 / favicon 自动填入；网址只读 |
| EXT-05 | 标题可编辑 | 同 EXT-04 | 编辑标题输入框 | 标题字段允许修改，保存时使用编辑后的值 |
| EXT-06 | 无效 favicon → icon 设为空 | 已登录 | 1) 在 `chrome://settings` 等内部页打开；2) 点图标 | favicon 字段为空（或显示占位地球图标），URL 仍正常抓取，**保存仍可成功**（后端 icon 可空） |
| EXT-07 | 拉取栏目列表 | 已登录 | 1) 进入收藏界面；2) 观察栏目下拉/选择器 | 栏目列表来自 `POST /column/findByCode`，仅显示当前账号可访问的 enabled 栏目 |
| EXT-08 | 栏目支持搜索过滤 | 同 EXT-07 | 在栏目搜索框输入子串 | 列表按 `name` 子串实时过滤；可上/下方向键 + Enter 选中 |
| EXT-09 | 一键收藏（创建站点 + 绑定栏目原子完成） | 已登录 + 选中某栏目 | 点击 "保存到花森导航" | 后端 `POST /site/quick-add` 200；返回站点 `_id`；栏目-站点关联同事务建立 |
| EXT-10 | 后端 quick-add 接口存在 | 后端运行 | `curl -X POST http://localhost:8080/site/quick-add -H 'Content-Type: application/json' -H 'token: <admin-jwt>' -d '{"name":"x","url":"https://x","icon":"","columnId":<id>}'` | HTTP 200，data 是 Site；**Plan 10 已交付** |
| EXT-11 | 非管理员收到 403 | 用 `code < 3` 的账号登录 | 同 EXT-09 步骤 | 登录阶段插件即提示 "该账号无收藏权限（需要管理员）"；若绕过该提示直接调 quick-add，后端返回 403 |
| EXT-12 | 成功 toast + Portal 可见 | 已登录 | 1) 收藏一条；2) 打开 Portal 导航首页；3) 刷新页面 | 收藏成功 toast；新链接出现在所选栏目下 |
| EXT-13 | 未登录点保存 → "请先登录" | 在 popup 内点 "退出登录" 后停留 | 触发保存动作（若 UI 暴露） | 显示 "请先登录"，回到登录界面 |
| EXT-14 | 后端错误 msg 原样展示，禁止 generic "保存失败" | 已登录 | 1) 把 URL 字段改成 `ftp://x`；2) 点保存 | toast 显示后端返回的 `msg`，例如 "URL必须以http://或https://开头" |
| EXT-15 | 保存中按钮禁用 + spinner 防双提交 | 已登录 | 在 DevTools Network 面板限速到 Slow 3G，点保存 | 按钮立即禁用并出现 spinner；fetch 完成前再次点击不会触发第二次请求 |

### 上下文需求

- **EXT-10 / EXT-11** 的后端实现来自 Phase 10（`SiteController.quickAdd`）。本扩展不重新实现，只调用契约。
- **后端契约**（再次锁定，避免 popup UI 写错）：
  - `POST /user/login` 公开，body `{id, password}`；成功 200 `{data:{token, code, ...}}`；失败 400。
  - `POST /column/findByCode` 需要 `token` 头，无 body；成功 200 `{data:[{_id, name, ...}]}`。
  - `POST /site/quick-add` 需要 `token` 头 + `huasenJWT_code >= 3`；body `{name, url, icon, columnId}`；403 = 过期或非管理员；400 = 参数校验失败。

---

## 文件结构（当前 plan 完成后）

```
extension/
├── manifest.json          # MV3 描述符（permissions、host_permissions、popup 入口）
├── storage.js             # chrome.storage.local 包装（getToken/setToken/clearToken/getCode/setCode）
├── api.js                 # 后端客户端（login / findByCode / quickAdd），统一 token 头与 403 处理
├── README.md              # 本文件
└── icons/
    ├── icon16.png         # 占位 #1e80ff 纯色（后续 plan 可替换为正式图标）
    ├── icon48.png
    └── icon128.png
```

**Plan 11-02 起补齐：** popup.html / popup.css / popup.js（vanilla JS 状态机 LOGIN → FORM → RESULT）。

---

## 安全说明（来自 11-RESEARCH §Security Domain + 本 plan threat model）

- **T-11-01** JWT 仅存于 `chrome.storage.local`（扩展源隔离），`api.js` 严禁打印 token 值。
- **T-11-02** 开发期 `API_BASE = http://localhost:8080`（loopback，可接受）；**生产部署必须切换为 HTTPS**，否则明文密码/JWT 会暴露。`api.js` 中 `API_BASE` 是唯一切换点。
- **T-11-04** `host_permissions` 限定单个后端 origin，不使用 `<all_urls>`；`permissions` 仅 `storage` + `activeTab`。
- **T-11-SC** 零 npm/外部包，无 slopsquatting 暴露面。

---

*生成日期：2026-06-10 · 适用范围：v1.2 milestone*
