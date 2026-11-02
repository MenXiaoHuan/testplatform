/*
 * 巡页 runner 冒烟测试（不依赖真实浏览器 / Docker）。
 *
 * 通过最小 playwright-core 占位模块 + 假 page 对象，验证：
 *   1. EXECUTE 模式按 cases.json 执行，ACTION 带 caseId/caseStepSeq，危险步骤 SKIPPED，CASE_END SUCCESS。
 *   2. DISCOVER 模式输出 PAGE_VISIT 快照，并从快照元素中发现同源链接。
 *
 * 运行：node src/test/patrol/patrol-runner.smoke.test.js
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'patrol-runner-test-'));
fs.mkdirSync(path.join(temp, 'screenshots'), { recursive: true });
const config = {
  startUrl: 'https://a.test/',
  browser: 'chromium',
  headless: true,
  mode: 'EXECUTE',
  auth: { mode: 'NONE' },
  limits: {
    actionTimeoutMs: 1000,
    pageTimeoutMs: 1000,
    settleMs: 1,
    maxScreenshots: 10,
    maxDurationMs: 60000,
    maxActions: 20,
    maxPages: 5,
    seqOffset: 0,
    scrollScan: false,
    allowDangerous: false,
    dangerousKeywords: ['删除'],
  },
};
fs.writeFileSync(path.join(temp, 'config.json'), JSON.stringify(config));
fs.writeFileSync(path.join(temp, 'cases.json'), JSON.stringify([
  {
    caseId: 1,
    name: '检索流程',
    pageKey: 'https://a.test/',
    steps: [
      { seq: 1, action: 'fill', selector: '#kw', label: '搜索框', value: '测试' },
      { seq: 2, action: 'click', selector: 'button.search', label: '搜索' },
      { seq: 3, action: 'assert_text', selector: null, label: '结果', value: '结果' },
      { seq: 4, action: 'click', selector: '#delete', label: '删除' },
    ],
  },
]));
fs.writeFileSync(path.join(temp, 'events.jsonl'), '');

process.env.PATROL_CONFIG = 'config.json';
process.env.PATROL_EVENTS = 'events.jsonl';
process.env.PATROL_SCREENSHOT_DIR = 'screenshots';
process.chdir(temp);

// 提供最小 playwright-core 占位模块，使 patrol.js 可在无浏览器环境被测试。
const fakeModuleDir = path.join(temp, 'node_modules', 'playwright-core');
fs.mkdirSync(fakeModuleDir, { recursive: true });
fs.writeFileSync(path.join(fakeModuleDir, 'index.js'),
  'module.exports = { chromium: {}, firefox: {}, webkit: {} };\n');
process.env.NODE_PATH = path.join(temp, 'node_modules');
require('module').Module._initPaths();

let currentUrl = 'https://a.test/';
const fakeLocator = (selector) => ({
  first() { return this; },
  count: async () => 1,
  click: async () => {
    if (selector === 'button.search') currentUrl = 'https://a.test/results';
  },
  fill: async () => {},
  selectOption: async () => {},
  hover: async () => {},
  scrollIntoViewIfNeeded: async () => {},
  waitFor: async () => {},
  innerText: async () => '结果列表',
});
const fakeFrame = {
  evaluate: async (fn, arg) => {
    if (arg && typeof arg === 'object' && 'max' in arg) {
      return {
        visibleText: '欢迎 搜索',
        elements: [
          { selector: '#kw', tag: 'input', type: 'text', role: '', text: '', ariaLabel: '搜索', href: '', enabled: true, visible: true, bbox: { x: 0, y: 0, width: 10, height: 10 } },
          { selector: 'button.search', tag: 'button', type: '', role: 'button', text: '搜索', ariaLabel: '', href: '', enabled: true, visible: true, bbox: { x: 0, y: 0, width: 10, height: 10 } },
          { selector: 'a.about', tag: 'a', type: '', role: 'link', text: '关于', ariaLabel: '', href: 'https://a.test/about', enabled: true, visible: true, bbox: { x: 0, y: 0, width: 10, height: 10 } },
        ],
      };
    }
    return '结果列表';
  },
};
const page = {
  url: () => currentUrl,
  goto: async (url) => { currentUrl = url; },
  waitForLoadState: async () => {},
  waitForTimeout: async () => {},
  title: async () => '首页',
  mainFrame: () => fakeFrame,
  locator: fakeLocator,
  screenshot: async ({ path: target }) => { fs.writeFileSync(target, 'fake-png'); },
  on: () => {},
};

const patrol = require(path.join(__dirname, '..', '..', 'main', 'resources', 'patrol', 'patrol.js'));

function readEvents() {
  return fs.readFileSync(path.join(temp, 'events.jsonl'), 'utf8')
    .split('\n').filter(Boolean).map((line) => JSON.parse(line));
}

(async () => {
  const ok = await patrol.runExecution(page);
  if (!ok) throw new Error('runExecution returned false');
  const events = readEvents();
  const actions = events.filter((event) => event.type === 'ACTION');
  const caseEnd = events.find((event) => event.type === 'CASE_END');
  if (actions.length !== 4) throw new Error(`expected 4 actions, got ${actions.length}`);
  if (actions[0].caseId !== 1 || actions[0].caseStepSeq !== 1) throw new Error('case step mapping failed');
  if (actions[3].status !== 'SKIPPED') throw new Error(`dangerous step should be skipped, got ${actions[3].status}`);
  if (!caseEnd || caseEnd.status !== 'SUCCESS') throw new Error(`case should succeed, got ${caseEnd && caseEnd.status}`);
  console.log('EXECUTE mode events ok:', actions.map((action) => `${action.caseStepSeq}:${action.status}`).join(','));

  patrol.resetForTest();
  currentUrl = 'https://a.test/';
  await patrol.runDiscovery(page);
  const discoveryEvents = readEvents();
  const visits = discoveryEvents.filter((event) => event.type === 'PAGE_VISIT');
  const discovered = discoveryEvents.filter((event) => event.type === 'PAGE_DISCOVERED');
  if (visits.length !== 2) throw new Error(`expected 2 page visits, got ${visits.length}`);
  if (!visits[0].snapshot || visits[0].snapshot.elements.length !== 3) throw new Error('snapshot missing elements');
  if (!discovered.some((event) => event.page.key === 'https://a.test/about')) {
    throw new Error('link discovery failed');
  }
  console.log('DISCOVER mode events ok:', visits.map((visit) => visit.page.key).join(','));

  console.log('PATROL RUNNER SMOKE TEST PASSED');
})().catch((error) => {
  console.error('PATROL RUNNER SMOKE TEST FAILED:', error);
  process.exit(1);
});
