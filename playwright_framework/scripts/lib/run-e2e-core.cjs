const { spawn, spawnSync } = require('node:child_process');
const { rmSync } = require('node:fs');
const {
  REPORT_LANGUAGE,
  patchGeneratedReportShell,
} = require('./allure-report-shell.cjs');

const {
  buildPlaywrightArgs,
  detectCi,
  parseArgs,
} = require('./run-e2e-options.cjs');

const REPORT_HIDDEN_LABELS = ['package', 'feature', 'titlePath', 'parentSuite', 'subSuite', 'host', 'thread'];
const REPORT_ARGS = [
  'allure',
  'awesome',
  './.allure-results',
  '--output',
  './reports/allure-report',
  '--report-name',
  'Allure 自动化测试报告',
  '--report-language',
  REPORT_LANGUAGE,
  ...REPORT_HIDDEN_LABELS.flatMap((labelName) => ['--hide-labels', labelName]),
];

function cleanupPaths(deps = {}) {
  const remove = deps.rmSync ?? rmSync;

  remove('./.allure-results', { recursive: true, force: true });
  remove('./reports/allure-report', { recursive: true, force: true });
  remove('./.playwright-artifacts', { recursive: true, force: true });
}

function cleanupRuntimeArtifacts(deps = {}) {
  const remove = deps.rmSync ?? rmSync;

  remove('./.allure-results', { recursive: true, force: true });
  remove('./.playwright-artifacts', { recursive: true, force: true });
}

function shouldKeepPlatformArtifacts(env = process.env) {
  return env.PLAYWRIGHT_PLATFORM_MODE === 'true';
}

function resolveExitCode(testStatus, reportStatus) {
  if ((testStatus ?? 1) !== 0) {
    return testStatus ?? 1;
  }

  return reportStatus ?? 1;
}

function createRunSync() {
  return function runSync(command, args) {
    return spawnSync(command, args, {
      cwd: process.cwd(),
      stdio: 'inherit',
      shell: process.platform === 'win32',
    });
  };
}

function createOpenReport(deps = {}) {
  const spawnImpl = deps.spawn ?? spawn;
  const customizeReport = deps.customizeReport ?? patchGeneratedReportShell;

  return function openReport() {
    customizeReport();

    const child = spawnImpl('npx', ['allure', 'open', './reports/allure-report'], {
      cwd: process.cwd(),
      stdio: 'ignore',
      shell: process.platform === 'win32',
      detached: true,
    });

    if (typeof child.unref === 'function') {
      child.unref();
    }
  };
}

function runE2E(deps = {}) {
  const argv = deps.argv ?? [];
  const env = deps.env ?? process.env;
  const options = parseArgs(argv);
  const runSync = deps.runSync;
  const cleanup = deps.cleanup ?? cleanupPaths;
  const cleanupArtifacts = deps.cleanupRuntimeArtifacts ?? cleanupRuntimeArtifacts;
  const openReport = deps.openReport ?? createOpenReport();
  const customizeReport = deps.customizeReport ?? patchGeneratedReportShell;

  if (!runSync) {
    throw new Error('runSync dependency is required');
  }

  cleanup();

  const testResult = runSync('npx', buildPlaywrightArgs(options));
  const reportResult = runSync('npx', REPORT_ARGS);
  const isCi = detectCi(env);

  if ((reportResult.status ?? 1) === 0) {
    customizeReport();
  }

  if ((testResult.status ?? 1) === 0
    && (reportResult.status ?? 1) === 0
    && !shouldKeepPlatformArtifacts(env)) {
    cleanupArtifacts();
  }

  if ((reportResult.status ?? 1) === 0 && !isCi) {
    openReport();
  }

  return {
    testStatus: testResult.status ?? 1,
    reportStatus: reportResult.status ?? 1,
    exitCode: resolveExitCode(testResult.status ?? 1, reportResult.status ?? 1),
  };
}

module.exports = {
  REPORT_ARGS,
  REPORT_HIDDEN_LABELS,
  cleanupPaths,
  cleanupRuntimeArtifacts,
  createOpenReport,
  createRunSync,
  patchGeneratedReportShell,
  resolveExitCode,
  runE2E,
  shouldKeepPlatformArtifacts,
};
