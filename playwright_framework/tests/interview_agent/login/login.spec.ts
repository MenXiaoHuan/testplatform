import { expect, test } from '../../../utils/allure';
import { interviewLoginGroupConfig } from '../../../config/test-groups/interview_login';
import { setupGroupContext } from '../../../utils/groupContext';

const loginPath = '/#/pages/login/index';

test.describe('登录模块', () => {
  test.describe.configure({ mode: 'serial' });

  const group = setupGroupContext(interviewLoginGroupConfig);

  test.beforeEach(async () => {
    await group.page.goto(loginPath);
  });

  test.afterEach(async ({}, testInfo) => {
    await testInfo.attach('runtime-url', {
      body: Buffer.from(group.page.url()),
      contentType: 'text/plain',
    });
  });

  test('输入正确的账号和密码', async ({ page }) => {
    await page.goto('https://localhost:5173/#/pages/login/index');
    await page.locator('uni-input').filter({ hasText: '请输入您的账号' }).getByRole('textbox').click();
    await page.locator('uni-input').filter({ hasText: '请输入您的账号' }).getByRole('textbox').fill('admin001');
    await page.locator('uni-input').filter({ hasText: '请输入您的密码' }).getByRole('textbox').click();
    await page.locator('uni-input').filter({ hasText: '请输入您的密码' }).getByRole('textbox').fill('666999');
    await page.getByText('登 录').click();
    await expect(page).toHaveURL(`${interviewLoginGroupConfig.baseURL}/#/pages/home/index`);
  });

});
