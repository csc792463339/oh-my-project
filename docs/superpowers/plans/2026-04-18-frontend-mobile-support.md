# 前端 Web 移动端支持 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `frontend/` 的聊天页（`/`）与后台管理（`/admin/*`）在手机（≤ 768px）上可用且体验良好，桌面端视觉与交互保持不变。

**Architecture:** 纯 CSS 媒体查询（单一断点 `@media (max-width: 768px)`）集中追加在 `src/styles.css` 末尾，零新增 runtime 依赖。`AdminLayout.tsx` 新增汉堡菜单 + 抽屉所需的最小 React 状态。后台表格通过给 `<td>` 加 `data-label` 属性 + CSS 转卡片布局。引入 `@playwright/test` 做 desktop/mobile 双 project 的 E2E 回归测试。

**Tech Stack:** React 18 · Vite 5 · TypeScript 5 · react-router-dom 6 · CSS 原生 `@media` · `@playwright/test`

**Spec:** `docs/superpowers/specs/2026-04-18-frontend-mobile-support-design.md`

---

## 文件结构总览

**新建：**
- `frontend/playwright.config.ts` — Playwright 配置，定义 desktop + mobile 两个 project
- `frontend/tests/e2e/chat.mobile.spec.ts` — 聊天页移动端用例
- `frontend/tests/e2e/admin.mobile.spec.ts` — 后台移动端用例（依赖后端时需 `E2E_ADMIN=1`）
- `frontend/tests/e2e/admin.desktop.spec.ts` — 后台桌面端冒烟

**修改：**
- `frontend/src/styles.css` — 末尾追加 "Mobile Adaptations" 区块
- `frontend/src/pages/admin/AdminLayout.tsx` — 加 `navOpen` state、topbar、backdrop、路由变化自动关闭
- `frontend/src/pages/admin/AdminProjectsPage.tsx` — `<td>` 补 `data-label`
- `frontend/src/pages/admin/AdminSessionsPage.tsx` — `<td>` 补 `data-label`
- `frontend/package.json` — 加 `@playwright/test` devDependency 与 scripts
- `frontend/.gitignore`（若不存在则新建）— 排除 Playwright 产物

**零改动：** `ChatPage.tsx`、`AttachmentChip.tsx`、`MarkdownView.tsx`、`FileModal.tsx`、`StatusIndicator.tsx`、`AdminLoginPage.tsx`（仅靠 CSS 调整）、路由、API、后端。

---

## Task 1: Playwright 基础设施

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/playwright.config.ts`
- Create: `frontend/.gitignore`（若已存在则 modify）
- Create: `frontend/tests/e2e/smoke.spec.ts`（临时，验证环境）

- [ ] **Step 1.1：安装 Playwright**

```bash
cd frontend
npm install --save-dev @playwright/test
npx playwright install chromium webkit
```

期望：`package.json` 的 `devDependencies` 出现 `"@playwright/test": "^1.x"`，浏览器下载到 `~/Library/Caches/ms-playwright/`。

- [ ] **Step 1.2：在 `frontend/package.json` 添加 scripts**

将 `scripts` 字段修改为：

```json
"scripts": {
  "dev": "vite",
  "build": "vite build",
  "preview": "vite preview",
  "test:e2e": "playwright test",
  "test:e2e:ui": "playwright test --ui"
}
```

- [ ] **Step 1.3：创建 `frontend/playwright.config.ts`**

```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 720 } },
    },
    {
      name: 'mobile',
      use: { ...devices['iPhone 13'] },
    },
  ],
});
```

- [ ] **Step 1.4：创建 `frontend/.gitignore`（或追加）**

如果 `frontend/.gitignore` 不存在，新建；存在则在末尾追加：

```
# Playwright
/test-results/
/playwright-report/
/.playwright/
/blob-report/
/playwright/.cache/
```

- [ ] **Step 1.5：创建 `frontend/tests/e2e/smoke.spec.ts` 验证环境**

```ts
import { test, expect } from '@playwright/test';

test('聊天首页可以打开', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Oh My Project/);
});
```

- [ ] **Step 1.6：运行 smoke 测试**

```bash
cd frontend
npm run test:e2e -- smoke
```

期望：两个 project（desktop / mobile）各跑一次，全部 PASS。

- [ ] **Step 1.7：Commit**

```bash
git add frontend/package.json frontend/package-lock.json \
        frontend/playwright.config.ts frontend/.gitignore \
        frontend/tests/e2e/smoke.spec.ts
git commit -m "test: add playwright with desktop and mobile projects"
```

---

## Task 2: 聊天页移动端 E2E 用例（TDD · 先写会失败的测试）

**Files:**
- Create: `frontend/tests/e2e/chat.mobile.spec.ts`
- Delete: `frontend/tests/e2e/smoke.spec.ts`（smoke 使命完成）

- [ ] **Step 2.1：写 `chat.mobile.spec.ts`（此时会失败，因为 CSS 还没写）**

```ts
import { test, expect } from '@playwright/test';

test.describe('聊天页 · 移动端', () => {
  test.skip(({ browserName }) => browserName === 'chromium' && !test.info().project.name.includes('mobile'),
    '仅 mobile project 运行');

  test('页面无横向滚动', async ({ page }) => {
    await page.goto('/');
    const { scrollWidth, clientWidth } = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth);
  });

  test('composer 固定在底部', async ({ page }) => {
    await page.goto('/');
    const composer = page.locator('.composer');
    await expect(composer).toBeVisible();
    const box = await composer.boundingBox();
    const viewport = page.viewportSize()!;
    expect(box).not.toBeNull();
    // composer 底边应贴近视口底部（允许 safe-area + 少量误差）
    expect(box!.y + box!.height).toBeGreaterThan(viewport.height - 80);
  });

  test('发送与附件按钮满足 44×44 触控命中', async ({ page }) => {
    await page.goto('/');
    const sendOrAttach = page.locator('.composer button').first();
    await expect(sendOrAttach).toBeVisible();
    const box = await sendOrAttach.boundingBox();
    expect(box!.width).toBeGreaterThanOrEqual(44);
    expect(box!.height).toBeGreaterThanOrEqual(44);
  });

  test('输入并发送文本时消息气泡出现', async ({ page }) => {
    await page.goto('/');
    const textarea = page.locator('.composer textarea');
    await textarea.fill('你好，移动端测试');
    const send = page.locator('.composer button', { hasText: /发送|Send/i }).first();
    // 若按钮文案不匹配则回退到最后一个按钮
    const btn = (await send.count()) > 0 ? send : page.locator('.composer button').last();
    await btn.click();
    await expect(page.locator('.bubble.user')).toContainText('你好，移动端测试', { timeout: 10_000 });
  });
});
```

- [ ] **Step 2.2：删除 smoke**

```bash
rm frontend/tests/e2e/smoke.spec.ts
```

- [ ] **Step 2.3：运行测试确认部分失败**

```bash
cd frontend
npm run test:e2e -- --project=mobile chat.mobile
```

期望：`无横向滚动`、`composer 固定在底部`、`触控命中 44×44` 三条至少一条 FAIL（因为目前 CSS 没有移动端规则）。`发送消息` 在无后端时也可能失败，本地跑可先放过（后续完善）。记录实际失败项作为改造目标。

---

## Task 3: 聊天页移动端 CSS（让 Task 2 的测试通过）

**Files:**
- Modify: `frontend/src/styles.css`（在末尾追加）

- [ ] **Step 3.1：在 `styles.css` 末尾追加 "Mobile Adaptations" 总区块骨架**

在文件末尾新增：

```css
/* ============================================================
   Mobile Adaptations (≤ 768px)
   ============================================================ */
:root {
  --safe-top: env(safe-area-inset-top, 0px);
  --safe-bottom: env(safe-area-inset-bottom, 0px);
  --safe-left: env(safe-area-inset-left, 0px);
  --safe-right: env(safe-area-inset-right, 0px);
}

@media (max-width: 768px) {
  html, body {
    /* 使用 dvh 规避 iOS 键盘 viewport 抖动 */
    min-height: 100vh;
    min-height: 100dvh;
  }

  /* --- 聊天页：布局 --- */
  .layout { flex-direction: column; }
  .sidebar { display: none; }
  .main { width: 100%; padding: 0; }

  .messages {
    padding: 12px 12px 140px;
  }
  .messages-inner {
    max-width: 100%;
  }

  .bubble {
    max-width: 86% !important;
  }

  /* --- 聊天页：composer 底部固定 --- */
  .composer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    padding: 10px 12px calc(10px + var(--safe-bottom));
    background: var(--bg);
    border-top: 1px solid var(--border);
  }
  .composer-inner {
    max-width: 100%;
  }
  .composer textarea {
    font-size: 16px; /* 防 iOS 自动缩放 */
  }
  .composer button {
    min-width: 44px;
    min-height: 44px;
  }
  .composer-hint { display: none; }

  /* --- 聊天页：附件 chip --- */
  .attachment-chip {
    max-width: calc(50vw - 24px);
  }
  .attachment-chip.image {
    width: 56px;
    height: 56px;
  }

  /* --- 聊天页：Markdown 代码块横向滚动 --- */
  .bubble pre,
  .bubble code {
    -webkit-overflow-scrolling: touch;
  }
}
```

- [ ] **Step 3.2：运行聊天页移动端测试**

```bash
cd frontend
npm run test:e2e -- --project=mobile chat.mobile
```

期望：`无横向滚动`、`composer 固定在底部`、`触控命中 44×44` 三条全部 PASS。`发送消息` 若本地无后端支持，允许继续失败或手动 skip；若测试环境有 mock，应 PASS。

- [ ] **Step 3.3：手动视觉验证（桌面端回归）**

```bash
cd frontend
npm run dev
```

在桌面浏览器打开 `http://localhost:5173`，确认：
- 聊天页与改造前完全一致（sidebar 可见、composer 非 fixed、消息 `max-width: 760px` 约束仍在）。
- 用 DevTools 切换到 iPhone 13 viewport，确认 sidebar 消失、composer 固定底部、不被 home indicator 遮挡。

- [ ] **Step 3.4：Commit**

```bash
git add frontend/src/styles.css frontend/tests/e2e/chat.mobile.spec.ts
git commit -m "feat(mobile): adapt chat page for phones ≤ 768px"
```

---

## Task 4: AdminLayout 抽屉化（TDD · 先补测试失败）

**Files:**
- Create: `frontend/tests/e2e/admin.desktop.spec.ts`
- Create: `frontend/tests/e2e/admin.mobile.spec.ts`
- Modify: `frontend/src/pages/admin/AdminLayout.tsx`
- Modify: `frontend/src/styles.css`（在 `@media (max-width: 768px)` 区块内追加）

### Step 4A：写后台桌面端冒烟（防回归）

- [ ] **Step 4.1：创建 `frontend/tests/e2e/admin.desktop.spec.ts`**

```ts
import { test, expect } from '@playwright/test';

test.describe('后台 · 桌面端冒烟', () => {
  test.skip(({}, testInfo) => testInfo.project.name !== 'desktop', '仅 desktop project 运行');
  test.skip(!process.env.E2E_ADMIN, '需 E2E_ADMIN=1 且 admin 已登录/可登录才能跑');

  test('侧边栏常驻、汉堡按钮不可见', async ({ page }) => {
    await page.goto('/admin/projects');
    await expect(page.locator('.admin-layout nav')).toBeVisible();
    await expect(page.locator('.admin-topbar')).toBeHidden();
    await expect(page.locator('.admin-layout main table thead')).toBeVisible();
  });
});
```

### Step 4B：写后台移动端用例（覆盖抽屉 + 表格转卡片）

- [ ] **Step 4.2：创建 `frontend/tests/e2e/admin.mobile.spec.ts`**

```ts
import { test, expect } from '@playwright/test';

test.describe('后台 · 移动端', () => {
  test.skip(({}, testInfo) => testInfo.project.name !== 'mobile', '仅 mobile project 运行');
  test.skip(!process.env.E2E_ADMIN, '需 E2E_ADMIN=1 且 admin 已登录/可登录才能跑');

  test('汉堡可打开与关闭抽屉', async ({ page }) => {
    await page.goto('/admin/projects');
    const nav = page.locator('.admin-layout nav');
    const hamburger = page.locator('.admin-topbar [data-testid="nav-toggle"]');
    const backdrop = page.locator('.admin-backdrop');

    await expect(hamburger).toBeVisible();
    // 默认关闭：抽屉左边界在视口外
    const closedBox = await nav.boundingBox();
    expect(closedBox!.x).toBeLessThan(0);

    await hamburger.click();
    await expect(backdrop).toBeVisible();
    const openBox = await nav.boundingBox();
    expect(openBox!.x).toBeGreaterThanOrEqual(0);

    await backdrop.click();
    await expect(backdrop).toBeHidden();
    const closedAgain = await nav.boundingBox();
    expect(closedAgain!.x).toBeLessThan(0);
  });

  test('点击 NavLink 后抽屉自动关闭并跳转', async ({ page }) => {
    await page.goto('/admin/projects');
    await page.locator('.admin-topbar [data-testid="nav-toggle"]').click();
    await page.locator('.admin-layout nav a', { hasText: '会话列表' }).click();
    await expect(page).toHaveURL(/\/admin\/sessions/);
    await expect(page.locator('.admin-backdrop')).toBeHidden();
  });

  test('页面无横向滚动', async ({ page }) => {
    await page.goto('/admin/projects');
    const { scrollWidth, clientWidth } = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth);
  });

  test('表格以卡片形式渲染', async ({ page }) => {
    await page.goto('/admin/projects');
    const thead = page.locator('.admin-layout main table thead');
    await expect(thead).toBeHidden();
    const tr = page.locator('.admin-layout main table tbody tr').first();
    const display = await tr.evaluate((el) => getComputedStyle(el).display);
    expect(display).toBe('block');
  });
});
```

- [ ] **Step 4.3：运行测试，确认 mobile 用例失败**

```bash
cd frontend
E2E_ADMIN=1 npm run test:e2e -- admin
```

期望：`admin.mobile` 的 4 条用例全部 FAIL（找不到 `.admin-topbar`、`[data-testid="nav-toggle"]`、`.admin-backdrop`）。若当前环境无 admin 后端，用例会 skip — 那就先跳过验证，直接进入实现（后续在 Step 4.7 再跑）。

### Step 4C：修改 `AdminLayout.tsx`

- [ ] **Step 4.4：修改 `frontend/src/pages/admin/AdminLayout.tsx` 全文替换为：**

```tsx
import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';

export default function AdminLayout() {
  const nav = useNavigate();
  const location = useLocation();
  const [checked, setChecked] = useState(false);
  const [navOpen, setNavOpen] = useState(false);

  useEffect(() => {
    api.adminMe().then(() => setChecked(true)).catch(() => nav('/admin/login'));
  }, [nav]);

  useEffect(() => {
    setNavOpen(false);
  }, [location.pathname]);

  async function logout() {
    await api.adminLogout();
    nav('/admin/login');
  }

  if (!checked) return null;

  return (
    <div className="admin-layout">
      <div className="admin-topbar">
        <button
          type="button"
          className="admin-nav-toggle"
          data-testid="nav-toggle"
          aria-label="打开导航"
          onClick={() => setNavOpen(true)}
        >
          <span aria-hidden>☰</span>
        </button>
        <div className="admin-topbar-title">后台</div>
      </div>

      <nav className={navOpen ? 'open' : ''}>
        <div className="brand">Oh My Project 后台</div>
        <NavLink to="/admin/projects" className={({ isActive }) => (isActive ? 'active' : '')}>
          项目管理
        </NavLink>
        <NavLink to="/admin/sessions" className={({ isActive }) => (isActive ? 'active' : '')}>
          会话列表
        </NavLink>
        <div style={{ marginTop: 'auto', paddingTop: 20 }}>
          <Link to="/">← 返回聊天</Link>
          <button className="btn secondary" onClick={logout} style={{ marginTop: 12, width: '100%' }}>
            退出登录
          </button>
        </div>
      </nav>

      {navOpen && (
        <div
          className="admin-backdrop"
          role="presentation"
          onClick={() => setNavOpen(false)}
        />
      )}

      <main>
        <Outlet />
      </main>
    </div>
  );
}
```

### Step 4D：在 `styles.css` 末尾的移动端块中追加 admin 样式

- [ ] **Step 4.5：在 `styles.css` 的 `@media (max-width: 768px) { ... }` 块内（Task 3 已创建）追加：**

```css
  /* --- 后台：抽屉与顶部条 --- */
  .admin-layout {
    flex-direction: column;
    height: 100dvh;
  }
  .admin-topbar {
    display: flex;
    align-items: center;
    gap: 12px;
    height: 48px;
    padding: 0 12px calc(0px + var(--safe-top));
    background: var(--surface);
    border-bottom: 1px solid var(--border);
    position: sticky;
    top: 0;
    z-index: 30;
  }
  .admin-nav-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    padding: 0;
    background: transparent;
    border: none;
    font-size: 22px;
    color: var(--text);
    cursor: pointer;
  }
  .admin-topbar-title {
    font-weight: 500;
    color: var(--text);
  }
  .admin-layout nav {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: 260px;
    z-index: 50;
    transform: translateX(-100%);
    transition: transform 200ms ease;
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.1);
  }
  .admin-layout nav.open {
    transform: translateX(0);
  }
  .admin-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.35);
    z-index: 40;
  }
  .admin-layout main {
    padding: 16px;
    width: 100%;
  }
```

- [ ] **Step 4.6：在桌面端样式中确保 `.admin-topbar` 默认隐藏**

在 `styles.css` 里原有 `.admin-layout` 规则附近（约 633 行之后），加入一条：

```css
.admin-topbar { display: none; }
```

放在 `.admin-layout { ... }` 规则之后、`.admin-layout nav { ... }` 之前。这样默认（桌面）不显示 topbar；移动端 `@media` 内已显式 `display: flex` 覆盖。

- [ ] **Step 4.7：运行后台相关测试**

```bash
cd frontend
E2E_ADMIN=1 npm run test:e2e -- admin
```

期望：`admin.desktop` 冒烟 PASS，`admin.mobile` 前 3 条（汉堡、NavLink 关闭、无横向滚动）PASS。第 4 条（表格卡片）仍 FAIL — 留给 Task 5。

若本地无 admin 后端，所有 admin 用例会 skip，改为手动验证：
- `npm run dev`，用 DevTools 切换到 iPhone 13，登录 admin，进入 `/admin/projects`，验证汉堡与抽屉行为。

- [ ] **Step 4.8：Commit**

```bash
git add frontend/src/pages/admin/AdminLayout.tsx \
        frontend/src/styles.css \
        frontend/tests/e2e/admin.mobile.spec.ts \
        frontend/tests/e2e/admin.desktop.spec.ts
git commit -m "feat(mobile): drawer nav for admin layout on phones"
```

---

## Task 5: 后台表格转卡片

**Files:**
- Modify: `frontend/src/pages/admin/AdminProjectsPage.tsx`
- Modify: `frontend/src/pages/admin/AdminSessionsPage.tsx`
- Modify: `frontend/src/styles.css`（移动端块内追加）

- [ ] **Step 5.1：修改 `AdminProjectsPage.tsx` 的 `<td>` 添加 `data-label`**

将文件中 `<tbody>` 内每个 `<td>` 修改为（保持原有 style / onClick 不变）：

```tsx
<tbody>
  {items.map((p) => (
    <tr key={p.id}>
      <td data-label="名称">{p.name}</td>
      <td data-label="路径" style={{ fontFamily: 'ui-monospace, SFMono-Regular, monospace', fontSize: 12 }}>{p.path}</td>
      <td data-label="介绍" style={{ color: '#4b5563', whiteSpace: 'pre-wrap' }}>
        {p.description || <span style={{ color: '#9ca3af' }}>—</span>}
      </td>
      <td data-label="操作" className="actions">
        <button className="btn secondary" onClick={() => startEdit(p)} style={{ marginRight: 8 }}>
          编辑
        </button>
        <button className="btn danger" onClick={() => remove(p.id)}>
          删除
        </button>
      </td>
    </tr>
  ))}
  {items.length === 0 && (
    <tr>
      <td colSpan={4} className="empty" data-label="">
        暂无项目，点击"新增项目"开始
      </td>
    </tr>
  )}
</tbody>
```

- [ ] **Step 5.2：修改 `AdminSessionsPage.tsx` 的 `<td>` 添加 `data-label`**

将 `<tbody>` 内修改为：

```tsx
<tbody>
  {items.map((s) => (
    <tr key={s.sessionId} style={{ cursor: 'pointer' }} onClick={() => open(s.sessionId)}>
      <td data-label="标题">{s.title}</td>
      <td data-label="用户" style={{ fontFamily: 'monospace', fontSize: 12 }}>{s.userId.slice(0, 8)}</td>
      <td data-label="最近活跃">{new Date(s.lastActiveAt).toLocaleString()}</td>
    </tr>
  ))}
  {items.length === 0 && (
    <tr>
      <td colSpan={3} className="empty" data-label="">
        暂无会话
      </td>
    </tr>
  )}
</tbody>
```

- [ ] **Step 5.3：在 `styles.css` 的移动端块内追加表格转卡片 CSS**

在 `@media (max-width: 768px) { ... }` 里（Task 4 之后）追加：

```css
  /* --- 后台：表格转卡片 --- */
  .admin-layout main table,
  .admin-layout main thead,
  .admin-layout main tbody,
  .admin-layout main tr,
  .admin-layout main td {
    display: block;
    width: 100%;
  }

  .admin-layout main thead { display: none; }

  .admin-layout main tbody tr {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 12px;
    margin-bottom: 12px;
  }

  .admin-layout main td {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
    padding: 6px 0;
    word-break: break-all;
  }

  .admin-layout main td::before {
    content: attr(data-label);
    font-weight: 500;
    color: var(--text-soft);
    flex-shrink: 0;
    min-width: 72px;
  }

  .admin-layout main td:not([data-label])::before,
  .admin-layout main td[data-label=""]::before {
    content: none;
  }

  .admin-layout main td.actions {
    justify-content: flex-end;
  }
  .admin-layout main td.actions::before { content: none; }

  /* 会话列表页：两列网格在手机上改单列 */
  .admin-layout main div[style*="grid-template-columns"] {
    grid-template-columns: 1fr !important;
  }

  /* 后台表单：移动端宽度撑满 */
  .form-grid { max-width: 100% !important; }
  .admin-layout main input,
  .admin-layout main textarea,
  .admin-layout main select {
    font-size: 16px;
  }
```

- [ ] **Step 5.4：运行后台移动端测试**

```bash
cd frontend
E2E_ADMIN=1 npm run test:e2e -- admin
```

期望：`admin.mobile` 的 4 条用例全部 PASS；`admin.desktop` 冒烟仍 PASS。若无 admin 后端，手动验证：移动端 viewport 下 `/admin/projects` 每行变卡片、字段前显示中文标签、`/admin/sessions` 单列布局。

- [ ] **Step 5.5：Commit**

```bash
git add frontend/src/pages/admin/AdminProjectsPage.tsx \
        frontend/src/pages/admin/AdminSessionsPage.tsx \
        frontend/src/styles.css
git commit -m "feat(mobile): convert admin tables to cards on phones"
```

---

## Task 6: 登录页 & 收尾校验

**Files:**
- Modify: `frontend/src/styles.css`（移动端块内追加）

- [ ] **Step 6.1：在 `styles.css` 的移动端块末尾追加登录页覆写**

```css
  /* --- 后台登录页：卡片宽度自适应 --- */
  form, .admin-login-card { /* 登录页 root div 用 inline style，靠选择器定位 */ }
  /* 直接定位 AdminLoginPage 的外层：maxWidth:360 + margin:120px auto */
  .admin-layout + * { /* no-op placeholder */ }
```

由于 `AdminLoginPage` 使用 inline style，直接 CSS 覆盖不可行。改为给它加一个 className：

- [ ] **Step 6.2：修改 `AdminLoginPage.tsx` 根 div 添加 `className`**

将第 20 行 `<div style={{...}}>` 修改为：

```tsx
<div
  className="admin-login-card"
  style={{
    maxWidth: 360,
    margin: '120px auto',
    padding: 32,
    background: 'var(--surface)',
    borderRadius: 'var(--radius-lg)',
    border: '1px solid var(--border)',
    boxShadow: 'var(--shadow)',
  }}
>
```

- [ ] **Step 6.3：用真实选择器替换 Step 6.1 的占位**

把 Step 6.1 写入的占位规则替换成：

```css
  /* --- 后台登录页：卡片宽度自适应 --- */
  .admin-login-card {
    max-width: calc(100% - 32px) !important;
    margin: 40px 16px !important;
    padding: 24px !important;
  }
  .admin-login-card input {
    font-size: 16px !important;
  }
```

- [ ] **Step 6.4：TypeScript 构建校验**

```bash
cd frontend
npm run build
```

期望：无 TS/构建错误，`dist/` 正常产出。

- [ ] **Step 6.5：完整运行 E2E 套件**

```bash
cd frontend
npm run test:e2e
```

期望：
- `chat.mobile.spec.ts` mobile project 全部 PASS
- `admin.desktop.spec.ts`、`admin.mobile.spec.ts` 在无 `E2E_ADMIN=1` 时 skip；在有 admin 后端+`E2E_ADMIN=1` 时全部 PASS

- [ ] **Step 6.6：手动跨视口视觉回归**

启动 `npm run dev`，依次在 DevTools 切换下列 viewport，按验收标准清单过一遍：

| 视口 | 页面 | 检查项 |
|------|------|--------|
| iPhone 13 (390×844) | `/` | 无横向滚动、composer 固定底部、键盘弹起不遮消息、按钮 ≥ 44×44、发送消息链路正常 |
| iPhone 13 | `/admin/login` | 卡片宽度自适应、输入框不触发缩放 |
| iPhone 13 | `/admin/projects` | 汉堡开关抽屉、遮罩点击关闭、NavLink 跳转后自动关闭、表格以卡片形式显示 |
| iPhone 13 | `/admin/sessions` | 单列布局、卡片显示字段标签、详情面板可滚动 |
| 1280×720 | `/`、`/admin/*` | 与改造前视觉一致（sidebar 常驻、表格正常、无 topbar） |

- [ ] **Step 6.7：Commit**

```bash
git add frontend/src/pages/admin/AdminLoginPage.tsx frontend/src/styles.css
git commit -m "feat(mobile): adapt admin login card for phones"
```

---

## 验收 Checklist

执行完所有 Task 后，确认：

- [ ] `frontend/src/styles.css` 末尾存在 "Mobile Adaptations" 区块（≥ 150 行）
- [ ] `AdminLayout.tsx` 具备 `navOpen` state、顶部 topbar、backdrop、`useLocation` 路由变化关闭
- [ ] `AdminProjectsPage.tsx` / `AdminSessionsPage.tsx` 每个 `<td>` 都有 `data-label`
- [ ] `frontend/package.json` 包含 `@playwright/test`、`test:e2e` / `test:e2e:ui` scripts
- [ ] `frontend/playwright.config.ts` 配置 desktop + mobile 两个 project
- [ ] `frontend/tests/e2e/` 下有 `chat.mobile.spec.ts`、`admin.mobile.spec.ts`、`admin.desktop.spec.ts`
- [ ] `frontend/.gitignore` 排除 `test-results/`、`playwright-report/`
- [ ] `npm run build` 无错误
- [ ] iPhone 13 viewport：聊天页与 admin 均无横向滚动、关键交互正常
- [ ] 1280×720 桌面：改造前后视觉零差异
