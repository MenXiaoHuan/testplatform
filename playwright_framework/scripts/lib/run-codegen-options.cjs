function defaultCodegenOutput(name) {
  return `tests/codegen/${name}.spec.ts`;
}

function projectDefaults(project) {
  if (project === 'chromium') {
    return {
      browser: 'chromium',
    };
  }

  return {
    browser: null,
  };
}

function parseHeaderEntry(value) {
  const separatorIndex = value.indexOf('=');

  if (separatorIndex <= 0) {
    return null;
  }

  const key = value.slice(0, separatorIndex).trim();
  const headerValue = value.slice(separatorIndex + 1).trim();

  if (!key) {
    return null;
  }

  return [key, headerValue];
}

function parseCodegenArgs(argv = []) {
  const options = {
    name: 'codegen',
    env: null,
    role: null,
    headers: {},
    project: null,
    device: null,
    browser: null,
    url: null,
    output: null,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];

    if (value === '--name') {
      options.name = argv[index + 1] ?? 'codegen';
      index += 1;
      continue;
    }

    if (value === '--env') {
      options.env = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--role') {
      options.role = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--header') {
      const headerEntry = parseHeaderEntry(argv[index + 1] ?? '');

      if (headerEntry) {
        const [key, headerValue] = headerEntry;
        options.headers[key] = headerValue;
      }

      index += 1;
      continue;
    }

    if (value === '--project') {
      options.project = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--device') {
      options.device = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--browser') {
      options.browser = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--url') {
      options.url = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--output') {
      options.output = argv[index + 1] ?? null;
      index += 1;
    }
  }

  options.output = options.output ?? defaultCodegenOutput(options.name);
  return options;
}

function buildCodegenArgs(options) {
  const defaults = projectDefaults(options.project);
  const browser = options.browser ?? defaults.browser;
  const args = [
    'playwright',
    'codegen',
    '--output',
    options.output,
    '--viewport-size',
    '1440,900',
    '--ignore-https-errors',
  ];

  if (browser) {
    args.push('-b', browser);
  }

  if (options.device) {
    args.push('--device', options.device);
  }

  if (options.url) {
    args.push(options.url);
  }

  return args;
}

module.exports = {
  parseCodegenArgs,
  buildCodegenArgs,
  defaultCodegenOutput,
  parseHeaderEntry,
  projectDefaults,
};
