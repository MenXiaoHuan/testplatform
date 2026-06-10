import type {
  BrowserContext,
  BrowserContextOptions,
  Page,
} from '@playwright/test';

import { test } from './allure';

type ContextCookie = Parameters<BrowserContext['addCookies']>[0][number];

export type SetupGroupContextOptions = {
  baseURL?: string;
  headers?: Record<string, string>;
  cookies?: ContextCookie[];
  contextOptions?: Omit<BrowserContextOptions, 'baseURL' | 'extraHTTPHeaders'>;
};

type GroupContextHandle = {
  readonly context: BrowserContext;
  readonly page: Page;
};

export function setupGroupContext(
  options: SetupGroupContextOptions,
): GroupContextHandle {
  let context!: BrowserContext;
  let page!: Page;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({
      baseURL: options.baseURL,
      extraHTTPHeaders: options.headers,
      ...options.contextOptions,
    });

    if (options.cookies?.length) {
      await context.addCookies(options.cookies);
    }

    page = await context.newPage();
  });

  test.afterAll(async () => {
    await page?.close();
    await context?.close();
  });

  return {
    get context() {
      return context;
    },
    get page() {
      return page;
    },
  };
}
