import type { SetupGroupContextOptions } from '../../utils/groupContext';

export const demoGroupConfig = {
  baseURL: 'https://example.com',
  headers: {
    'x-demo-env': 'interview',
    'x-demo-role': 'qa',
  },
  cookies: [
    {
      name: 'sessionid',
      value: 'demo-session-token',
      domain: '.example.com',
    },
  ]
} satisfies SetupGroupContextOptions;
