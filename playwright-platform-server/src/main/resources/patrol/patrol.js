/*
 * 巡页执行引擎（在 Playwright Runner 容器内执行）。
 *
 * SNAPSHOT：只打开入口页，输出可见文本、可交互元素、截图与控制台错误。
 * EXECUTE ：读取 cases.json，逐条用例独立 Context 执行（录屏）；
 *           断言步骤截图；失败用例落控制台 JSON 与 DOM HTML；
 *           未授权的写/破坏性操作被步骤守卫与网络护栏双重拦截。
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { chromium, firefox, webkit } = require('playwright-core');

const configPath = process.env.PATROL_CONFIG || 'config.json';
const eventsPath = process.env.PATROL_EVENTS || 'events.jsonl';
const screenshotDir = process.env.PATROL_SCREENSHOT_DIR || 'screenshots';
const videoDir = process.env.PATROL_VIDEO_DIR || 'videos';
const evidenceDir = process.env.PATROL_EVIDENCE_DIR || 'evidence';
const casesPath = process.env.PATROL_CASES || 'cases.json';

const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
const limits = Object.assign(
  {
    pageTimeoutMs: 20000,
    actionTimeoutMs: 5000,
    settleMs: 600,
    visibleTextLimit: 1800,
    maxDurationMs: 15 * 60 * 1000,
    scrollScan: true,
    recordVideo: true,
    allowWrite: false,
    allowDestructive: false,
    approvedCaseIds: [],
  },
  config.limits || {},
);

const mode = (config.mode || 'SNAPSHOT').toUpperCase() === 'EXECUTE' ? 'EXECUTE' : 'SNAPSHOT';
const startUrl = config.startUrl;
const startOrigin = new URL(startUrl).origin;
const browserType = (config.browser || 'chromium').toLowerCase();
const engineType = { chrome: 'chromium', edge: 'chromium' }[browserType] || browserType;
const headless = config.headless === undefined ? true : Boolean(config.headless);
const auth = config.auth || { mode: 'NONE' };
// 补充入口的前置步骤：进入页面后先执行，再快照/跑用例。
const setupSteps = Array.isArray(config.setupSteps) ? config.setupSteps.filter(Boolean) : [];
const startedAt = Date.now();

fs.mkdirSync(screenshotDir, { recursive: true });
fs.mkdirSync(videoDir, { recursive: true });
fs.mkdirSync(evidenceDir, { recursive: true });
fs.writeFileSync(eventsPath, '');

const stats = { pages: 0, steps: 0, success: 0, failed: 0, skipped: 0, cases: 0 };
const stopReasons = new Set();

function nowIso() {
  return new Date().toISOString();
}

function writeEvent(type, payload) {
  try {
    fs.appendFileSync(eventsPath, JSON.stringify(Object.assign({ type, ts: nowIso() }, payload || {})) + '\n');
  } catch (error) {
    console.error('failed to write event', type, error.message);
  }
}

function shortMessage(error) {
  const text = error && error.message ? String(error.message) : String(error || '');
  const firstLine = text.split('\n')[0].trim();
  return firstLine.length > 500 ? firstLine.slice(0, 500) : firstLine;
}

function normalizeUrl(raw, base) {
  try {
    const url = new URL(raw, base || startUrl);
    url.hash = '';
    return url.toString();
  } catch (error) {
    return String(raw || '');
  }
}

function isSameOrigin(raw) {
  try {
    return new URL(raw, startUrl).origin === startOrigin;
  } catch (error) {
    return false;
  }
}

function pageUrl(page) {
  return normalizeUrl(page.url());
}

function normalizeRisk(step) {
  const raw = String(step.risk || step.operation || '').trim().toUpperCase();
  if (['READ', 'WRITE', 'DESTRUCTIVE', 'AUTH'].includes(raw)) {
    return raw;
  }
  const action = String(step.action || '').toLowerCase();
  const mutating = [
    'click', 'dblclick', 'rightclick', 'tap', 'fill', 'type', 'press', 'clear',
    'check', 'uncheck', 'set_checked', 'select', 'drag_to',
  ];
  return mutating.includes(action) ? 'WRITE' : 'READ';
}

function isCaseInApproval(caseId) {
  const ids = Array.isArray(limits.approvedCaseIds) ? limits.approvedCaseIds.map(Number) : [];
  return ids.length === 0 || ids.includes(Number(caseId));
}

/** 步骤级授权：READ 直接放行；WRITE/AUTH 需 allowWrite；DESTRUCTIVE 需 allowDestructive。 */
function stepAllowed(caseItem, step) {
  if (!isCaseInApproval(caseItem.caseId)) {
    return false;
  }
  const risk = normalizeRisk(step);
  if (risk === 'READ') {
    return true;
  }
  if (risk === 'DESTRUCTIVE') {
    return Boolean(limits.allowDestructive);
  }
  return Boolean(limits.allowWrite);
}

function caseMayMutate(caseItem) {
  return (Array.isArray(caseItem.steps) ? caseItem.steps : []).some((step) => {
    return normalizeRisk(step) !== 'READ' && stepAllowed(caseItem, step);
  });
}

async function settle(page) {
  try {
    await page.waitForLoadState('domcontentloaded', { timeout: limits.pageTimeoutMs });
  } catch (error) {
    // 页面可能一直处于加载状态，继续用固定等待兜底。
  }
  await page.waitForTimeout(limits.settleMs).catch(() => {});
}

async function preScanScroll(page) {
  if (!limits.scrollScan) {
    return;
  }
  try {
    await page.mainFrame().evaluate(async () => {
      const step = Math.max(200, Math.floor(window.innerHeight * 0.8));
      const pageHeight = Math.min(
        Math.max(
          document.body ? document.body.scrollHeight : 0,
          document.documentElement ? document.documentElement.scrollHeight : 0,
        ),
        12000,
      );
      let offset = 0;
      let guard = 0;
      while (offset < pageHeight && guard < 20) {
        window.scrollTo(0, offset);
        await new Promise((resolve) => setTimeout(resolve, 150));
        offset += step;
        guard += 1;
      }
      window.scrollTo(0, 0);
      await new Promise((resolve) => setTimeout(resolve, 100));
    });
  } catch (error) {
    // 滚动失败不影响后续采集。
  }
}

/**
 * 静态深度采集：递归遍历主文档、开放 Shadow DOM 与同源 iframe 的全部层级，
 * 包含未打开弹层中已存在的元素（visible=false），并记录 frame 链与弹层深度。
 * 不主动点击，避免快照阶段产生副作用。
 */
async function collectElements(page) {
  try {
    return await page.mainFrame().evaluate(() => {
      const selectorList = [
        'a', 'button', 'input', 'select', 'textarea', 'summary',
        '[contenteditable="true"]', '[tabindex]', '[onclick]', '[aria-haspopup]',
        '[draggable="true"]', 'audio[controls]', 'video[controls]',
        '[role="button"]', '[role="link"]', '[role="tab"]', '[role="menuitem"]',
        '[role="menuitemcheckbox"]', '[role="menuitemradio"]', '[role="checkbox"]',
        '[role="switch"]', '[role="radio"]', '[role="combobox"]', '[role="listbox"]',
        '[role="option"]', '[role="slider"]', '[role="spinbutton"]', '[role="searchbox"]',
        '[role="textbox"]', '[role="treeitem"]', '[role="gridcell"]',
      ].join(',');

      const seenElements = new Set();
      const seenRoots = new Set();
      const collected = [];

      function escapeId(element, id) {
        const css = element.ownerDocument && element.ownerDocument.defaultView
          ? element.ownerDocument.defaultView.CSS : window.CSS;
        return css && css.escape ? css.escape(id) : id;
      }

      function segmentPath(element, boundary) {
        const rootDocument = boundary.nodeType === 9
          ? boundary : (boundary.ownerDocument || document);
        const parts = [];
        let current = element;
        while (current && current.nodeType === 1
          && current !== boundary && current !== rootDocument.body && current !== rootDocument.documentElement) {
          if (current.id) {
            parts.unshift('#' + escapeId(current, current.id));
            break;
          }
          let part = current.tagName.toLowerCase();
          const parent = current.parentElement;
          const siblings = parent
            ? Array.from(parent.children || []).filter((item) => item.tagName === current.tagName)
            : [current];
          part += `:nth-of-type(${Math.max(1, siblings.indexOf(current) + 1)})`;
          parts.unshift(part);
          current = current.parentElement;
        }
        return parts.join(' > ');
      }

      function fullSelector(element, boundary, shadowChain) {
        const local = segmentPath(element, boundary);
        if (!local) {
          return '';
        }
        if (shadowChain.length === 0) {
          return local;
        }
        return shadowChain.concat([local]).map((part) => 'css=' + part).join(' >> ');
      }

      function layerInfo(element) {
        let depth = 0;
        let container = '';
        let current = element;
        while (current && current.nodeType === 1) {
          const tag = current.tagName.toLowerCase();
          const role = (current.getAttribute('role') || '').toLowerCase();
          const className = typeof current.className === 'string' ? current.className : '';
          const layerLike = tag === 'dialog' || current.getAttribute('aria-modal') === 'true'
            || ['dialog', 'alertdialog', 'menu', 'listbox', 'tree', 'grid', 'tooltip', 'popover']
              .includes(role)
            || /(modal|popup|dropdown|overlay|picker|popper|dialog|drawer|datepicker|timepicker)/i
              .test(className);
          if (layerLike) {
            depth += 1;
            if (!container) {
              container = tag + (role ? '[' + role + ']' : '');
            }
          }
          current = current.parentElement
            || (current.getRootNode && current.getRootNode().host)
            || null;
        }
        return { depth, container };
      }

      function visibleInfo(element) {
        const view = element.ownerDocument && element.ownerDocument.defaultView
          ? element.ownerDocument.defaultView : window;
        const style = view.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        const visible = style.display !== 'none' && style.visibility !== 'hidden'
          && Number(style.opacity || 1) > 0 && rect.width > 1 && rect.height > 1;
        const disabled = element.disabled === true || style.pointerEvents === 'none';
        return { visible, disabled, rect };
      }

      function pushElement(element, boundary, context) {
        if (seenElements.has(element)) {
          return;
        }
        seenElements.add(element);
        const info = visibleInfo(element);
        const layer = layerInfo(element);
        const tag = element.tagName.toLowerCase();
        const text = (element.innerText || element.value || element.getAttribute('aria-label')
          || element.getAttribute('title') || '').replace(/\s+/g, ' ').trim().slice(0, 120);
        collected.push({
          selector: fullSelector(element, boundary, context.shadowChain) || ':scope',
          frame: context.framePath,
          tag,
          role: element.getAttribute('role') || '',
          text,
          name: element.getAttribute('name') || '',
          type: element.getAttribute('type') || '',
          href: tag === 'a' ? (element.href || '') : '',
          visible: info.visible,
          enabled: !info.disabled,
          layerDepth: layer.depth,
          layerContainer: layer.container,
          shadowDepth: context.shadowChain.length,
          bbox: {
            x: Math.round(info.rect.x), y: Math.round(info.rect.y),
            w: Math.round(info.rect.width), h: Math.round(info.rect.height),
          },
        });
      }

      function walk(root, context) {
        if (!root || seenRoots.has(root)) {
          return;
        }
        seenRoots.add(root);
        let matches = [];
        try {
          matches = root.querySelectorAll(selectorList);
        } catch (error) {
          matches = [];
        }
        for (const element of matches) {
          pushElement(element, root, context);
        }

        let descendants = [];
        try {
          descendants = root.querySelectorAll('*');
        } catch (error) {
          descendants = [];
        }
        for (const element of descendants) {
          if (element.shadowRoot) {
            const hostLocal = segmentPath(element, root);
            walk(element.shadowRoot, {
              framePath: context.framePath,
              shadowChain: hostLocal
                ? context.shadowChain.concat([hostLocal]) : context.shadowChain,
            });
          }
          const tag = element.tagName.toLowerCase();
          if (tag === 'iframe' || tag === 'frame') {
            let childDocument = null;
            try {
              childDocument = element.contentDocument;
            } catch (error) {
              childDocument = null;
            }
            if (childDocument && childDocument.documentElement) {
              const frameSelector = fullSelector(element, root, context.shadowChain);
              if (frameSelector) {
                walk(childDocument, {
                  framePath: context.framePath.concat([frameSelector]),
                  shadowChain: [],
                });
              }
            }
          }
        }
      }

      walk(document, { framePath: [], shadowChain: [] });

      collected.sort((first, second) => {
        const score = (item) => (item.visible ? 4 : 0) + (item.enabled ? 2 : 0)
          + (item.text ? 1 : 0) + (item.layerDepth > 0 ? 1 : 0);
        return score(second) - score(first);
      });
      return collected;
    });
  } catch (error) {
    return [];
  }
}

async function buildSnapshot(page) {
  const elements = await collectElements(page);
  const visible = await page.mainFrame()
    .evaluate((limit) => {
      const raw = (document.body ? document.body.innerText : '')
        .replace(/\s+/g, ' ').trim();
      return { text: raw.slice(0, limit), length: raw.length };
    }, limits.visibleTextLimit)
    .catch(() => ({ text: '', length: 0 }));
  const visibleText = visible.text || '';
  const visibleTextLength = visible.length || 0;
  return {
    visibleText,
    visibleTextLength,
    visibleTextTruncated: visibleTextLength > visibleText.length,
    elements,
  };
}

function buildCookies() {
  if (auth.mode !== 'COOKIE' || !auth.cookie) {
    return [];
  }
  const raw = String(auth.cookie).trim();
  if (raw.startsWith('[')) {
    return JSON.parse(raw).map((cookie) => {
      const result = Object.assign({}, cookie);
      if (!result.url && !result.domain) {
        result.url = startUrl;
      }
      return result;
    });
  }
  return raw
    .split(';')
    .map((pair) => {
      const index = pair.indexOf('=');
      if (index <= 0) {
        return null;
      }
      return { name: pair.slice(0, index).trim(), value: pair.slice(index + 1).trim(), url: startUrl };
    })
    .filter((cookie) => cookie && cookie.name);
}

/**
 * 录屏鼠标层：在页面内绘制当前鼠标位置、移动轨迹与点击涟漪。
 * 通过 addInitScript 注入到所有 frame，Playwright 录屏会把它合成进视频。
 */
function cursorOverlayScript() {
  if (window.__patrolCursor) {
    return;
  }
  var state = { visible: true, points: [], ripples: [] };
  state.setVisible = function (visible) {
    state.visible = visible;
    if (state.host) {
      state.host.style.display = visible ? '' : 'none';
    }
  };
  window.__patrolCursor = state;

  function mount() {
    if (!document.body || !document.documentElement) {
      setTimeout(mount, 50);
      return;
    }
    var host = document.createElement('div');
    host.setAttribute('data-patrol-cursor', '1');
    host.style.cssText = 'position:fixed;inset:0;z-index:2147483647;pointer-events:none;';
    var canvas = document.createElement('canvas');
    canvas.style.cssText = 'display:block;width:100%;height:100%;';
    host.appendChild(canvas);
    host.style.display = state.visible ? '' : 'none';
    document.documentElement.appendChild(host);
    state.host = host;

    var ctx = canvas.getContext('2d');
    function resize() {
      var dpr = window.devicePixelRatio || 1;
      canvas.width = Math.max(1, Math.floor(window.innerWidth * dpr));
      canvas.height = Math.max(1, Math.floor(window.innerHeight * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    resize();
    window.addEventListener('resize', resize);

    var last = null;
    function record(x, y, clicked) {
      var now = performance.now();
      last = { x: x, y: y };
      state.points.push({ x: x, y: y, t: now });
      if (state.points.length > 90) {
        state.points.shift();
      }
      if (clicked) {
        state.ripples.push({ x: x, y: y, t: now });
      }
    }
    ['mousemove', 'pointermove'].forEach(function (name) {
      window.addEventListener(name, function (event) {
        record(event.clientX, event.clientY, false);
      }, true);
    });
    ['mousedown', 'pointerdown'].forEach(function (name) {
      window.addEventListener(name, function (event) {
        record(event.clientX, event.clientY, true);
      }, true);
    });
    ['touchstart', 'touchmove'].forEach(function (name) {
      window.addEventListener(name, function (event) {
        var touch = event.touches && event.touches[0];
        if (touch) {
          record(touch.clientX, touch.clientY, name === 'touchstart');
        }
      }, true, { passive: true });
    });

    function draw() {
      requestAnimationFrame(draw);
      if (!state.visible) {
        return;
      }
      var now = performance.now();
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
      state.points = state.points.filter(function (point) {
        return now - point.t <= 1400;
      });
      state.points.forEach(function (point) {
        var alpha = Math.max(0, 1 - (now - point.t) / 1400);
        ctx.beginPath();
        ctx.arc(point.x, point.y, 2.4, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(56,189,248,' + (alpha * 0.45).toFixed(3) + ')';
        ctx.fill();
      });
      state.ripples = state.ripples.filter(function (ripple) {
        return now - ripple.t <= 900;
      });
      state.ripples.forEach(function (ripple) {
        var progress = (now - ripple.t) / 900;
        ctx.beginPath();
        ctx.arc(ripple.x, ripple.y, 8 + progress * 26, 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(251,146,60,' + (0.55 * (1 - progress)).toFixed(3) + ')';
        ctx.lineWidth = 2;
        ctx.stroke();
      });
      if (last) {
        ctx.beginPath();
        ctx.arc(last.x, last.y, 11, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(15,23,42,0.22)';
        ctx.fill();
        ctx.beginPath();
        ctx.arc(last.x, last.y, 9, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(255,255,255,0.95)';
        ctx.fill();
        ctx.beginPath();
        ctx.arc(last.x, last.y, 5.5, 0, Math.PI * 2);
        ctx.fillStyle = '#2563eb';
        ctx.fill();
        ctx.beginPath();
        ctx.arc(last.x, last.y, 10, 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(15,23,42,0.7)';
        ctx.lineWidth = 1.5;
        ctx.stroke();
      }
    }
    draw();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mount);
  } else {
    mount();
  }
}

async function setCursorVisible(page, visible) {
  await Promise.all(page.frames().map((frame) => frame.evaluate((value) => {
    if (window.__patrolCursor) {
      window.__patrolCursor.setVisible(value);
    }
  }, visible).catch(() => {})));
}

/** 截图前隐藏鼠标层，避免光标轨迹影响视觉判定。 */
async function captureScreenshot(page, options) {
  await setCursorVisible(page, false);
  try {
    return await page.screenshot(options);
  } finally {
    await setCursorVisible(page, true);
  }
}

/**
 * 把目标元素滚动到视口中央（会自动处理弹层、表格等内层滚动容器），
 * 确保元素操作与断言发生时，截图和录屏都能拍到目标元素。
 */
async function revealElement(page, locator) {
  try {
    await locator.evaluate((element) => {
      if (element && typeof element.scrollIntoView === 'function') {
        element.scrollIntoView({ block: 'center', inline: 'center' });
      }
    });
  } catch (error) {
    await locator.scrollIntoViewIfNeeded({ timeout: limits.actionTimeoutMs }).catch(() => {});
  }
  await page.waitForTimeout(200).catch(() => {});
}

async function newContext(browser, withVideo) {
  const options = {
    viewport: { width: 1280, height: 900 },
    hasTouch: true,
    ignoreHTTPSErrors: true,
  };
  const token = auth.mode === 'TOKEN' && auth.token ? String(auth.token) : null;
  if (token) {
    options.extraHTTPHeaders = { [auth.tokenHeader || 'Authorization']: token };
  }
  if (withVideo && limits.recordVideo) {
    options.recordVideo = { dir: videoDir, size: { width: 1280, height: 900 } };
  }
  const context = await browser.newContext(options);
  if (withVideo && limits.recordVideo) {
    await context.addInitScript(cursorOverlayScript);
  }
  const cookies = buildCookies();
  if (cookies.length > 0) {
    await context.addCookies(cookies);
  }
  if (auth.mode === 'LOCAL_STORAGE' && auth.localStorage) {
    await context.addInitScript((values) => {
      try {
        Object.keys(values).forEach((key) => window.localStorage.setItem(key, String(values[key])));
      } catch (error) {
        // 某些页面禁止访问 localStorage，忽略即可。
      }
    }, auth.localStorage);
  }
  return context;
}

/** 重放补充入口的前置步骤；失败时返回原因，由调用方决定 SKIP 还是中断。 */
async function runSetupSteps(page, caseId) {
  if (setupSteps.length === 0) {
    return { ok: true };
  }
  for (let index = 0; index < setupSteps.length; index += 1) {
    const step = Object.assign({ seq: index + 1 }, setupSteps[index]);
    const result = await runStep(page, step);
    if (result.status !== 'SUCCESS') {
      writeEvent('SETUP_STEP', {
        caseId: caseId === undefined ? null : caseId,
        seq: index + 1,
        action: step.action,
        selector: step.selector || null,
        status: 'FAILED',
        message: result.message || '',
      });
      return { ok: false, message: result.message || '前置步骤执行失败' };
    }
    writeEvent('SETUP_STEP', {
      caseId: caseId === undefined ? null : caseId,
      seq: index + 1,
      action: step.action,
      selector: step.selector || null,
      status: 'SUCCESS',
    });
    await settle(page);
  }
  writeEvent('SETUP_DONE', { steps: setupSteps.length });
  return { ok: true };
}

async function runSnapshot(browser) {
  const context = await newContext(browser, false);
  const page = await context.newPage();
  page.setDefaultTimeout(limits.actionTimeoutMs);
  let consoleErrors = 0;
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors += 1;
    }
  });
  page.on('pageerror', () => {
    consoleErrors += 1;
  });

  try {
    await page.goto(startUrl, { waitUntil: 'domcontentloaded', timeout: limits.pageTimeoutMs });
    await settle(page);
    const setup = await runSetupSteps(page);
    if (!setup.ok) {
      writeEvent('SETUP_FAILED', { message: setup.message });
      return false;
    }
    await preScanScroll(page);
    await settle(page);
    const title = await page.title().catch(() => '');
    const screenshot = 'screenshots/snapshot.png';
    await page.screenshot({ path: path.join(screenshotDir, 'snapshot.png'), fullPage: true, timeout: 15000 })
      .catch(() => {});
    const snapshot = await buildSnapshot(page);
    writeEvent('PAGE_SNAPSHOT', {
      page: { url: pageUrl(page), title, consoleErrors },
      snapshot,
      screenshot,
    });
    return true;
  } catch (error) {
    console.error('snapshot failed', shortMessage(error));
    return false;
  } finally {
    await context.close().catch(() => {});
  }
}

/** 按步骤的 frame 链定位作用域：逐层 frameLocator，兼容 Shadow DOM 与同源 iframe。 */
function scopeOf(page, step) {
  const framePath = Array.isArray(step.frame) ? step.frame.filter(Boolean) : [];
  let scope = page;
  for (const frameSelector of framePath) {
    scope = scope.frameLocator(frameSelector);
  }
  return scope;
}

function normalizeText(text) {
  return String(text || '').replace(/\s+/g, ' ').trim();
}

function expectContains(actual, expected, label) {
  const normalized = normalizeText(actual);
  if (!normalized.includes(normalizeText(expected))) {
    throw new Error(`${label}未匹配: ${expected}`);
  }
}

async function runStep(page, step, beforeAct) {
  const action = String(step.action || '').toLowerCase();
  const selector = step.selector || '';
  const value = step.value === undefined || step.value === null ? '' : String(step.value);
  const startedStepAt = Date.now();

  try {
    const scope = scopeOf(page, step);
    const locator = selector ? scope.locator(selector).first() : null;
    const mustExist = [
      'click', 'dblclick', 'rightclick', 'tap', 'fill', 'type', 'clear',
      'check', 'uncheck', 'set_checked', 'select', 'hover', 'focus', 'blur',
      'scroll_into_view', 'drag_to', 'assert_visible', 'assert_value',
      'assert_checked', 'assert_attribute',
    ].includes(action) || (action === 'assert_text' && Boolean(selector));
    if (mustExist) {
      if (!selector) {
        throw new Error(`${action} 缺少 selector`);
      }
      const count = await locator.count();
      if (count === 0) {
        throw new Error(`元素不存在或已失效: ${selector}`);
      }
    }
    if (['assert_hidden', 'assert_count'].includes(action) && !selector) {
      throw new Error(`${action} 缺少 selector`);
    }

    // 所有需要定位元素的操作/断言都先把元素滚到视口中央，避免“操作成功但截图/录屏里看不到”。
    if (mustExist && locator) {
      await revealElement(page, locator);
      if (typeof beforeAct === 'function') {
        await beforeAct();
      }
    }

    switch (action) {
      case 'click':
        await locator.click({ timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'dblclick':
        await locator.dblclick({ timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'rightclick':
        await locator.click({ button: 'right', timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'tap':
        await locator.tap({ timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'fill':
        await locator.fill(value || 'patrol-test', { timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'type':
        if (typeof locator.pressSequentially === 'function') {
          await locator.pressSequentially(value, { timeout: limits.actionTimeoutMs });
        } else {
          await locator.type(value, { timeout: limits.actionTimeoutMs });
        }
        await settle(page);
        break;
      case 'press':
        if (locator) {
          await locator.press(value || 'Enter', { timeout: limits.actionTimeoutMs });
        } else {
          await page.keyboard.press(value || 'Enter');
        }
        await settle(page);
        break;
      case 'clear':
        await locator.fill('', { timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'check':
        await locator.check({ timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'uncheck':
        await locator.uncheck({ timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'set_checked':
        await locator.setChecked(!['false', '0', ''].includes(value.toLowerCase()), { timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      case 'select':
        try {
          await locator.selectOption(value, { timeout: limits.actionTimeoutMs });
        } catch (selectError) {
          try {
            await locator.selectOption({ label: value }, { timeout: limits.actionTimeoutMs });
          } catch (labelError) {
            await locator.selectOption({ index: Number(value) || 0 }, { timeout: limits.actionTimeoutMs });
          }
        }
        await settle(page);
        break;
      case 'hover':
        await locator.hover({ timeout: limits.actionTimeoutMs });
        await page.waitForTimeout(300);
        break;
      case 'focus':
        await locator.focus({ timeout: limits.actionTimeoutMs });
        break;
      case 'blur':
        await locator.evaluate((element) => element.blur());
        break;
      case 'scroll': {
        const delta = Number(value);
        await page.mouse.wheel(0, Number.isFinite(delta) && delta !== 0 ? delta : 800);
        await page.waitForTimeout(300);
        break;
      }
      case 'scroll_into_view':
        await locator.scrollIntoViewIfNeeded({ timeout: limits.actionTimeoutMs });
        await page.waitForTimeout(200);
        break;
      case 'drag_to': {
        const targetSelector = step.target || value;
        if (!targetSelector) {
          throw new Error('drag_to 缺少目标 selector');
        }
        await locator.dragTo(scope.locator(targetSelector).first(), { timeout: limits.actionTimeoutMs });
        await settle(page);
        break;
      }
      case 'goto': {
        const target = normalizeUrl(value);
        if (!isSameOrigin(target)) {
          throw new Error(`跨域跳转被拒绝: ${target}`);
        }
        await page.goto(target, { waitUntil: 'domcontentloaded', timeout: limits.pageTimeoutMs });
        await settle(page);
        break;
      }
      case 'go_back':
        await page.goBack({ waitUntil: 'domcontentloaded', timeout: limits.pageTimeoutMs });
        await settle(page);
        break;
      case 'go_forward':
        await page.goForward({ waitUntil: 'domcontentloaded', timeout: limits.pageTimeoutMs });
        await settle(page);
        break;
      case 'reload':
        await page.reload({ waitUntil: 'domcontentloaded', timeout: limits.pageTimeoutMs });
        await settle(page);
        break;
      case 'wait':
        await page.waitForTimeout(Number(value) || 500);
        break;
      case 'wait_for_selector':
        await scope.locator(selector).first().waitFor({ state: 'visible', timeout: limits.actionTimeoutMs });
        break;
      case 'wait_for_url': {
        const pattern = value || selector;
        await page.waitForURL((url) => url.toString().includes(pattern), { timeout: limits.actionTimeoutMs });
        break;
      }
      case 'wait_for_load_state':
        await page.waitForLoadState(value || 'load', { timeout: limits.pageTimeoutMs });
        break;
      case 'assert_visible':
        await locator.waitFor({ state: 'visible', timeout: limits.actionTimeoutMs });
        break;
      case 'assert_hidden':
        await locator.waitFor({ state: 'hidden', timeout: limits.actionTimeoutMs });
        break;
      case 'assert_text': {
        const expected = value || step.label || '';
        if (!expected) {
          throw new Error('assert_text 缺少期望文本');
        }
        let actual;
        if (locator) {
          actual = await locator.innerText({ timeout: limits.actionTimeoutMs });
        } else if (Array.isArray(step.frame) && step.frame.length > 0) {
          actual = await scope.locator('body').first().innerText({ timeout: limits.actionTimeoutMs });
        } else {
          actual = await page.mainFrame().evaluate(() => (document.body ? document.body.innerText : ''));
        }
        expectContains(actual, expected, '期望文本');
        break;
      }
      case 'assert_value': {
        const actual = await locator.inputValue({ timeout: limits.actionTimeoutMs });
        const expected = value === '' ? step.label || '' : value;
        if (!expected) {
          throw new Error('assert_value 缺少期望值');
        }
        expectContains(actual, expected, '输入框内容');
        break;
      }
      case 'assert_checked': {
        const checked = await locator.isChecked({ timeout: limits.actionTimeoutMs });
        const expected = !['false', '0', ''].includes(value.toLowerCase());
        if (checked !== expected) {
          throw new Error(`选中状态不符: 期望 ${expected}，实际 ${checked}`);
        }
        break;
      }
      case 'assert_count': {
        const expected = Number(value || step.label);
        if (!Number.isFinite(expected)) {
          throw new Error('assert_count 缺少期望数量');
        }
        const actual = await locator.count();
        if (actual !== expected) {
          throw new Error(`元素数量不符: 期望 ${expected}，实际 ${actual}`);
        }
        break;
      }
      case 'assert_url':
        expectContains(page.url(), value || selector, '页面地址');
        break;
      case 'assert_title': {
        const title = await page.title();
        expectContains(title, value || selector, '页面标题');
        break;
      }
      case 'assert_attribute': {
        const name = step.attribute || step.name || '';
        if (!name) {
          throw new Error('assert_attribute 缺少属性名');
        }
        const actual = await locator.getAttribute(name, { timeout: limits.actionTimeoutMs });
        const expected = value === '' ? step.label || '' : value;
        if (expected === '') {
          if (actual === null) {
            throw new Error(`属性不存在: ${name}`);
          }
          break;
        }
        // 快照里的 href/src 是浏览器解析后的绝对地址，而 DOM attribute 往往仍是相对值（如 "/"），
        // 直接比对会把正确页面判成失败；这里先比原始属性，再回退比解析后的属性值。
        if (!normalizeText(actual).includes(normalizeText(expected))) {
          const resolved = await locator.evaluate((element, attr) => {
            const raw = element[attr];
            if (typeof raw === 'string') {
              return raw;
            }
            if (raw && typeof raw === 'object' && 'baseVal' in raw) {
              return raw.baseVal;
            }
            return '';
          }, name);
          if (!normalizeText(resolved).includes(normalizeText(expected))) {
            expectContains(actual || '', expected, '属性值');
          }
        }
        break;
      }
      default:
        throw new Error(`不支持的动作: ${action}`);
    }
    return { status: 'SUCCESS', message: '', durationMs: Date.now() - startedStepAt };
  } catch (error) {
    return { status: 'FAILED', message: shortMessage(error), durationMs: Date.now() - startedStepAt };
  }
}

async function runExecution(browser) {
  if (!fs.existsSync(casesPath)) {
    writeEvent('RUN_END', { status: 'FAILED', reason: '未找到 cases.json', counts: stats });
    return false;
  }
  let cases;
  try {
    cases = JSON.parse(fs.readFileSync(casesPath, 'utf8'));
  } catch (error) {
    writeEvent('RUN_END', { status: 'FAILED', reason: 'cases.json 解析失败', counts: stats });
    return false;
  }
  if (!Array.isArray(cases) || cases.length === 0) {
    writeEvent('RUN_END', { status: 'COMPLETED', reason: '没有可执行用例', counts: stats });
    return true;
  }
  for (const caseItem of cases) {
    if (Date.now() - startedAt >= limits.maxDurationMs) {
      stopReasons.add('达到总时长预算，未执行剩余用例');
      break;
    }
    stats.cases += 1;
    writeEvent('CASE_START', {
      caseId: caseItem.caseId,
      name: caseItem.name,
      worstRisk: caseItem.worstRisk || '',
    });

    const consoleMessages = [];
    const requests = [];
    let consoleErrors = 0;
    let caseStatus = 'SUCCESS';
    let caseMessage = '';
    let videoFile = null;
    let evidenceConsole = null;
    let evidenceDom = null;
    let finalScreenshot = null;
    let visibleText = '';
    let visibleTextLength = 0;
    let visibleTextTruncated = false;
    const caseStartedAt = Date.now();

    const context = await newContext(browser, true);
    const page = await context.newPage();
    page.setDefaultTimeout(limits.actionTimeoutMs);
    page.on('console', (message) => {
      const item = { type: message.type(), text: message.text().slice(0, 500), ts: nowIso() };
      consoleMessages.push(item);
      if (message.type() === 'error') {
        consoleErrors += 1;
      }
    });
    page.on('pageerror', (error) => {
      consoleMessages.push({ type: 'pageerror', text: shortMessage(error), ts: nowIso() });
      consoleErrors += 1;
    });

    const mutationAllowed = caseMayMutate(caseItem);
    await context.route('**/*', async (route) => {
      const request = route.request();
      const method = request.method();
      if (['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        return route.continue();
      }
      const url = request.url();
      if (!isSameOrigin(url)) {
        return route.continue();
      }
      if (mutationAllowed) {
        requests.push({ method, url, blocked: false });
        return route.continue();
      }
      requests.push({ method, url, blocked: true });
      writeEvent('BLOCKED_REQUEST', {
        caseId: caseItem.caseId,
        method,
        url,
        reason: '未授权的变更请求',
      });
      return route.abort('blockedbyclient');
    });

    try {
      await page.goto(startUrl, { waitUntil: 'domcontentloaded', timeout: limits.pageTimeoutMs });
      await settle(page);

      const setup = await runSetupSteps(page, caseItem.caseId);
      if (!setup.ok) {
        caseStatus = 'SKIPPED';
        caseMessage = `起始状态不可达：${setup.message}`;
      }

      const steps = setup.ok && Array.isArray(caseItem.steps) ? caseItem.steps : [];
      let blockedByGuard = false;
      for (const step of steps) {
        if (Date.now() - startedAt >= limits.maxDurationMs) {
          stopReasons.add('达到总时长预算，未执行剩余步骤');
          caseStatus = 'SKIPPED';
          caseMessage = '达到总时长预算，未执行剩余步骤';
          break;
        }
        if (!stepAllowed(caseItem, step)) {
          const risk = normalizeRisk(step);
          const message = `${risk} 操作未授权，已拦截`;
          writeEvent('CASE_STEP', {
            caseId: caseItem.caseId,
            seq: step.seq,
            action: step.action,
            status: 'BLOCKED',
            message,
            screenshot: null,
            request: null,
            approved: false,
            durationMs: 0,
          });
          stats.steps += 1;
          stats.skipped += 1;
          blockedByGuard = true;
          caseStatus = 'BLOCKED';
          caseMessage = message;
          break;
        }

        stats.steps += 1;
        const isAssertion = String(step.action || '').toLowerCase().startsWith('assert_');
        const stepShotName = `case-${caseItem.caseId}-step-${step.seq}.png`;
        let screenshot = null;
        const result = await runStep(page, step, async () => {
          // 元素操作前先截图：此时目标已滚动到视口中央，导航类点击也能留下操作现场。
          if (!isAssertion) {
            await captureScreenshot(page, { path: path.join(screenshotDir, stepShotName), timeout: 10000 })
              .then(() => { screenshot = `screenshots/${stepShotName}`; })
              .catch(() => {});
          }
        });
        if (isAssertion) {
          await captureScreenshot(page, { path: path.join(screenshotDir, stepShotName), timeout: 10000 })
            .then(() => { screenshot = `screenshots/${stepShotName}`; })
            .catch(() => {});
        }
        if (!isAssertion && !screenshot) {
          // 无目标元素的动作（goto / 滚动 / 等待等）在执行后补一张，便于追溯页面状态。
          await captureScreenshot(page, { path: path.join(screenshotDir, stepShotName), timeout: 10000 })
            .then(() => { screenshot = `screenshots/${stepShotName}`; })
            .catch(() => {});
        }
        if (result.status === 'FAILED') {
          const failedName = `case-${caseItem.caseId}-step-${step.seq}-failed.png`;
          await captureScreenshot(page, { path: path.join(screenshotDir, failedName), timeout: 10000 }).catch(() => {});
          screenshot = `screenshots/${failedName}`;
          stats.failed += 1;
        } else {
          stats.success += 1;
        }
        const lastRequest = requests.length > 0 ? requests[requests.length - 1] : null;
        writeEvent('CASE_STEP', {
          caseId: caseItem.caseId,
          seq: step.seq,
          action: step.action,
          status: result.status,
          message: result.message,
          screenshot,
          request: lastRequest ? JSON.stringify(lastRequest) : null,
          approved: normalizeRisk(step) === 'READ' || stepAllowed(caseItem, step),
          durationMs: result.durationMs,
        });
        if (result.status === 'FAILED') {
          caseStatus = 'FAILED';
          caseMessage = result.message;
          break;
        }
      }

      finalScreenshot = `screenshots/case-${caseItem.caseId}.png`;
      await captureScreenshot(page, { path: path.join(screenshotDir, `case-${caseItem.caseId}.png`), timeout: 10000 })
        .catch(() => { finalScreenshot = null; });
      const visible = await page.mainFrame()
        .evaluate((limit) => {
          const raw = (document.body ? document.body.innerText : '')
            .replace(/\s+/g, ' ').trim();
          return { text: raw.slice(0, limit), length: raw.length };
        }, limits.visibleTextLimit)
        .catch(() => ({ text: '', length: 0 }));
      visibleText = visible.text || '';
      visibleTextLength = visible.length || 0;
      visibleTextTruncated = visibleTextLength > visibleText.length;

      if (!blockedByGuard && requests.some((item) => item.blocked)) {
        caseStatus = 'BLOCKED';
        caseMessage = '网络护栏拦截了未授权的变更请求';
      }
      if (!blockedByGuard && caseStatus === 'SUCCESS' && requests.some((item) => item.blocked)) {
        caseStatus = 'BLOCKED';
      }
    } catch (error) {
      caseStatus = 'FAILED';
      caseMessage = '用例执行异常: ' + shortMessage(error);
      finalScreenshot = `screenshots/case-${caseItem.caseId}.png`;
      await captureScreenshot(page, { path: path.join(screenshotDir, `case-${caseItem.caseId}.png`), timeout: 10000 })
        .catch(() => {});
    }

    // 失败/拦截用例保留控制台与 DOM；有控制台错误的用例也会被判失败，同样保留。
    if (caseStatus === 'FAILED' || caseStatus === 'BLOCKED' || consoleErrors > 0) {
      evidenceConsole = `evidence/case-${caseItem.caseId}.console.json`;
      fs.writeFileSync(path.join(evidenceDir, `case-${caseItem.caseId}.console.json`), JSON.stringify({
        caseId: caseItem.caseId,
        consoleErrors,
        messages: consoleMessages,
        requests,
      }, null, 2));
      evidenceDom = `evidence/case-${caseItem.caseId}.html`;
      const html = await page.content().catch(() => '');
      fs.writeFileSync(path.join(evidenceDir, `case-${caseItem.caseId}.html`),
        `<!-- url: ${pageUrl(page)} capturedAt: ${nowIso()} -->\n${html}`);
    }

    // 用例结束后再停留 3 秒，让录屏完整记录收尾画面
    if (limits.recordVideo) {
      await page.waitForTimeout(3000).catch(() => {});
    }

    const video = page.video();
    await context.close().catch(() => {});
    if (video && limits.recordVideo) {
      try {
        const source = await video.path();
        const targetName = `case-${caseItem.caseId}.webm`;
        fs.renameSync(source, path.join(videoDir, targetName));
        videoFile = `videos/${targetName}`;
      } catch (error) {
        console.error('save video failed', caseItem.caseId, error.message);
      }
    }

    writeEvent('CASE_END', {
      caseId: caseItem.caseId,
      status: caseStatus,
      message: caseMessage,
      video: videoFile,
      console: evidenceConsole,
      dom: evidenceDom,
      screenshot: finalScreenshot,
      visibleText,
      visibleTextLength,
      visibleTextTruncated,
      durationMs: Date.now() - caseStartedAt,
      consoleErrors,
    });
  }

  const reason = stopReasons.size > 0
    ? Array.from(stopReasons).join('；')
    : '用例执行完成';
  writeEvent('RUN_END', { status: 'COMPLETED', reason, counts: stats });
  return true;
}

async function main() {
  writeEvent('RUN_START', {
    url: startUrl,
    browser: browserType,
    headless,
    authType: auth.mode,
    mode,
    limits,
  });
  const launcher = { chromium, firefox, webkit }[engineType] || chromium;
  const browser = await launcher.launch({
    headless,
    args: engineType === 'chromium' ? ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] : [],
  });
  let completed = true;
  try {
    completed = mode === 'EXECUTE' ? await runExecution(browser) : await runSnapshot(browser);
  } catch (error) {
    writeEvent('RUN_END', {
      status: 'FAILED',
      reason: '巡页执行异常: ' + shortMessage(error),
      counts: stats,
    });
    completed = false;
  } finally {
    await browser.close().catch(() => {});
  }
  if (mode === 'SNAPSHOT') {
    writeEvent('RUN_END', {
      status: completed ? 'COMPLETED' : 'FAILED',
      reason: completed ? '入口页快照完成' : '入口页快照失败',
      counts: stats,
    });
  }
  return completed;
}

process.on('SIGTERM', () => {
  writeEvent('RUN_END', { status: 'CANCELED', reason: '任务已取消', counts: stats });
  process.exit(143);
});

if (require.main === module) {
  main().then((ok) => process.exit(ok ? 0 : 1)).catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
