# Geräteanmeldung auf dem Smartphone

Am 21.10.2026 mit dem angeschlossenen Samsung SM-S918B und dem echten Preview-Geräte-QR-Code geprüft.

`tests/manual/account_live.e2e.ts` hat die sichtbare Benutzerkennung, Benutzerübersicht mit Gerätezuordnung zur Preview-Umgebung, Abmelden und den abgemeldeten Zustand nach App-Neustart erfolgreich geprüft (1 Test, 15,2 Sekunden). Die Anmeldung erfolgte durch den Benutzer am Telefon; es wurden keine Zugangsdaten in den Test übernommen.

Der Konto-Einstieg verwendet den stabilen Compose-Testbezeichner `open-account`; sein zugänglicher Text gehört zu einer verschachtelten Gruppe und ist kein eigenständiger Button-Name.

Der Test meldet das Gerät absichtlich ab. Für eine erneute Anmeldung wird ein frischer Geräte-QR-Code benötigt. Dieser Nachweis betrifft die Geräteanmeldung, nicht die Ausstellung neuer PKI-Zertifikate.
