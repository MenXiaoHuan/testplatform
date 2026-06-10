import { expect, test as base } from '@playwright/test';
import {
  ContentType,
  Severity,
  attachment,
  feature,
  owner,
  parentSuite,
  severity,
  story,
  subSuite,
} from 'allure-js-commons';
import { buildTraceGuidance, deriveAllureMetadata } from './allureMetadata';

export const test = base;

test.beforeEach(async ({}, testInfo) => {
  const metadata = deriveAllureMetadata({
    cwd: process.cwd(),
    file: testInfo.file,
    title: testInfo.title,
  });

  await parentSuite(metadata.parentSuiteName);
  await feature(metadata.featureName);
  await story(metadata.storyName);
  await subSuite(metadata.subSuiteName);
  await owner('QA');
  await severity(Severity.MINOR);
  await attachment(
    'Trace 使用说明',
    buildTraceGuidance({
      traceName: 'trace.zip',
      tracePath: `${testInfo.outputDir}/trace.zip`,
    }),
    ContentType.TEXT
  );
});

export { expect };
