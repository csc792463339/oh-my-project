import { test, expect } from '@playwright/test';

test.describe('后台 · 桌面端冒烟', () => {
  test.skip(({}, testInfo) => testInfo.project?.name !== 'desktop', '仅 desktop project 运行');
  test.skip(!process.env.E2E_ADMIN, '需 E2E_ADMIN=1 且 admin 已登录/可登录才能跑');

  test('侧边栏常驻、汉堡按钮不可见', async ({ page }) => {
    await page.goto('/admin/projects');
    await expect(page.locator('.admin-layout nav')).toBeVisible();
    await expect(page.locator('.admin-topbar')).toBeHidden();
    await expect(page.locator('.admin-layout main table thead')).toBeVisible();
  });
});
