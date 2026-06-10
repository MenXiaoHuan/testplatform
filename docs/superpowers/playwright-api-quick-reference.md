# Playwright API 实战速查

这份文档面向“马上要写 case”的场景，重点整理最常用的定位、操作、等待、断言和调试 API。

## 1. 最常用导入

```ts
import { test, expect } from '@playwright/test';
```

## 2. 推荐定位方式

优先级建议：

1. `getByRole()`
2. `getByLabel()`
3. `getByPlaceholder()`
4. `getByText()`
5. `getByTestId()`
6. `locator()` 作为补充

原因：

- 语义化更强
- 对 UI 微调更稳
- 可读性更好

选择原则：

- 用户能感知到语义时，优先用 `getByRole()`、`getByLabel()`
- 组件没有良好语义但有稳定测试标识时，用 `getByTestId()`
- 组件库或复杂 DOM 结构下，再考虑 `locator()`
- 文案经常变化时，少用 `getByText()`
- 列表、表格、卡片类结构，优先“先缩小范围，再在范围内定位”

### 2.1 `getByRole()`

适合按钮、输入框、链接、弹窗等有明确语义的元素。

```ts
await page.getByRole('button', { name: '登录' }).click();
await page.getByRole('textbox', { name: '用户名' }).fill('admin');
await page.getByRole('link', { name: '详情' }).click();
```

### 2.2 `getByLabel()`

适合表单项。

```ts
await page.getByLabel('账号').fill('admin001');
await page.getByLabel('密码').fill('123456');
```

### 2.3 `getByPlaceholder()`

适合输入框有 placeholder 的场景。

```ts
await page.getByPlaceholder('请输入您的账号').fill('admin001');
await page.getByPlaceholder('请输入您的密码').fill('123456');
```

### 2.4 `getByText()`

适合文案驱动的元素，但不要滥用。

```ts
await page.getByText('提交成功').waitFor();
await page.getByText('暂无数据').isVisible();
```

### 2.5 `getByTestId()`

适合团队有统一 `data-testid` 规范时使用。

```ts
await page.getByTestId('login-submit').click();
await expect(page.getByTestId('user-name')).toHaveText('管理员');
```

### 2.6 `locator()`

适合复杂结构或组件库场景。

```ts
await page.locator('.menu-item').filter({ hasText: '系统管理' }).click();
await page.locator('table tbody tr').nth(0).click();
```

说明：

- `locator()` 很强，但也最容易被写成脆弱选择器
- 如果你写出了很长的 CSS 路径，通常说明还没找到更稳定的业务语义
- 可以优先考虑“父范围 + 子元素”的组合方式，而不是一条很长的选择器

### 2.7 Shadow DOM

Playwright 对大多数开放的 Shadow DOM 支持穿透定位，很多时候不需要特殊 API。

```ts
await page.getByText('提交').click();
await page.locator('my-login-component').getByRole('button', { name: '登录' }).click();
```

推荐方式：

- 先按正常定位方式尝试
- 如果知道宿主组件范围，先定位宿主，再在里面继续定位
- 优先使用宿主组件内可感知的语义元素，不要直接依赖 shadow 内部层级

例如：

```ts
const loginPanel = page.locator('my-login-component');
await loginPanel.getByPlaceholder('请输入您的账号').fill('admin001');
await loginPanel.getByRole('button', { name: '登录' }).click();
```

注意：

- Playwright 主要能穿透开放的 Shadow DOM
- 如果组件使用关闭模式的 shadow root，自动化可见性会受限
- 遇到 closed shadow root，优先和前端约定可测试入口，而不是强行走内部结构

## 3. 定位器组合技巧

### 3.1 `filter()`

作用：

- 先拿到一组元素，再按文本或子元素条件进一步筛选
- 适合“列表里找某一项”“卡片里找某一块”这类场景

常见场景：

- 一组卡片里找“订单中心”
- 一组按钮里找带某段文案的那一个
- 表格行很多，但只想操作满足特定文本的那一行

```ts
await page.locator('.card').filter({ hasText: '订单中心' }).click();
```

参数说明：

- `hasText`
  - 按文本内容筛选
  - 适合目标元素本身或其后代包含某段文案的场景
- `has`
  - 按“内部是否包含某个子定位器”筛选
  - 适合结构化组件筛选

### 3.2 `has`

作用：

- 通过“这个元素内部有没有某个子元素”来筛选父元素
- 很适合复杂组件、列表项、表格行、卡片块定位

常见场景：

- 找“内部带删除按钮”的列表项
- 找“包含某个标签/状态”的卡片
- 找“这一行里有某个按钮”的表格行

```ts
await page.locator('.list-item', {
  has: page.getByRole('button', { name: '删除' }),
}).click();
```

说明：

- `has` 的核心思路不是直接找按钮，而是先找“包含这个按钮的父块”
- 这样后续可以对整块做点击、断言、拖拽等操作

### 3.3 `nth()` / `first()` / `last()`

作用：

- 在一组匹配结果里，按顺序取某一个元素
- 适合“多个结果结构相同，但你明确知道自己要第几个”的场景

区别：

- `first()`
  - 取第一个
- `last()`
  - 取最后一个
- `nth(index)`
  - 取指定下标，从 `0` 开始

适用场景：

- 取列表第一条
- 取表格最后一行
- 取第 3 个 tab、按钮、卡片

注意：

- 这类方法依赖顺序，顺序一变就容易失效
- 如果能通过文本、角色、测试标识定位，优先不要用索引定位

```ts
await page.locator('.row').first().click();
await page.locator('.row').nth(2).click();
await page.locator('.row').last().click();
```

### 3.4 链式定位

作用：

- 先把查找范围缩到一个局部区域，再在这个局部里继续找目标元素
- 是复杂页面里非常推荐的定位方式

适用场景：

- 弹窗里的按钮
- 抽屉里的输入框
- 某张卡片里的操作按钮
- 某个表格区域里的分页器

```ts
await page
  .locator('.dialog')
  .getByRole('button', { name: '确认' })
  .click();
```

实战建议：

- 页面结构复杂时，不要一开始就全局查找
- 先定位区域，再定位区域内元素，稳定性通常更高
- 对弹窗、抽屉、表格、卡片、列表都适用

## 4. 常用页面操作

### 4.1 页面跳转

```ts
await page.goto('https://localhost:5173/#/pages/login/index');
await page.reload();
await page.goBack();
```

### 4.2 点击

```ts
await page.getByRole('button', { name: '登录' }).click();
await page.getByText('更多').dblclick();
await page.locator('.menu-item').click({ button: 'right' });
```

说明：

- 点击失败时，先不要急着加 `force: true`
- 优先排查元素是否被遮挡、未渲染完成、在动画中、或定位错了
- `force: true` 适合少数特殊场景，不适合作为默认解决方案

### 4.3 输入

```ts
await page.getByLabel('账号').fill('admin001');
await page.getByLabel('密码').fill('123456');
await page.getByLabel('搜索').press('Enter');
```

说明：

- `fill()` 会清空再输入，适合表单赋值
- `type()` 更像逐字输入，适合需要触发按键过程的场景
- 如果业务依赖失焦校验，可以在输入后补一个 `press('Tab')`

### 4.4 选择框

```ts
await page.locator('select').selectOption('beijing');
await page.locator('select').selectOption({ label: '上海' });
```

### 4.5 勾选

```ts
await page.getByLabel('记住我').check();
await page.getByLabel('记住我').uncheck();
```

### 4.6 文件上传

```ts
await page.setInputFiles('input[type="file"]', 'fixtures/demo.png');
```

### 4.7 鼠标与键盘

```ts
await page.mouse.move(100, 200);
await page.mouse.wheel(0, 500);
await page.keyboard.press('Escape');
await page.keyboard.type('hello');
```

## 5. 等待 API

Playwright 默认带自动等待，优先相信 Locator 和 Expect，不要一上来就 `waitForTimeout()`.

等待选择建议：

- 验证结果是否出现：优先 `expect(...)`
- 等元素显隐：优先 `locator.waitFor()`
- 等路由跳转：优先 `waitForURL()`
- 等接口完成：优先 `waitForResponse()`
- 等页面完全加载：再考虑 `waitForLoadState()`

### 5.1 推荐：断言式等待

```ts
await expect(page.getByText('登录成功')).toBeVisible();
await expect(page.getByRole('button', { name: '提交' })).toBeEnabled();
await expect(page).toHaveURL(/dashboard/);
```

### 5.2 `locator.waitFor()`

适合等待元素出现、隐藏、挂载、卸载。

```ts
await page.locator('.loading').waitFor({ state: 'hidden' });
await page.locator('.dialog').waitFor({ state: 'visible' });
```

支持的常见状态：

- `attached`
- `detached`
- `visible`
- `hidden`

### 5.3 `page.waitForURL()`

```ts
await page.waitForURL('**/dashboard');
await page.waitForURL(/\/pages\/home/);
```

### 5.4 `page.waitForLoadState()`

作用：

- 等当前页面进入某个加载阶段
- 适合页面跳转后，想等文档或资源到达某个稳定状态再继续

常见写法：

```ts
await page.waitForLoadState('load');
await page.waitForLoadState('domcontentloaded');
await page.waitForLoadState('networkidle');
```

参数说明：

- `'domcontentloaded'`
  - 含义：HTML 已经解析完成，DOM 树可用了
  - 不代表图片、样式、子资源都加载完
  - 适合：你只关心 DOM 可操作，不关心资源是否全加载完成
- `'load'`
  - 含义：页面触发了浏览器原生 `load` 事件
  - 一般意味着大部分静态资源都已经加载完成
  - 适合：你希望页面主体和资源更完整后再继续
- `'networkidle'`
  - 含义：一段时间内没有新的网络请求
  - 适合：非常少数需要等待网络完全安静下来的场景
  - 不适合：有轮询、长连接、持续请求的现代前端页面

可以粗略理解成：

- `domcontentloaded`：DOM 可以用了
- `load`：页面大体加载完成了
- `networkidle`：网络基本安静下来了

如何选择：

- 只是要开始查找和操作 DOM：优先 `domcontentloaded`
- 页面初次加载，需要更稳一点：常用 `load`
- 明确知道页面必须等网络彻底安静：才考虑 `networkidle`

注意：

- `networkidle` 不一定适合所有前端项目
- 对轮询页面、长连接页面要谨慎使用
- 很多场景其实不需要显式 `waitForLoadState()`，直接用 Locator 或 `expect` 更自然

### 5.5 `page.waitForResponse()`

适合等接口返回。

```ts
await page.waitForResponse((response) => {
  return response.url().includes('/api/login') && response.status() === 200;
});
```

实战建议：

- 如果点击会触发关键接口，通常把“点击”和“等响应”放进同一个 `Promise.all()`
- 这样可以避免因为请求发得太快而错过监听时机

```ts
const [response] = await Promise.all([
  page.waitForResponse((res) => res.url().includes('/api/login') && res.status() === 200),
  page.getByRole('button', { name: '登录' }).click(),
]);
```

### 5.6 不推荐：`waitForTimeout()`

```ts
await page.waitForTimeout(3000);
```

只适合：

- 临时调试
- 动画观察
- 无法快速拿到稳定信号的极少数场景

不适合：

- 正式测试主流程

## 6. 常用断言

### 6.1 元素断言

```ts
await expect(page.getByText('提交成功')).toBeVisible();
await expect(page.getByRole('button', { name: '保存' })).toBeEnabled();
await expect(page.getByLabel('账号')).toHaveValue('admin001');
await expect(page.locator('.error-tip')).toContainText('密码错误');
```

### 6.2 页面断言

```ts
await expect(page).toHaveTitle(/首页/);
await expect(page).toHaveURL(/dashboard/);
```

### 6.3 数量断言

```ts
await expect(page.locator('.table-row')).toHaveCount(10);
```

断言建议：

- 断言尽量贴近业务结果，不要只断言“按钮点过了”
- 优先断言用户真正能观察到的结果：文案、URL、列表数量、表单值、状态标签
- 对异步页面，优先用 `expect(locator)` 的自动重试能力，不要自己手动循环判断

## 7. 接口 Mock

Playwright 可以直接拦截网络请求，用于：

- 隔离外部依赖
- 稳定接口数据
- 覆盖异常分支
- 构造难以在线上环境稳定制造的数据场景

### 7.1 `page.route()`

单页场景下最常用。

```ts
await page.route('**/api/login', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      code: 0,
      message: 'success',
      data: {
        token: 'mock-token',
      },
    }),
  });
});
```

### 7.2 `context.route()`

适合一个 context 下多个页面共用。

```ts
await context.route('**/api/user/profile', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      code: 0,
      data: {
        id: 1,
        name: '管理员',
      },
    }),
  });
});
```

### 7.3 模拟失败响应

```ts
await page.route('**/api/login', async (route) => {
  await route.fulfill({
    status: 500,
    contentType: 'application/json',
    body: JSON.stringify({
      code: 500,
      message: '服务器异常',
    }),
  });
});
```

### 7.4 继续请求但改写部分内容

```ts
await page.route('**/api/user/profile', async (route) => {
  const response = await route.fetch();
  const json = await response.json();

  json.data.name = 'Mock 管理员';

  await route.fulfill({
    response,
    body: JSON.stringify(json),
  });
});
```

### 7.5 放行请求

```ts
await page.route('**/api/**', async (route) => {
  await route.continue();
});
```

### 7.6 中止请求

```ts
await page.route('**/api/report', async (route) => {
  await route.abort();
});
```

Mock 建议：

- 优先 mock 对业务判断真正关键的接口
- 不要把所有请求都 mock 掉，否则容易失去真实集成价值
- 一个 case 如果想验证前端逻辑，mock 很有价值
- 一个 case 如果想验证联调链路，尽量少 mock

## 8. iframe 与多窗口

### 8.1 iframe

```ts
const frame = page.frameLocator('iframe');
await frame.getByRole('button', { name: '确认' }).click();
```

### 8.2 新页面

```ts
const [newPage] = await Promise.all([
  page.context().waitForEvent('page'),
  page.getByRole('link', { name: '打开详情' }).click(),
]);

await newPage.waitForLoadState();
```

### 8.3 弹窗

```ts
page.on('dialog', async (dialog) => {
  await dialog.accept();
});
```

## 9. 网络与调试

### 9.1 监听请求

```ts
page.on('request', (request) => {
  console.log('>>', request.method(), request.url());
});
```

### 9.2 监听响应

```ts
page.on('response', (response) => {
  console.log('<<', response.status(), response.url());
});
```

### 9.3 暂停调试

```ts
await page.pause();
```

### 9.4 截图

```ts
await page.screenshot({ path: 'debug.png', fullPage: true });
```

### 9.5 观察控制台报错

```ts
page.on('console', (msg) => {
  console.log('[browser console]', msg.type(), msg.text());
});
```

```ts
page.on('pageerror', (error) => {
  console.log('[page error]', error.message);
});
```

## 10. 一个常见登录示例

```ts
import { test, expect } from '@playwright/test';

test('登录成功', async ({ page }) => {
  await page.goto('https://localhost:5173/#/pages/login/index');

  await page.getByPlaceholder('请输入您的账号').fill('admin001');
  await page.getByPlaceholder('请输入您的密码').fill('123456');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page).toHaveURL(/home|index|dashboard/);
});
```

## 11. 常见误区

- 不要优先写超长 CSS 选择器
- 不要把 `waitForTimeout()` 当主等待方案
- 不要用 `nth()` 解决所有定位问题
- 不要在断言前手动 sleep
- 不要把定位和断言拆得过碎，优先用 `expect(locator)...`
- 不要为了让 case 通过就习惯性使用 `force: true`
- 不要把所有接口都 mock 掉
- 遇到 Shadow DOM 不要第一反应就研究内部层级，先找宿主语义和可测试入口

## 12. 速查结论

- 定位优先：`getByRole`、`getByLabel`、`getByPlaceholder`
- 等待优先：`expect`、`locator.waitFor`
- Shadow DOM 优先：先正常定位，再宿主范围内定位
- Mock 优先：只 mock 关键接口，不要全量 mock
- 调试优先：`page.pause()`、截图、Trace
- 复杂结构再用：`locator().filter().nth()`
