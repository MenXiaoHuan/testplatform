#!/usr/bin/env node

const { createOpenReport, createRunSync, runE2E } = require('./lib/run-e2e-core.cjs');

const result = runE2E({
  argv: process.argv.slice(2),
  env: process.env,
  runSync: createRunSync(),
  openReport: createOpenReport(),
});

process.exit(result.exitCode);
