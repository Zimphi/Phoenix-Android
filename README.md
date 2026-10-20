# Phoenix Android

Kotlin/Jetpack Compose Android frontend for local ePassport and eID simulation. The original app
code is **Apache-2.0**; the Phoenix cores are separate and excluded (see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). PQC is reserved at the same
adapter boundary, not advertised as a working mode. Cloud is planned only.

## Contract

- Document type with profile counts → filtered profile list → editor → data
  groups/settings; import belongs in the editor. Every listed profile shows its document type.
- About displays actual app/build and bundled core version/commit identifiers.
- Settings contains Legal, Privacy, licenses, IFD pairing and a disabled planned cloud entry.
- eID simulation offers NFC and IFD icon selectors below the phone illustration.
  Switching transport preserves the layout; pairing belongs in Settings → IFD-Schnittstelle.
- NFC APDUs execute locally through the unchanged core implementation; no
  substitute protocol engine and no cloud fallback.
- Android, local and Vercel must use the same source revisions. Target binaries differ.
  `components.lock.json` records the Android revisions. Existing local/Vercel drift
  still needs reconciliation; this development build does not establish parity.
- Cores remain private build inputs. No secrets, personal profiles or binaries in Git.

The app includes 16 signed PersoSim fixtures (01–11 and UB01–UB05), without
GlobalTester. Signed data stays immutable; editable copies use the local test PKI.
The editor lists all supported eID DGs directly and exposes CAN, PIN, PUK and
initial remaining counters. Transport-PIN (five digits) and deactivated eID are independent settings,
implemented by the shared eID core. All three eID build pins are identical;
remaining drift in other component dependencies is recorded separately.

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

For the physical REINER SCT check, keep the phone unlocked on the reader and run
`PHOENIX_RF_TEST=1 npm run test:e2e:native`. This opt-in uses the licensed sibling
`Phoenix-IS-Terminal` through `Phoenix-ePassport-Core/.venv/bin/python`; no core
sources are copied. It checks application selection, PACE, a protected synthetic
DG1 (passport) or CardAccess read (eID), and an actual `6985` response after stop.
Transport failures remain failures. It does not perform full CA/PA/TA conformance.

The full physical authentication test uses the existing local eID Client and
IS Terminal: `PHOENIX_AUTH_MATERIAL=/private/test-directory npm run test:e2e:native
-- --grep 'full authentication'`. Preparation, verified profiles and the required
Android AID preselection are documented in [INTEGRATION.md](docs/INTEGRATION.md).

## Smartphone as IFD reader

Connect the phone and desktop to the same local network. In Phoenix, open
Settings → IFD-Schnittstelle and start pairing. In desktop AusweisApp, select
Phoenix Android under Settings → Smartphone as card reader and enter the code.
The code expires after five minutes or five failed attempts. Pairing exposes no
card; select an eID profile, choose IFD-Schnittstelle in Simulation and start it
to make that synthetic card available. Subsequent connections use mutual TLS.
Stopping simulation, leaving the simulation page, or backgrounding Phoenix closes
the network reader. Pairings persist across restarts and can be removed in settings.

Implementation and verification: [docs/remote-ifd.md](docs/remote-ifd.md).
The shared protocol cores and their source revisions are unchanged by this adapter.
