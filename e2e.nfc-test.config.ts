import type {E2EConfig} from 'e2e';
import {mobile,type DeviceProvider} from '@e2e-dev/mobile';
const serial=process.env.PHOENIX_ANDROID_DEVICE;
if(!serial)throw new Error('PHOENIX_ANDROID_DEVICE must select the test phone serial');
const phone:DeviceProvider={name:'local-adb',async acquire(){return {id:serial,deviceId:serial,client:{}};},async release(){}};
export default {
 tests:['tests/e2e/nfc-test.e2e.ts'],
 targets:[{engine:mobile({platform:'android',device:phone,session:'nfc-assistant-'+serial}),app:{bundleId:'de.phoenix.simulator'}}],
 workers:1
} satisfies E2EConfig;
