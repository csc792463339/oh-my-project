import { test, expect } from '@playwright/test';

test.describe('聊天页 · 移动端', () => {

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
    expect(box!.y + box!.height).toBeGreaterThan(viewport.height - 80);
  });

  test('发送与附件按钮满足 44×44 触控命中（仅 mobile）', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅 mobile project 运行');
    await page.goto('/');
    const sendOrAttach = page.locator('.composer button').first();
    await expect(sendOrAttach).toBeVisible();
    const box = await sendOrAttach.boundingBox();
    expect(box!.width).toBeGreaterThanOrEqual(44);
    expect(box!.height).toBeGreaterThanOrEqual(44);
  });

  test('输入并发送文本时消息气泡出现（需后端）', async ({ page }, testInfo) => {
    test.skip(!process.env.E2E_BACKEND, '需 E2E_BACKEND=1 且后端运行中');
    test.skip(testInfo.project.name !== 'mobile', '仅 mobile project 运行');
    await page.goto('/');
    const textarea = page.locator('.composer textarea');
    await textarea.fill('你好，移动端测试');
    const send = page.locator('.composer button', { hasText: /发送|Send/i }).first();
    const btn = (await send.count()) > 0 ? send : page.locator('.composer button').last();
    await btn.click();
    await expect(page.locator('.bubble.user')).toContainText('你好，移动端测试', { timeout: 10_000 });
  });
});
