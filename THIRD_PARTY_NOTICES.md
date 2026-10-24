# Licensing boundaries

The original Android application and integration glue are Apache-2.0.
Phoenix eID, ePassport, PQC and their license services retain their respective
licenses. No proprietary source, keys, licenses or native binaries are committed.
Private builds require authorized access to those components. An open app license
does not grant a license to redistribute or use the cores.

`app/src/main/res/drawable-nodpi/phoenix_*.png` is the original Phoenix logo
from Phoenix Simulator `GUI/assets/phoenix-mark.png`. It retains GPL-3.0-only
and the existing separate commercial license option; see `licenses/GPL-3.0.txt`.
The logo is not relicensed under Apache-2.0. Trademark rights are not granted.
Before distributing a combined binary, review all component terms, including
GPL/LGPL dependencies and the simulator host's licensing. No public APK is supplied.

Lucide icons are under ISC / MIT terms, with the full notice in `licenses/Lucide.txt`.
Source revision and vector conversion are recorded in `licenses/Lucide-source.txt`.
Manrope font is under SIL Open Font License 1.1; see `licenses/Manrope.txt`.

PKI QR scanning uses JourneyApps ZXing Android Embedded 4.3.0 and ZXing core,
under Apache-2.0: https://github.com/journeyapps/zxing-android-embedded and
https://github.com/zxing/zxing. Camera frames are decoded locally; this scanner
does not use a remote QR-generation or QR-decoding service.

PersoSim Profile01–11 and ProfileUB01–05: synthetic test data from
https://github.com/PersoSim/de.persosim.simulator at
2b04578928ae5a1b40189dabe87c4939cb69bdf1, GPL-3.0.
See licenses/GPL-3.0.txt. GlobalTester is excluded.

## Offline country catalogue

`icao_countries_DE.json` is copied unchanged from
https://github.com/Zimphi/CountryCode-ISO-3166-1-alpha-3- at the revision in
`countries.lock.json`, by the repository owner's explicit request. Its source
data and German translations come from Debian iso-codes via pycountry
(LGPL-2.1-or-later; see `licenses/LGPL-2.1.txt`). The original JSON, source
repository and integrity hash are retained. Utopia is a separate synthetic UI
option and is not presented as an ISO country.

## NFC diagnostic code pairing

The Android app uses Bouncy Castle Java 1.83 (`bcprov-jdk18on`) for the
J-PAKE password-authenticated key exchange. Bouncy Castle is distributed under
its MIT-style license: https://www.bouncycastle.org/licence.html .
The exact dependency's license and copyright notice is retained in
`licenses/BouncyCastle.txt`.
The six-digit code is never transmitted as an authentication password.
