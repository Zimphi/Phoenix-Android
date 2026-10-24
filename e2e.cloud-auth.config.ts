import type {E2EConfig} from 'e2e';
import {web} from '@e2e-dev/web';
export default {
  tests:['tests/mockups/*.e2e.ts'],
  targets:[{engine:web(),app:{url:'http://127.0.0.1:18789',command:{executable:'python3',args:['-m','http.server','18789','--bind','127.0.0.1','--directory','..']}}}],
  workers:1,
} satisfies E2EConfig;
