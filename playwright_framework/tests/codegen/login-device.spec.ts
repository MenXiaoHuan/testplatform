import { test, expect, devices } from '@playwright/test';

test.use({
  ...devices['iPhone 13'],
  ignoreHTTPSErrors: true,
  viewport: {
    height: 900,
    width: 1440
  }
});

test('test', async ({ page }) => {
  await page.goto('https://localhost:5173/#/pages/login/index');
  await page.getByRole('link', { name: '忘记密码?' }).click();
  await page.locator('uni-input').filter({ hasText: '注册邮箱' }).getByRole('textbox').click();
});