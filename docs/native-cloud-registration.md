# Native Phoenix Cloud registration

The Cloud settings implement the approved welcome, email, code, passkey and
connected screens with the Phoenix Cloud logo centered on every screen. The
confirmation email is configured separately; its mail-client presentation and
the Android credential-provider sheet are outside app control. Existing local
profiles and the QR device enrollment remain available.

Pending registration/enrollment survives process restart in AES-256-GCM encrypted
preferences protected by Android Keystore. Leaving registration keeps its pending
secret; it does not abandon the upstream account. Correcting the email preserves
the old attempt in encrypted storage and reuses it if the same address is entered
again. Email is requested once per registration; no resend button is presented. Authentication tokens and OTPs
are not persisted. Native App Links require the release certificate association
and the server prerequisites documented in Phoenix-Vercel/docs/native-passkey-signup.md.
The mobile server entry point remains disabled until explicitly configured.

Validation commands, run from the parent ePassport repository:

- `npm ci --ignore-scripts`
- `npm run test:e2e -- --config Phoenix-Android/e2e.cloud-auth.config.ts`
- `npm run test:e2e -- --config Phoenix-Vercel/e2e.passkeys.config.ts`
- `PHOENIX_ANDROID_DEVICE="SM G991B" npm run test:e2e -- --config Phoenix-Android/e2e.cloud-native.config.ts`
- Android `./gradlew :app:assembleDebug :app:lintDebug`
- Android `./gradlew :app:assembleDebugAndroidTest -PauthTest`
- `adb -s <test-device> shell am instrument -w de.phoenix.simulator.test/de.phoenix.simulator.MobileAuthInstrumentation`

Use a synthetic test device; the native UI test never submits registration, sends
mail, logs out, or deletes an existing device identity.
