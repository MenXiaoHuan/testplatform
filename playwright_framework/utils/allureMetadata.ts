import path from 'node:path';

export function isCiEnvironment(env: NodeJS.ProcessEnv): boolean {
  return (
    env.CI === 'true' ||
    env.GITHUB_ACTIONS === 'true' ||
    env.BUILDKITE === 'true' ||
    env.GITLAB_CI === 'true'
  );
}

export function cleanTitle(value: string): string {
  return value.replace(/@\w+/g, '').replace(/\s+/g, ' ').trim();
}

export function deriveAllureMetadata(input: {
  cwd: string;
  file: string;
  title: string;
}) {
  const relativeFile = path.relative(input.cwd, input.file);
  const segments = relativeFile.split(path.sep);
  const testsIndex = segments.indexOf('tests');
  const testSegments = testsIndex >= 0 ? segments.slice(testsIndex + 1) : segments;
  const rawFeature = testSegments.length > 1 ? testSegments[0] : 'root';
  const fileName = path.basename(relativeFile, path.extname(relativeFile));

  return {
    parentSuiteName: 'Playwright E2E',
    featureName: rawFeature,
    subSuiteName: fileName,
    storyName: cleanTitle(input.title) || fileName,
  };
}

export function buildAllureEnvironmentInfo(input: {
  env: NodeJS.ProcessEnv;
  browserProject: string;
  generatedAt: string;
  nodeVersion: string;
  osPlatform: string;
  osRelease: string;
}) {
  void input.generatedAt;

  return {
    执行模式: isCiEnvironment(input.env) ? 'CI' : '本地',
    浏览器项目: input.browserProject,
    'Node 版本': input.nodeVersion,
    操作系统: `${input.osPlatform} ${input.osRelease}`,
  };
}

export function buildTraceGuidance(input: {
  traceName: string;
  tracePath: string;
}) {
  return [
    `Trace 附件：${input.traceName}`,
    `Trace 路径：${input.tracePath}`,
    `本地打开命令：npx playwright show-trace ${input.tracePath}`,
    '建议优先在失败用例中查看 Trace、截图和错误上下文，快速定位页面状态和操作轨迹。',
  ].join('\n');
}
