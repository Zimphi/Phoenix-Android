import type {E2EConfig} from 'e2e';
import base from './e2e.nfc-test.config';

// Attach to the real two-phone harness after it has completed all six cases.
export default {...base,tests:['tests/e2e/nfc-test-results.e2e.ts']} satisfies E2EConfig;
