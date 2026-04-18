import { test, expect } from '@playwright/test';

test('聊天首页可以打开', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Oh My Project/);
});
