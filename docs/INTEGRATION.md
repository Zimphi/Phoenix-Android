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
- A physical REINER SCT cyberJack RFID basis / Samsung SM-S918B check on
  2026-10-20 passed passport application selection, CardAccess, CAN PACE and a
  secure-messaging DG1 read matching the synthetic Erika profile. A stopped
  session returned `6985`. Subsequent attempts lost RF contact (`80100016`,
  then no card `8010000C`); eID RF and full CA/PA/TA remain unverified.
  ADB/UI tests alone do not prove RF interoperability.

## Agreed activation direction

Use the existing license trial relay in the background, with no licensing GUI.
Existing trial duration is exactly 30 days; automatic bootstrap is first-start
only. Recurrent renewal is not authorized or silently introduced.
A non-exportable Android Keystore signing key (StrongBox preferred, TEE fallback)
signs a random native challenge for device identity. Native code verifies possession;
the relay does not verify hardware attestation, so remote hardware provenance is
not claimed. Profile data stay local. The trial relay transport uses HTTPS and
passes its signed delivery to the unchanged shared verifier; no local grant is minted.
