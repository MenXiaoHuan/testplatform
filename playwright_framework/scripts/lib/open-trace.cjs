const { spawn } = require('node:child_process');
const { readdirSync, statSync } = require('node:fs');
const path = require('node:path');

const TRACE_ATTACHMENTS_DIR = path.resolve(process.cwd(), './reports/allure-report/data/attachments');

function listTraceArchives(traceDir = TRACE_ATTACHMENTS_DIR) {
  return readdirSync(traceDir)
    .filter((fileName) => fileName.endsWith('.zip'))
    .map((fileName) => {
      const filePath = path.join(traceDir, fileName);
      const stats = statSync(filePath);

      return {
        path: filePath,
        mtimeMs: stats.mtimeMs,
      };
    })
    .sort((left, right) => right.mtimeMs - left.mtimeMs);
}

function createOpenTrace(deps = {}) {
  const getTraceArchives = deps.listTraceArchives ?? listTraceArchives;
  const spawnImpl = deps.spawn ?? spawn;

  return function openTrace() {
    const traceArchives = [...getTraceArchives()].sort((left, right) => right.mtimeMs - left.mtimeMs);

    if (traceArchives.length === 0) {
      throw new Error('No trace zip found under ./reports/allure-report/data/attachments. Generate a report first.');
    }

    const latestTraceArchive = traceArchives[0];

    const child = spawnImpl('npx', ['playwright', 'show-trace', latestTraceArchive.path], {
      cwd: process.cwd(),
      stdio: 'inherit',
      shell: process.platform === 'win32',
    });

    return child;
  };
}

module.exports = {
  TRACE_ATTACHMENTS_DIR,
  createOpenTrace,
  listTraceArchives,
};
