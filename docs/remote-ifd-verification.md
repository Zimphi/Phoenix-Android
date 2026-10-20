# Remote IFD verification — 2026-10-20

Hardware: Samsung Galaxy S23 Ultra (SM-S918B), desktop AusweisApp 2.6.1 on macOS.
The phone and Mac used the same physical WLAN; ADB was used for test control only.
No USB port forwarding, local substitute eID server, or cloud card simulation was used.
Only the bundled synthetic PersoSim 03 profile was disclosed to the official Governikus test self-disclosure service.

## Live checks

| Check | Result |
| --- | --- |
| Automatic WLAN discovery of Phoenix Android | Passed; visible in AusweisApp without manually entering an address |
| TLS pairing without PSK/client identity | Rejected with handshake failure |
| Incorrect pairing code in AusweisApp | Rejected; AusweisApp displayed pairing failure |
| Correct pairing code | AusweisApp displayed pairing success |
| Full official test self-disclosure | Passed; AusweisApp displayed “Lesevorgang erfolgreich” and the synthetic card data |
| App backgrounded after first disclosure | TCP listener closed; a fresh LAN connection was refused |
| Resume, start reader again, reuse stored pairing | Passed without pairing again |
| Second complete official test self-disclosure | Passed with the same synthetic data |
| Unpaired TLS client with correctly pinned server certificate | Rejected with TLSV13_ALERT_CERTIFICATE_REQUIRED |
| Explicit Simulation beenden | TCP listener closed; a fresh LAN connection was refused |
| Repeated NFC/IFD selection | Profile and start-button bounding boxes remain identical through three round trips |
| Final installed UI after process restart | Stored pairing reused; updated laptop icon and “IFD-Schnittstelle aktiv” verified |
| Cancel removal confirmation | Pairing remains stored after cancelling |

Desktop screens were operated and inspected through CUA. TesterArmy controlled the phone;
its desktop-coordination markers were advanced only after the corresponding desktop result
or network assertion had been observed. The trace therefore records the phone workflow;
the actual AusweisApp success messages were separately verified in its accessibility tree.

Evidence in the local workspace: `.e2e/ifd-ausweisapp-fixed/`,
`.e2e/ifd-layout-fixed/`, `.e2e/ifd-final-regression/` and the corresponding
`/private/tmp/phoenix-ifd-*.log` files. Ephemeral pairing codes and raw desktop logs are not committed.

## Issues found and fixed

The JNI envelope reserves `error` for a failed operation. Reader status now uses
`last_error`; an empty status field can no longer produce an empty error dialog.

The shared identity factory produces an empty subject/issuer. Two such simulator
anchors in one OpenSSL trust store caused the new phone certificate to be rejected,
although PSK pairing had succeeded. This was reproduced with the phone's actual
public certificate and the existing simulator anchor. The Android adapter now
configures a device-unique subject through the existing identity API, preserving
the encrypted private key. The corrected phone certificate verifies alongside the
old anchor and both complete AusweisApp reads succeeded afterward. No core source,
version pin, certificate-verification policy, or installed AusweisApp was modified.

The original layout changed image height and inserted extra content when selecting
IFD. Both modes now have the same image and caption dimensions and the two icon
buttons stay below the illustration. The position regression first reproduced a
128-pixel jump and then passed after correction.

## Other validation and limits

The native adapter has 11 passing unit tests, including identity coexistence and
restart stability, corrupt trust rejection, removal of pairings without changing
the identity, local-network peer restrictions and the JNI envelope regression.
All six Python repository/build contracts and the three existing Android UI tests pass.
Android debug APK, test APK and lint build successfully. Existing isolated Android
instrumentation checks for persistence, import validation, hardware-backed key proof,
native inventory and stale/background start cancellation also pass.

The final NFC regression used the physical REINER SCT cyberJack RFID basis for
Erika's ePassport, Max's editable eID, PersoSim 01 and PersoSim 03. All four passed
PACE and protected reads; all four rejected APDUs after Stop. The same run also
passed the stable interface-layout test (five tests total).
Evidence: `.e2e/ifd-nfc-regression/`.
The final pairing/settings UI check also passed in `.e2e/ifd-final-pairing-ui/`.

## Phoenix eID Client follow-up

At the user's request, the final installed Android app was tested again with the
editable Phoenix profile **Max Mustermann**, rather than a PersoSim profile.
The existing local Phoenix eID Client disclosure bridge read the phone through
the physical REINER SCT cyberJack RFID basis. The full workflow passed with
security state `PACE-TA-PA-CA`; PACE, passive authentication and chip authentication
were verified and the authorized DG4, DG5 and DG8 were returned. TesterArmy
selected the profile, started the NFC simulation and stopped it afterward.
An independent post-stop PC/SC probe confirmed that the phone rejected APDUs.
Evidence: `.e2e/phoenix-client-reiner-final/` and
`/private/tmp/phoenix-client-reiner-final.log` (2026-10-20, 20:40).
This check exercises the actual local client runtime, not its Flutter GUI.

## Limits

Five-minute pairing expiration and the five-attempt budget use the unchanged core
PairingSession. This run did not wait out five minutes or exhaust all five attempts.
No other phone models, iOS app, or formal TR conformance certification were tested.
