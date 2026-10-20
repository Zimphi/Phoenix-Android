# Shared core integration

The app/transport adapter owns Android lifecycle, UI and JNI only. PACE, BAC,
Secure Messaging, authorization and all APDU behavior remain in the existing Rust
cores and existing host. The open repository contains no core source or binaries.

A private native build uses the pinned simulator host and its recursive core
checkout. Source revisions are recorded, not just marketing versions. Future PQC
uses the same adapter, without a second Android-specific protocol implementation.
Cloud settings are informational and send no requests.

Platform work and remaining limits:
- Android key identity, CLOCK_BOOTTIME and Android system TLS trust were added
  centrally to the same licensing SDK. Signature and authenticated time gates remain.
- Authenticated NTS time is an existing network requirement of core licensing;
  local APDU processing does not mean guaranteed cold-start airplane-mode licensing.
- Local and Vercel currently pin different commits despite matching version labels.
  Parity must be established before claiming equivalence on all targets.
- Physical full authentication passed on 2026-10-20 with a Samsung SM-S918B
  and REINER SCT cyberJack RFID basis. Both local clients first require an
  external SELECT AID to route Android HCE; their MF-first startup is not yet
  sufficient on its own. See the test evidence below.


## Agreed activation direction

Use the existing license trial relay in the background, with no licensing GUI.
Existing trial duration is exactly 30 days; automatic bootstrap is first-start
only. Recurrent renewal is not authorized or silently introduced.
A non-exportable Android Keystore signing key (StrongBox preferred, TEE fallback)
signs a random native challenge for device identity. Native code verifies possession;
the relay does not verify hardware attestation, so remote hardware provenance is
not claimed. Profile data stay local. The trial relay transport uses HTTPS and
passes its signed delivery to the unchanged shared verifier; no local grant is minted.

## Physical authentication evidence (2026-10-20)

| Android profile | Existing local client | Verified physical workflow |
| --- | --- | --- |
| Max Mustermann (eID) | Phoenix eID Client 0.2.0-dev, disclosure bridge | PACE → TA → PA → CA; authorized DG4, DG5, DG8 read |
| Erika EAC-Test (additional ePassport) | Phoenix IS Terminal, local CLI | PACE → CA → PA → TA; complete document authentic; DG1, DG2, protected DG3 and DG14 read |

The original Erika passport profile only configures PACE and PA. A separate
profile was generated through the unchanged, pinned native personalization API
for the full EAC test. Original drafts and native profiles were retained.
Matching terminal credentials derive from synthetic test CVCAs. CSCA/CVC checks,
SOD signatures, DG hashes and card cryptographic authentication stayed enabled.
No card protocol or licensing check was replaced. The test harness holds a
physical PC/SC connection and selects the relevant AID before starting the local
client; all subsequent authentication runs in that client.

TesterArmy drives selection/start/stop on the phone. To repeat with the private
fixture directory, use `PHOENIX_AUTH_MATERIAL=/private/test-directory npm run
test:e2e:native -- --grep 'full authentication'`. The example
`native/examples/full_auth_material.rs` prepares a fresh, private copy of Android
synthetic PKI/profile data; it needs `phone.tar`, `profile-passphrase`,
`runtime/pki`, the original `profiles`, a random ASCII `terminal-password`, and
`synthetic-dg3.bin` from the existing synthetic DG3 builder. Then run
`tests/device/configure_local_terminals.py DIRECTORY` with the sibling Python
runtime to verify CVC chains and configure both clients. Only the additional
native passport profile is transferred; its draft is imported through the
instrumentation's existing validated ProfileStore import. Keep all generated
keys, copies and client traces outside Git in a private directory.

This is physical functional evidence, not a certification or exhaustive
interoperability result. Online PAOS and PQC were not part of this check.


### PersoSim test cards

The catalog adds Profile01–11 and ProfileUB01–05 once, preserving existing drafts
and the existing `eid-persosim03` ID. GlobalTester is excluded. Provenance and
SHA-256 pins are in `persosim-fixtures.lock.json`, upstream revision
`2b04578928ae5a1b40189dabe87c4939cb69bdf1`. The catalog generator decodes each
profile through the shared core and verifies its CardSecurity against the
independently pinned BSI Test-CSCA09 certificate. Preparation removes only the
unsigned obsolete CVCA/date administration; signed files, DGs and test CA keys
remain byte-identical. Current Governikus `DETESTeID00009` is pinned separately.

Signed originals cannot be edited. The editor offers an editable copy using the
existing local test PKI, so it is no longer advertised as a BSI-signed test card.
DG1–22 are listed directly, excluding reserved DG16. Complex values use the
existing core JSON representation. CAN/PIN/PUK and initial remaining counters are
stored in the draft and included in the native cache identity. PIN counts are
0–3 and remaining PUK uses 0–10, as enforced by the shared core. These are initial
card states, not configurable protocol maxima: successful PIN authentication
restores the standard three tries. Reconnecting the same native profile retains
its card-owned counters. The transport-PIN flag requires exactly five PIN digits; ordinary PINs require
six. `eid_active=false` separately sets the existing core PIN activation state.
Both fields are part of native cache identity. Neither setting changes the signed
PersoSim identity. Transport PINs allow PIN management via PACE, but cannot
authorize TA or protected eID disclosure. Changing to a six-digit personal PIN
does not elevate the old channel; fresh PACE is required.

The app uses the shared `PersonalizedProfile::load -> into_card -> application`
API for signed profiles. No app-specific APDU or license implementation exists.
The shared eID revision includes the official-reader interoperability fixes:
SELECT P2=0C suppresses FCI; protected outer SW matches DO99; RI key1 honors its
signed `authorizedOnly` policy while still requiring TA/CA and the signed sector
key binding. Key2 retains privileged authorization.

On 2026-10-20 the user confirmed full official AusweisApp test self-disclosure
working with Profile03 on Galaxy S23 Ultra and REINER SCT cyberJack RFID basis
after those fixes. This confirmation does not establish official service
acceptance for every fixture or for edited copies. The `check_eid_test_profile`
example separately checks all 16 imports, signature protection and SELECT using
an isolated copy of synthetic Android state. No proprietary binaries or keys are
published with this Apache-2.0 app source.

Validation of the 16-profile/editor update on 2026-10-20:

- All 16: original/adapted signed-file, DG and CA-key identity; trusted
  CardSecurity; complete import and SELECT; configured PIN=2 / PUK=7 counters
  verified in the encrypted card state. Modified signed DGs are rejected.
- Physical REINER SCT: original Erika passport, custom Max eID, PersoSim01 and
  PersoSim03 each passed PACE, protected reading and stopped-card rejection.
- TesterArmy: document artwork, simulation text, editor tabs, credential fields,
  all supported eID rows through DG22 and absence of the add-DG action.
- Android instrumentation: 18 isolated defaults, 16 signed fixtures, no duplicate
  fixtures after reopen, counter range rejection, persisted drafts, Keystore proof,
  both-core inventory and cancellation of stale/background simulation starts.
- Native adapter: 7 unit tests; public repository/build contracts: 6 tests.
  Android debug/test APK builds and Lint passed. The x86 Java 21 C2 compiler
  crashed under Rosetta once; rerunning with `-XX:TieredStopAtLevel=1` passed.

These checks do not claim full official online self-disclosure for all 16 profiles.

### Shared transport-PIN update

Android, the local Simulator component and the Vercel build lock now pin the same
eID source revision, `107212766ba02ebc788e4f17f5247c8aed6c02c7`. The central
core preserves its transport-PIN channel restriction in snapshots and cloud
checkpoints. Inactive eID rejects PIN access (MSE status 6283), while CAN remains
available for authorized administration. No Android-specific card protocol or
cloud fallback was added. A compile check with `vercel-cloud` is not a deployment
or evidence of runtime behavior on Vercel.

Physical verification of both PIN modes on 2026-10-20, Galaxy S23 Ultra and
REINER SCT cyberJack RFID basis, driven by TesterArmy:

- Five-digit PIN PACE succeeded. An invalid five-digit replacement was rejected,
  then protected CHANGE REFERENCE DATA replaced it with six digits.
- After stopping/restarting the same simulated card, fresh PACE with the new
  personal PIN succeeded, demonstrating persisted credentials.
- Separately, deactivated eID rejected PIN-based MSE with 6283; CAN PACE and
  protected CardAccess reading remained available.
- Both settings persisted after reopening the editor and remained independent.
  Test values were restored afterward.

The central Rust suite and its cloud-checkpoint policy test passed. Local
Simulator format checks, full tests and Clippy passed with the same eID commit;
existing licensed hardware/profile tests retain their explicit ignore status.
The Vercel worker feature combination (`vercel-owner,experimental-eac`) compiled;
no online Vercel runtime test is claimed.
