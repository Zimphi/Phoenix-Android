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


### Additional PersoSim test card

`eid-persosim03` is installed once without replacing existing drafts. Its signed
synthetic identity is immutable; custom eID drafts retain their own local test PKI.
PersoSim data and CA keys are taken from Profile03 at upstream commit
`2b04578928ae5a1b40189dabe87c4939cb69bdf1`.
`python3 scripts/prepare_test_fixture.py /path/to/ePassport` pins the original XML,
extracts no secret from a real document, and stages ignored private build inputs.
Only the unsigned obsolete CVCA/date administration nodes are removed: the app
pins current Governikus `DETESTeID00009` separately and uses current card time.
A native regression compares every signed file, DG and CA key with the original.
The external BSI Test-CSCA09 public certificate is pinned independently by SHA-256.
No protocol-core source is changed. The preset uses the existing validated
`PersonalizedProfile::load -> into_card -> application` API because the generic
host's additional PA report expects newly generated SecurityInfos. Validation,
licensing, TA authorization and secure messaging remain in the same eID core.
The opt-in `check_eid_test_profile` example checks import, rejection of modified
signed DGs and eID selection using an isolated copy of synthetic app state.
Do not infer successful official self-disclosure from this local check; that
requires a separate AusweisApp run against the physical NFC reader.


Hardware verification on 2026-10-20, Galaxy S23 Ultra + REINER SCT cyberJack RFID
basis, official AusweisApp test self-disclosure: PACE, terminal certificate chain,
TA and CA (AES-128, key 41) completed. The service then sent a batch containing
RI and DG reads. RI returned protected 6982 (effective requested CHAT has no RI
right), DG1–10/13/17 returned protected data, absent DG19 returned protected 6A82.
The remote service ended with `Paos_Error_AL_Internal_Error: Cannot decrypt
ResponseAPDU`. An independent Python cryptography check derived the CA session
from the public synthetic test key and recorded EAC2 nonce/public key: all 17
responses had valid AES-CMAC, all encrypted data had valid ISO7816 padding.
This is NOT a passed official self-disclosure. No RI authorization was broadened,
no failed PA/SM check bypassed, and no core source modified to conceal the result.
