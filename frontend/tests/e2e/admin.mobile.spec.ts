import { test, expect } from '@playwright/test';

test.describe('后台 · 移动端', () => {
  test.skip(({}, testInfo) => testInfo.project?.name !== 'mobile', '仅 mobile project 运行');
  test.skip(!process.env.E2E_ADMIN, '需 E2E_ADMIN=1 且 admin 已登录/可登录才能跑');

  test('汉堡可打开与关闭抽屉', async ({ page }) => {
    await page.goto('/admin/projects');
    const nav = page.locator('.admin-layout nav');
    const hamburger = page.locator('.admin-topbar [data-testid="nav-toggle"]');
    const backdrop = page.locator('.admin-backdrop');

    await expect(hamburger).toBeVisible();
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
