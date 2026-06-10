import { test, expect } from '@playwright/test';

test.use({
  ignoreHTTPSErrors: true,
  viewport: {
    height: 900,
    width: 1440
  }
});

test('test', async ({ page }) => {
  await page.goto('https://localhost:5173/#/pages/login/index');
});