import type {E2EConfig} from 'e2e';
import base from './e2e.nfc-test.config';

export default {...base,tests:['tests/e2e/nfc-test-camera.e2e.ts']} satisfies E2EConfig;
