import type { E2EConfig } from 'e2e';
import { mobile } from '@e2e-dev/mobile';
export default { tests: ['tests/e2e/*.e2e.ts'], targets: [{ engine: mobile({ platform: 'android' }), app: { bundleId: 'de.phoenix.simulator' } }], workers: 1 } satisfies E2EConfig;
