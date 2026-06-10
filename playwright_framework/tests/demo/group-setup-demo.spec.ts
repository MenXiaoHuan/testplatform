import { expect, test } from '../../utils/allure';
import { demoGroupConfig } from '../../config/test-groups/demo';
import { setupGroupContext } from '../../utils/groupContext';

// 这是“教用法”的示例，所以整个 describe 直接 skip。
// 你可以把 skip 去掉，再把示例地址/cookies/headers 换成你自己的真实值。
test.describe.skip('测试组前置配置 Demo', () => {
  // 这里选择 serial，是因为整个用例组共用一个 context/page。
  test.describe.configure({ mode: 'serial' });

  // beforeAll / afterAll 的重复逻辑已经收进 helper。
  // 用例组里只需要声明这组测试要用的环境配置即可。
  const group = setupGroupContext(demoGroupConfig);

  test.beforeEach(async () => {
    // 关键点 1：
    // beforeEach 放“每个用例都要做”的准备动作。
    // 比如进入某个页面、清空某些临时状态、准备测试数据。
    await group.page.goto('/dashboard');
  });

  test.afterEach(async ({}, testInfo) => {
    // 关键点 2：
    // afterEach 放“每个用例跑完都要做”的收尾动作。
    // 这里演示把当前页面地址挂到报告里，方便排查问题。
    await testInfo.attach('group-setup-runtime', {
      body: Buffer.from(`current url: ${group.page.url()}`),
      contentType: 'text/plain',
    });
  });

  test('用例 1：进入首页后检查是否登录成功', async () => {
    // 这里只演示写法，不演示具体定位。
    // 真正写业务时，把下面注释替换成你的断言即可。
    //
    // 例如：
    // await expect(group.page.locator('.user-name')).toHaveText('张三');
    // await expect(group.page).toHaveURL(/dashboard/);

    expect(demoGroupConfig.headers?.['x-demo-env']).toBe('interview');
  });

  test('用例 2：进入订单页后执行你的业务断言', async () => {
    // 如果某个用例需要从组公共页面跳到别的页面，就在用例里自己跳。
    await group.page.goto('/orders');

    // 例如：
    // await expect(group.page.locator('.order-list')).toBeVisible();
    // await expect(group.page.locator('.order-item')).toHaveCount(3);

    expect(demoGroupConfig.cookies?.[0]?.name).toBe('sessionid');
  });

  test('用例 3：如果只想在当前组里临时补一个 cookie，也可以直接加', async () => {
    await group.context.addCookies([
      {
        name: 'feature_flag',
        value: 'A',
        domain: '.example.com',
        path: '/',
      },
    ]);

    // 例如：
    // await group.page.reload();
    // await expect(group.page.locator('.new-feature')).toBeVisible();

    expect(demoGroupConfig.headers?.['x-demo-role']).toBe('qa');
  });
});
