# 前端 Web 移动端支持 · 设计文档

- 日期：2026-04-18
- 作者：chensicao（与 Claude 协作）
- 状态：待实施

## 背景

现有 `frontend/` 为 React 18 + Vite + TypeScript 单 SPA，包含：
- `/` 聊天页（`ChatPage.tsx`，518 行）
- `/admin/*` 后台管理（登录、项目管理、会话列表），布局由 `AdminLayout.tsx` 提供固定左侧 sidebar + 主区。

样式全部集中在 `src/styles.css`（1110 行），除了 `@media (prefers-reduced-motion)` 外几乎没有响应式规则。`index.html` 已设置 `viewport meta`。

目标：让聊天页与后台管理在移动端（手机）可用且体验良好，桌面端视觉与交互保持不变。

## 范围

**包含：**
- 聊天页 `/` 在手机（≤ 768px）上的布局、输入区、消息气泡适配
- 后台管理 `/admin/*` 在手机上的导航（汉堡菜单 + 抽屉）和表格（转卡片）适配
- Playwright 移动端回归测试

**不包含：**
- 新增 CSS 框架（Tailwind 等）或 UI 组件库
- 平板（769–1024px）专属中间态优化
- 深色模式 / 主题切换相关移动端调整
- PWA、离线、安装到桌面
- `ChatPage.tsx` 的结构性拆分重构
- CI 工作流集成（仅保证本地 `npm run test:e2e` 可跑）

## 关键决策

| 议题 | 决策 | 理由 |
|------|------|------|
| 目标屏幕 | 手机为主，单一断点 `@media (max-width: 768px)` | 平板占比低；单断点心智成本最小 |
| 实现方式 | 纯 CSS 媒体查询 + 极少 React 状态改动 | 现有 CSS 是桌面优先的一整块，追加 `@media` 尾巴最自然；零新依赖 |
| admin 导航 | 顶部汉堡按钮 + 左侧抽屉 | 复用现有 `<nav>`，改动最小 |
| composer | 底部固定 + iOS safe-area | 符合移动端对话 App 主流习惯 |
| admin 表格 | 转卡片（表头隐藏，`<tr>` 变卡片，`<td>` 用 `data-label` 显示列名） | 手机上可读性最好 |
| 测试 | 引入 `@playwright/test`，配置 desktop + mobile 两个 project | 保证桌面端回归不破坏，移动端主路径有自动化验证 |

## 架构与断点

### 单一断点

所有移动端样式集中在 `styles.css` 末尾新增的 **"Mobile Adaptations"** 区块内：

```css
/* ============================================================
   Mobile Adaptations (≤ 768px)
   ============================================================ */
@media (max-width: 768px) {
  /* ... 所有移动端覆盖样式 ... */
}
```

≥ 769px 走原桌面样式不变。

### 全局基础

- `index.html` 的 viewport meta 已就位（`width=device-width, initial-scale=1`），无需修改。
- 新增 CSS 变量（写在 `:root` 或移动端块内）：
  - `--safe-bottom: env(safe-area-inset-bottom, 0px)`
  - `--safe-top: env(safe-area-inset-top, 0px)`
- 使用 `100dvh` 替代需要撑满屏幕的 `100vh`，避免 iOS Safari 键盘弹起时 viewport 抖动导致内容被裁切。
- 移动端触控命中区：所有按钮 `min-width: 44px; min-height: 44px`（iOS HIG 标准）。

## 详细设计

### 1. 聊天页（ChatPage）

**消息列表：**
- 原桌面 `max-width: 760px` 约束在移动端解除，改为视口宽度 `- 16px`。
- 消息气泡 `max-width` 从 `78%` 放宽至 `86%`，避免窄屏大量换行。
- 消息内边距、字号保持桌面端一致，仅角色标签/头像若存在则缩小。

**composer（底部输入区）：**
- `position: fixed; left: 0; right: 0; bottom: 0`，宽度撑满。
- `padding-bottom: calc(8px + env(safe-area-inset-bottom))`，避开 iPhone home indicator。
- 文本框 `font-size: 16px`（防 iOS 自动放大）；自适应行数保持现状。
- 聊天列表底部预留 `padding-bottom` ≥ composer 高度（初值 120px，必要时改用 `--composer-h` CSS 变量驱动），保证最后一条消息不被遮挡。
- 附件按钮 / 发送按钮：`min-width: 44px; min-height: 44px`。
- 附件 chip：`max-width` 从 260px 降至 `calc(50vw - 24px)`；`.attachment-chip.image` 尺寸从 64px 缩至 56px。

**Markdown / 附件：**
- `MarkdownView` 代码块保留现有 `overflow-x: auto`，新增 `-webkit-overflow-scrolling: touch`。
- `FileModal` 图片预览：移动端全屏（`width: 100vw; height: 100dvh`）。

**JSX 改动：** `ChatPage.tsx` 及其子组件零逻辑改动，完全由 CSS 驱动。

### 2. 后台管理 · AdminLayout 抽屉

**JSX 改动（`AdminLayout.tsx`）：**

新增：
- `useState<boolean>(false)` 状态 `navOpen`。
- 顶部条 `<div className="admin-topbar">`，包含汉堡按钮（☰，44×44）+ 页标题占位；仅移动端可见（CSS 控制）。
- `<nav>` 增加动态 class：`navOpen ? 'open' : ''`。
- `<nav>` 之后新增 `<div className="admin-backdrop" onClick={() => setNavOpen(false)} />`。
- 每个 `NavLink` 的 `onClick` 中调用 `setNavOpen(false)`；并用 `useLocation()` 做 `useEffect(() => setNavOpen(false), [pathname])` 兜底，路由变化时自动关闭。

**CSS 改动（移动端块内）：**
- `.admin-layout nav`：`position: fixed; top: 0; left: 0; bottom: 0; width: 260px; z-index: 50; transform: translateX(-100%); transition: transform 200ms ease`。
- `.admin-layout nav.open`：`transform: translateX(0)`。
- `.admin-backdrop`：`position: fixed; inset: 0; background: rgba(0,0,0,0.35); z-index: 40`；默认 `display: none`，`nav.open` 时显示（或用独立状态 class）。
- `.admin-topbar`：`position: sticky; top: 0; z-index: 30; height: 48px; display: flex; align-items: center; padding: 0 12px; background: var(--surface); border-bottom: 1px solid var(--border)`。桌面端 `display: none`。
- `.admin-layout main`：移动端去除左侧让位，改全宽；`padding: 16px`。

### 3. 后台管理 · 表格转卡片

**JSX 改动（`AdminProjectsPage.tsx` / `AdminSessionsPage.tsx`）：**
- 为每个 `<td>` 添加 `data-label="列名"` 属性（中文写死，不做 i18n）。例：`<td data-label="项目名">{row.name}</td>`。
- 最后一格操作列（包含按钮/链接）改为 `<td data-label="操作" className="actions">`。

**CSS 改动（移动端块内）：**
```css
.admin-layout main table,
.admin-layout main thead,
.admin-layout main tbody,
.admin-layout main tr,
.admin-layout main td { display: block; }

.admin-layout main thead { display: none; }

.admin-layout main tr {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
}

.admin-layout main td {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  word-break: break-all;
}

.admin-layout main td::before {
  content: attr(data-label);
  font-weight: 500;
  color: var(--text-soft);
  margin-right: 12px;
  flex-shrink: 0;
}

.admin-layout main td.actions {
  justify-content: flex-end;
  gap: 8px;
}
```

超长字段（session ID、时间戳等）靠 `word-break: break-all` 避免撑破卡片。

### 4. 后台管理 · 登录页 & 表单

- `AdminLoginPage` 卡片：移动端 `width: calc(100% - 32px); margin: 40px 16px`。
- `.form-grid` 原桌面 `max-width: 640px`，移动端覆盖为 `max-width: 100%`。
- 所有 `input`、`textarea`、`select` 在移动端 `font-size: 16px`，避免 iOS 自动缩放。

### 5. Playwright 移动端回归测试

**新增依赖：**
- `frontend/package.json` devDependency：`@playwright/test`。
- Scripts：
  - `"test:e2e": "playwright test"`
  - `"test:e2e:ui": "playwright test --ui"`

**新增 `frontend/playwright.config.ts`：**
```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  use: { baseURL: 'http://localhost:5173' },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1280, height: 720 } } },
    { name: 'mobile', use: { ...devices['iPhone 13'] } },
  ],
});
```

**测试文件 `frontend/tests/e2e/`：**

- `chat.mobile.spec.ts`（mobile project）：
  - 打开 `/`，断言页面无横向滚动（`document.documentElement.scrollWidth <= viewport.width`）。
  - 断言 composer 在底部可见，且其 `getBoundingClientRect().bottom` 贴近视口高度。
  - 向文本框输入"测试消息"并点击发送，断言消息气泡在列表中出现。
  - 断言发送/附件按钮的 `clientHeight >= 44`、`clientWidth >= 44`。

- `admin.mobile.spec.ts`（mobile project，依赖真实后端时通过 `E2E_ADMIN=1` 启用）：
  - 进入 `/admin/login`，登录（使用开发账号或环境变量）。
  - 进入 `/admin/projects`，断言汉堡按钮可见、抽屉默认隐藏（检查 `transform` 计算值或 `getBoundingClientRect().left < 0`）。
  - 点击汉堡，断言抽屉出现；点击遮罩，断言关闭。
  - 打开抽屉、点击"会话列表" NavLink，断言跳转到 `/admin/sessions` 且抽屉自动关闭。
  - 断言表格 `thead` 的 `display` 为 `none`、`tr` 的 `display` 为 `block`。

- `admin.desktop.spec.ts`（desktop project，冒烟保底）：
  - 进入 `/admin/projects`，断言汉堡按钮不可见、`<nav>` 常驻显示、表格按原样渲染（`thead` 可见）。

**测试数据：**
- 聊天页不依赖登录，直接跑。
- admin 相关测试：若无真实后端，默认 `test.skip(!process.env.E2E_ADMIN)`。

**`.gitignore` 追加：** `frontend/test-results/`、`frontend/playwright-report/`、`frontend/.playwright/`。

## 代码改动清单

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `frontend/src/styles.css` | 追加 | 末尾新增 "Mobile Adaptations" 区块（预估 180–250 行） |
| `frontend/src/pages/admin/AdminLayout.tsx` | 修改 | 加 `navOpen` state、topbar、backdrop、路由变化自动关闭 |
| `frontend/src/pages/admin/AdminProjectsPage.tsx` | 修改 | `<td>` 补 `data-label` |
| `frontend/src/pages/admin/AdminSessionsPage.tsx` | 修改 | `<td>` 补 `data-label` |
| `frontend/package.json` | 修改 | 加 `@playwright/test` 与 scripts |
| `frontend/playwright.config.ts` | 新建 | Playwright 配置 |
| `frontend/tests/e2e/chat.mobile.spec.ts` | 新建 | 聊天页移动端用例 |
| `frontend/tests/e2e/admin.mobile.spec.ts` | 新建 | 后台移动端用例（可选运行） |
| `frontend/tests/e2e/admin.desktop.spec.ts` | 新建 | 后台桌面端冒烟 |
| `frontend/.gitignore` | 修改 | 排除 Playwright 产物 |

**零改动：**
- `ChatPage.tsx` 及其子组件（`AttachmentChip`、`MarkdownView`、`FileModal`、`StatusIndicator`）的逻辑
- 路由结构、API 客户端、后端
- `AdminLoginPage.tsx` 仅靠 CSS 调整，不改 JSX

## 验收标准

**iPhone 13 视口（390×844）：**
- 聊天页：页面无横向滚动；composer 固定在底部且不被虚拟键盘/home indicator 遮挡；发送消息链路正常；所有按钮 ≥ 44×44。
- 后台：汉堡可开关抽屉；遮罩点击关闭；导航跳转后抽屉自动关闭；`/admin/projects` 与 `/admin/sessions` 以卡片形式渲染，字段名可见、不溢出。
- 登录页：卡片宽度自适应、输入框不触发 iOS 自动缩放。

**桌面 ≥ 1024px：** 与改造前完全一致（核心回归点）。

**自动化：**
- `npm run build` 无新的 TS/构建错误。
- `npm run test:e2e` 的 desktop + mobile project 全部通过（admin.mobile 依赖真实后端时可通过 `E2E_ADMIN=1` 启用）。

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| `100dvh` 在旧版 Safari 支持有限 | 以 `min-height: 100vh; min-height: 100dvh` 双声明兜底 |
| 表格 `display: block` 覆写可能影响桌面端 | 所有规则限定在 `@media (max-width: 768px)` 内 |
| Playwright 首次安装浏览器体积大（~500MB） | 只在开发者本地运行，不进 CI；README 说明首次需 `npx playwright install` |
| composer 固定后，聊天列表底部 padding 与 composer 实际高度不同步 | 本次先用固定 120px；若后续 composer 多行展开影响明显，用 `ResizeObserver` 把真实高度写入 `--composer-h`（留作后续工单） |

## 后续可能演进（不在本次范围）

- 平板中间态（769–1024px）定制
- 把 `Mobile Adaptations` 区块抽取到 `styles.mobile.css`，按断点按需加载
- 给 composer 高度动态写入 CSS 变量（`ResizeObserver`）
- Playwright 接入 CI
