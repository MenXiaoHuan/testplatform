function detectCi(env = {}) {
  return (
    env.CI === 'true' ||
    env.GITHUB_ACTIONS === 'true' ||
    env.BUILDKITE === 'true' ||
    env.GITLAB_CI === 'true'
  );
}

function parseArgs(argv = []) {
  const options = {
    target: null,
    grep: null,
    project: null,
    headed: false,
    debug: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];

    if (value === '--target') {
      options.target = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--grep') {
      options.grep = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--project') {
      options.project = argv[index + 1] ?? null;
      index += 1;
      continue;
    }

    if (value === '--headed') {
      options.headed = true;
      continue;
    }

    if (value === '--debug') {
      options.debug = true;
    }
  }

  return options;
}

function buildPlaywrightArgs(options) {
  const args = ['playwright', 'test'];

  if (options.target) {
    args.push(options.target);
  }

  if (options.grep) {
    args.push('--grep', options.grep);
  }

  if (options.project) {
    args.push('--project', options.project);
  }

  if (options.headed) {
    args.push('--headed');
  }

  if (options.debug) {
    args.push('--debug');
  }

  return args;
}

module.exports = {
  detectCi,
  parseArgs,
  buildPlaywrightArgs,
};
