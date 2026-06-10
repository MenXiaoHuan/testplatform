import os from 'node:os';

import { defineConfig, devices } from '@playwright/test';
import { Status } from 'allure-js-commons';
import { buildAllureEnvironmentInfo, isCiEnvironment } from './utils/allureMetadata';

const platformMode = process.env.PLAYWRIGHT_PLATFORM_MODE === 'true';

const sharedUse = {
  viewport: { width: 1440, height: 900 },
  screenshot: 'on' as const,
  trace: 'on' as const,
  video: 'on' as const,
  ignoreHTTPSErrors: true,
};

const environmentInfo = buildAllureEnvironmentInfo({
  env: process.env,
  browserProject: 'chromium',
  generatedAt: new Date().toISOString(),
  nodeVersion: process.version,
  osPlatform: os.platform(),
  osRelease: os.release(),
});

const reporters = [
  ['list'],
  [
    'allure-playwright',
    {
      resultsDir: './.allure-results',
      // Keep detail enabled so failed cases still expose useful steps and attachments.
      detail: true,
      suiteTitle: true,
      categories: [
        {
          name: '页面加载或路由异常',
          messageRegex: '.*page.goto.*|.*waitForURL.*|.*net::ERR_.*',
          matchedStatuses: [Status.FAILED, Status.BROKEN],
        },
        {
          name: 'HTTPS 或证书异常',
          messageRegex: '.*ERR_CERT_AUTHORITY_INVALID.*|.*SSL.*|.*certificate.*',
          matchedStatuses: [Status.FAILED, Status.BROKEN],
        },
        {
          name: '接口响应异常',
          messageRegex: '.*waitForResponse.*|.*Response.*status.*',
          matchedStatuses: [Status.FAILED, Status.BROKEN],
        },
        {
          name: '元素定位异常',
          messageRegex: '.*locator\\.|.*element is not visible.*|.*waiting for locator.*',
          matchedStatuses: [Status.FAILED, Status.BROKEN],
        },
        {
          name: '断言异常',
          messageRegex: '.*expect\\(|.*toHave.*|.*toBe.*|.*AssertionError.*',
          matchedStatuses: [Status.FAILED],
        },
        {
          name: '数据准备异常',
          messageRegex: '.*storageState.*|.*ENOENT.*|.*Cannot find module.*',
          matchedStatuses: [Status.BROKEN],
        },
        {
          name: '脚本逻辑异常',
          messageRegex: '.*Cannot read properties.*|.*TypeError.*|.*ReferenceError.*',
          matchedStatuses: [Status.BROKEN],
        },
      ],
      environmentInfo,
    },
  ],
];

if (platformMode) {
  reporters.push([
    'json',
    {
      outputFile: './test-results/.playwright-results.json',
    },
  ]);
}

export default defineConfig({
  testDir: './tests',
  timeout: 30 * 1000,
  retries: 1,
  fullyParallel: true,
  forbidOnly: isCiEnvironment(process.env),
  reporter: reporters,
  outputDir: './.playwright-artifacts',
  use: sharedUse,
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...sharedUse,
      },
    },
  ],
});
