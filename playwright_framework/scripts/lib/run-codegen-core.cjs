const { existsSync, readFileSync } = require('node:fs');
const { join } = require('node:path');
const { projectDefaults } = require('./run-codegen-options.cjs');

function defaultLoadRecordingConfig(kind, name) {
  if (!name) {
    return null;
  }

  const filePath = join(process.cwd(), 'config', 'recording', `${kind}s`, `${name}.json`);

  if (!existsSync(filePath)) {
    return null;
  }

  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function resolveBrowserName(options = {}) {
  const defaults = projectDefaults(options.project);
  return options.browser ?? defaults.browser ?? 'chromium';
}

function buildRecordingContextOptions(options = {}, deps = {}) {
  const devices = deps.devices ?? {};
  const contextOptions = {
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
  };

  if (!options.device) {
    return contextOptions;
  }

  const deviceDescriptor = devices[options.device];

  if (!deviceDescriptor) {
    throw new Error(`Unknown device preset: ${options.device}`);
  }

  return {
    ...contextOptions,
    ...deviceDescriptor,
  };
}

function resolveBrowserType(playwright, browserName) {
  const browserType = playwright?.[browserName];

  if (!browserType || typeof browserType.launch !== 'function') {
    throw new Error(`Unsupported browser type: ${browserName}`);
  }

  return browserType;
}

function resolveRecordingRuntime(options = {}, deps = {}) {
  const loadRecordingConfig = deps.loadRecordingConfig ?? defaultLoadRecordingConfig;
  const contextOptions = buildRecordingContextOptions(options, deps);
  const envConfig = options.env ? loadRecordingConfig('env', options.env) ?? {} : {};
  const roleConfig = options.role ? loadRecordingConfig('role', options.role) ?? {} : {};
  const headers = {
    ...(envConfig.headers ?? {}),
    ...(roleConfig.headers ?? {}),
    ...(options.headers ?? {}),
  };

  if (envConfig.baseURL) {
    contextOptions.baseURL = envConfig.baseURL;
  }

  if (Object.keys(headers).length > 0) {
    contextOptions.extraHTTPHeaders = headers;
  }

  if (roleConfig.storageState) {
    contextOptions.storageState = roleConfig.storageState;
  }

  return {
    url: options.url,
    contextOptions,
  };
}

async function runCodegen(deps = {}) {
  const options = deps.options ?? {};
  const playwright = deps.playwright;
  const devices = deps.devices ?? playwright?.devices ?? {};
  const loadRecordingConfig = deps.loadRecordingConfig ?? defaultLoadRecordingConfig;

  if (!playwright) {
    throw new Error('playwright dependency is required');
  }

  const browserName = resolveBrowserName(options);
  const browserType = resolveBrowserType(playwright, browserName);
  const runtime = resolveRecordingRuntime(options, {
    devices,
    loadRecordingConfig,
  });
  const browser = await browserType.launch({ headless: false });

  try {
    const context = await browser.newContext(runtime.contextOptions);
    const page = await context.newPage();

    if (runtime.url) {
      await page.goto(runtime.url);
    }

    await page.pause();
  } finally {
    await browser.close();
  }
}

module.exports = {
  buildRecordingContextOptions,
  defaultLoadRecordingConfig,
  resolveBrowserName,
  resolveBrowserType,
  resolveRecordingRuntime,
  runCodegen,
};
