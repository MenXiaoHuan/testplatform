# Playwright 系统学习笔记

这份文档适合系统学习 Playwright。目标不是只记住 API，而是理解它为什么这样设计，以及日常写自动化时应该怎么思考。

## 1. Playwright 是什么

Playwright 是一个现代浏览器自动化框架，常用于：

- Web 端到端测试
- 冒烟测试
- UI 回归测试
- 调试页面交互
- 录制与回放操作

它支持：

- Chromium
- Firefox
- WebKit

相比传统自动化工具，Playwright 的优势主要在：

- 自带自动等待
- 多浏览器支持一致
- Locator 模型更现代
- 对弹窗、iframe、新页面、下载等场景支持更完整
- 调试体验较好

## 2. 运行模型

写 Playwright 测试时，最核心的对象通常有这几个：

- `browser`
- `context`
- `page`
- `locator`

可以简单理解成：

- `browser`：浏览器进程
- `context`：一个独立浏览器会话
- `page`：一个标签页
- `locator`：页面元素的定位描述

### 2.1 为什么 `context` 很重要

`context` 是隔离测试的关键：

- 不同 context 之间 cookie 不共享
- 不同 context 之间 localStorage 不共享
- 可以用来模拟不同用户身份

所以很多项目会围绕 `context` 组织：

- 登录态
- 环境头
- storageState
- 录制上下文

## 3. 为什么推荐 Locator，而不是 ElementHandle

Playwright 更推荐：

```ts
const submitButton = page.getByRole('button', { name: '提交' });
await submitButton.click();
```

而不是过度依赖老式元素句柄。

原因是 Locator：

- 是惰性的
- 每次操作前都会重新解析元素
- 自带更好的自动等待能力

这意味着当页面有重新渲染、节点替换时，Locator 通常比“先拿句柄再操作”更稳定。

## 4. Playwright 的等待哲学

很多人刚上手自动化最容易犯的错，就是把等待理解成：

> “睡几秒再说”

但 Playwright 的推荐思路是：

> “等一个明确的可观察信号”

比如：

- 等元素可见
- 等按钮可点击
- 等 URL 变化
- 等接口返回
- 等 loading 消失

而不是：

```ts
await page.waitForTimeout(3000);
```

### 4.1 自动等待是什么

像下面这种操作：

```ts
await page.getByRole('button', { name: '登录' }).click();
```

Playwright 不会立刻生硬地点击，它通常会等待元素满足可操作条件，例如：

- 元素已经出现在 DOM
- 元素可见
- 元素没有被遮挡
- 元素处于可交互状态

这就是它“比很多老方案稳”的原因之一。

### 4.2 什么时候还需要显式等待

自动等待并不等于“完全不需要等待”。当你等待的是“页面状态变化”而不是“单个操作本身”时，仍然需要显式表达。

例如：

- 提交后等待 toast
- 登录后等待 URL 跳转
- 等待 loading 消失
- 等待接口返回

常见写法：

```ts
await expect(page.getByText('提交成功')).toBeVisible();
await page.waitForURL(/dashboard/);
await page.locator('.loading').waitFor({ state: 'hidden' });
```

## 5. 定位器的推荐顺序

推荐顺序不是绝对规则，但能代表团队质量倾向。

### 5.1 第一优先：语义定位

例如：

- `getByRole`
- `getByLabel`
- `getByPlaceholder`

优点：

- 更接近真实用户
- 可读性高
- UI 小改动时更稳

### 5.2 第二优先：稳定标识

例如：

- `getByTestId`

适合业务团队已经建设好测试标识的场景。

### 5.3 第三优先：结构定位

例如：

- `locator('.table-row')`
- `locator('div > span:nth-child(2)')`

结构定位不是不能用，但更容易受 DOM 结构变化影响。

### 5.4 为什么不推荐一上来写超长 CSS

例如：

```ts
page.locator('#app > div > div:nth-child(2) > div > button')
```

这种写法的问题：

- 很难读
- 很难维护
- 页面结构一动就挂

### 5.5 Shadow DOM 的原理和处理方式

很多前端组件，尤其是 Web Components，会把内部 DOM 封装进 Shadow DOM。

它带来的影响是：

- 组件内部结构和外部页面结构隔离
- 普通 CSS 选择器思维会变差
- 自动化更应该依赖组件暴露出的语义，而不是内部层级

可以先把它理解成：

- Light DOM：普通页面 DOM
- Shadow DOM：组件内部自带的一层封装 DOM

对自动化来说，最重要的是区分两种模式：

- `open shadow root`
- `closed shadow root`

#### `open shadow root`

这是自动化相对友好的模式。Playwright 对大多数开放的 Shadow DOM 支持较好，很多时候可以直接通过 Locator 穿透定位。

例如：

```ts
await page.locator('my-login-component').getByRole('button', { name: '登录' }).click();
```

推荐思路：

1. 先按正常定位方式尝试
2. 如果知道宿主组件，先定位宿主
3. 再在宿主范围内继续用 `getByRole`、`getByLabel`、`getByPlaceholder`

这种方式比直接研究 shadow 内部层级更稳。

#### `closed shadow root`

这是自动化不友好的模式。

问题在于：

- 组件内部结构不会暴露给外部脚本
- 你很难像操作普通 DOM 那样稳定访问内部节点

这时更合理的做法通常不是“想办法穿进去”，而是：

- 和前端约定宿主组件上的可测试入口
- 补稳定的外部交互语义
- 用 `data-testid`、宿主属性、公共按钮区域等方式暴露测试面

#### 为什么 Shadow DOM 场景更要避免脆弱定位

因为组件升级时，shadow 内部结构很容易改：

- 标签层级会变
- class 名可能变
- 内部包装层可能变

但真正稳定的通常是：

- 宿主组件本身
- 对用户可见的按钮、输入框、标题
- 组件对外承诺的交互入口

所以 Shadow DOM 的核心不是“学会更复杂的选择器”，而是“更坚持语义定位和边界定位”。

## 6. 常见操作 API 的思维方式

### 6.1 点击不是重点，状态变化才是重点

很多初学者写：

```ts
await page.getByRole('button', { name: '提交' }).click();
```

但真正重要的是点击之后要验证什么：

```ts
await page.getByRole('button', { name: '提交' }).click();
await expect(page.getByText('提交成功')).toBeVisible();
```

### 6.2 输入时优先直接表达意图

```ts
await page.getByLabel('账号').fill('admin001');
```

通常比下面这种更清晰：

```ts
await page.locator('input').nth(0).click();
await page.locator('input').nth(0).fill('admin001');
```

## 7. 断言为什么重要

自动化不是“把动作跑完”，而是“验证结果正确”。

所以一个完整 case 通常包含三段：

1. 准备数据或页面状态
2. 执行动作
3. 断言结果

例如：

```ts
await page.goto('/login');
await page.getByLabel('账号').fill('admin001');
await page.getByLabel('密码').fill('123456');
await page.getByRole('button', { name: '登录' }).click();
await expect(page).toHaveURL(/dashboard/);
```

没有断言的脚本，更像“操作脚本”，不是可靠测试。

## 8. 等待 API 怎么选

### 8.1 首选 `expect`

适合验证结果。

```ts
await expect(page.getByText('成功')).toBeVisible();
await expect(page).toHaveURL(/home/);
```

### 8.2 其次 `locator.waitFor`

适合等待结构状态变化。

```ts
await page.locator('.loading').waitFor({ state: 'hidden' });
```

### 8.3 `waitForResponse`

适合接口是关键业务信号时。

```ts
await page.waitForResponse((r) => r.url().includes('/api/login') && r.status() === 200);
```

### 8.4 `waitForTimeout`

仅用于临时调试，不是正式策略。

## 9. 常见高级场景

### 9.1 iframe

```ts
const frame = page.frameLocator('iframe');
await frame.getByRole('button', { name: '确认' }).click();
```

### 9.2 新标签页

```ts
const [newPage] = await Promise.all([
  page.context().waitForEvent('page'),
  page.getByText('打开详情').click(),
]);
```

### 9.3 下载

```ts
const [download] = await Promise.all([
  page.waitForEvent('download'),
  page.getByRole('button', { name: '导出' }).click(),
]);
```

### 9.4 弹窗

```ts
page.on('dialog', async (dialog) => {
  await dialog.accept();
});
```

## 10. Mock 的设计边界

很多人一学会 `page.route()`，就会开始把所有接口都 mock 掉。技术上能做到，但测试设计上通常不是最优解。

更好的问题应该是：

> 这个 case 到底想验证什么？

因为不同目标，对 mock 的依赖完全不同。

### 10.1 什么时候适合 mock

适合 mock 的典型场景：

- 你要验证前端页面逻辑，而不是后端联调
- 外部依赖不稳定，导致 case 经常假失败
- 你要构造真实环境里很难稳定制造的数据
- 你要覆盖异常分支，例如 500、超时、空数据、脏数据

例如：

- 登录失败提示
- 列表空态
- 权限不足
- 接口返回异常字段

这些场景用 mock 往往非常高效。

### 10.2 什么时候不适合 mock

不适合大量 mock 的典型场景：

- 你要验证真实联调链路
- 你要确认页面和后端契约没有漂移
- 你要做接近真实发布前的冒烟
- 你要确认鉴权、网关、跨域、真实 headers 是否都正确

如果把这些也全 mock 掉，就会出现一个问题：

> case 通过了，但系统真实集成并不一定是好的

### 10.3 Mock 的层次感

一个更成熟的测试体系，通常不是“全 mock”或“全不 mock”，而是分层。

常见分层方式：

- 页面逻辑验证层：可以适度 mock
- 页面与服务集成层：少 mock 或不 mock
- 发布前冒烟层：尽量真实

这样做的好处是：

- 前端逻辑问题定位快
- 集成问题不会被 mock 掩盖
- 测试职责更清晰

### 10.4 设计边界的核心原则

可以记住四条：

1. mock 只服务于当前测试目标
2. 不要为了“让 case 绿”而 mock
3. 不要把所有接口都 mock 掉
4. 一个场景里，只 mock 真正需要控制的那部分依赖

例如一个登录页 case：

- 如果目标是验证“登录成功后页面跳转逻辑”，可以 mock 登录接口
- 如果目标是验证“真实登录链路是否可用”，就不该 mock 登录接口

### 10.5 `page.route()` 和真实测试的关系

`page.route()` 很适合局部、页面级控制。

它擅长：

- 快速构造接口响应
- 覆盖边界场景
- 让 case 不依赖后端波动

但它不擅长回答这些问题：

- 后端今天真的可用吗
- 契约字段有没有被改坏
- 网关、鉴权、真实 cookie/header 是否生效

所以 `page.route()` 是“测试工具”，不是“真实集成的替代品”。

### 10.6 一个简单判断法

写 mock 前，可以先问自己一句：

> 如果这个接口被我 mock 了，这条 case 还在验证真实风险吗？

如果答案是：

- “还在验证我要的前端逻辑” -> 可以 mock
- “不验证真实联调风险了” -> 就不该 mock，或者至少不能把它当成唯一 case

### 10.7 Mock 的常见反模式

常见问题包括：

- 所有接口统一 mock 成成功
- mock 数据和真实契约差异越来越大
- 页面逻辑 case 和联调用例混在一起
- 为了绕过不稳定环境而无限扩大 mock 范围

这些做法短期看很省事，长期会让测试越来越“假通过”。

## 11. 录制、调试和排查

### 11.1 `page.pause()`

适合：

- 现场观察页面
- 手动试操作
- 配合 Inspector 调试

### 11.2 Trace

适合：

- 回看失败过程
- 查看每一步的 DOM、截图、网络记录

### 11.3 视频与截图

适合：

- 复盘 flaky case
- 理解实际点击流程

## 12. 常见反模式

### 12.1 过度依赖 `waitForTimeout`

问题：

- 慢
- 不稳定
- 难维护

### 12.2 用 `nth()` 解决所有问题

问题：

- 列表顺序一变就挂

### 12.3 没有断言

问题：

- 跑完不代表对

### 12.4 定位表达不出业务语义

问题：

- 同事看不懂
- 后续排查困难

### 12.5 把 Mock 当成默认方案

问题：

- 很容易掩盖真实集成问题
- case 通过不代表真实链路可用
- mock 数据和真实接口会逐渐漂移

### 12.6 在 Shadow DOM 里研究内部层级

问题：

- 组件一升级就容易失效
- 可读性差
- 忽略了真正稳定的宿主语义和对外入口

## 13. 一套推荐写法

可以把常见用例思路固定成下面这个模板：

```ts
test('用户登录成功', async ({ page }) => {
  await page.goto('https://localhost:5173/#/pages/login/index');

  await page.getByPlaceholder('请输入您的账号').fill('admin001');
  await page.getByPlaceholder('请输入您的密码').fill('123456');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page).toHaveURL(/home|dashboard/);
  await expect(page.getByText(/首页|工作台/)).toBeVisible();
});
```

结构非常稳定：

- 页面准备
- 用户动作
- 结果断言

## 14. 学习顺序建议

如果你是刚上手，建议按这个顺序学：

1. `page.goto`、`click`、`fill`
2. `getByRole`、`getByLabel`、`locator`
3. `expect(...).toBeVisible()`、`toHaveURL()`
4. `locator.waitFor()`、`waitForURL()`
5. iframe、新页面、下载、弹窗
6. Trace、截图、调试

## 15. 和你当前项目怎么结合

你当前项目里已经有几层比较实用的能力：

- `playwright.config.ts` 负责全局运行配置
- `config/recording/envs/` 管环境
- `config/recording/roles/` 管角色、header 和内联 `storageState`
- `scripts/run-codegen.cjs` 提供统一录制入口

所以你可以把学习分两层：

- 通用 Playwright API：看这份文档和速查文档
- 项目实践：结合你当前项目里的 `env/role`、录制 Runner 和报告机制

## 16. 结论

真正把 Playwright 用稳，不是 API 背得多，而是形成这几个习惯：

- 优先语义定位
- 优先状态断言
- 优先明确等待信号
- Shadow DOM 优先看宿主边界，不优先看内部层级
- Mock 只服务于测试目标，不服务于“把 case 跑绿”
- 少写脆弱选择器
- 用 Locator 思维，不用“睡眠思维”
