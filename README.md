# Phoenix Android

Kotlin/Jetpack Compose Android frontend for local ePassport and eID simulation. The original app
code is **Apache-2.0**; the Phoenix cores are separate and excluded (see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). PQC is reserved at the same
adapter boundary, not advertised as a working mode. Cloud is planned only.

## Contract

- Document type with profile counts → filtered profile list → editor → settings/data
  groups; import belongs in the editor. Every listed profile shows its document type.
- About displays actual app/build and bundled core version/commit identifiers.
- Settings contains Legal, Privacy, licenses and a disabled planned cloud entry.
- NFC APDUs execute locally through the unchanged core implementation; no
  substitute protocol engine and no cloud fallback.
- Android, local and Vercel must use the same source revisions. Target binaries differ.
  `components.lock.json` records the Android revisions. Existing local/Vercel drift
  still needs reconciliation; this development build does not establish parity.
- Cores remain private build inputs. No secrets, personal profiles or binaries in Git.

## Build

Requires Java 21, Android SDK platform 35 and the pinned Gradle 8.9 wrapper.
Set `ANDROID_HOME` and run `./gradlew :app:assembleDebug :app:lintDebug`.
The public app can build without private components; simulation then fails closed
with an explanatory error. It never pretends a draft is a working smartcard.

Authorized native builds: run `python3 scripts/prepare_native.py /path/to/ePassport`,
then `python3 scripts/build_native.py --ndk /path/to/android-ndk/27.2.12479018`.
Install the Rust `aarch64-linux-android` target first. Set `PHOENIX_RUSTLS_MAVEN`
to the `maven/` directory supplied by `rustls-platform-verifier-android-0.1.1`
when building the APK. Private checkouts and native binaries are excluded from Git.
This is a development source release, not a verified NFC interoperability release.

Native integration and platform limitations: [docs/INTEGRATION.md](docs/INTEGRATION.md).

## Tests

`python3 -m unittest discover -s tests -v` checks component pinning and public
licensing boundaries. TesterArmy e2e drives a separately installed app on an
ADB-connected Android phone: `npm ci && npm run test:e2e`.
Only synthetic test data are allowed. No model credentials are needed.
`npm run test:e2e:native` additionally activates through the existing trial relay
and checks that both profiles can start and stop local simulation through the UI.
It requires an authorized native build and network access for the existing
license/time checks; it does not prove NFC radio interoperability.
