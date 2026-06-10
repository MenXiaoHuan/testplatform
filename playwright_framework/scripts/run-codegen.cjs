#!/usr/bin/env node

const { mkdirSync } = require('node:fs');
const { dirname } = require('node:path');
const playwright = require('@playwright/test');
const { parseCodegenArgs } = require('./lib/run-codegen-options.cjs');
const { runCodegen } = require('./lib/run-codegen-core.cjs');

async function main() {
  const options = parseCodegenArgs(process.argv.slice(2));
  mkdirSync(dirname(options.output), { recursive: true });

  await runCodegen({
    options,
    playwright,
    devices: playwright.devices,
  });
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
