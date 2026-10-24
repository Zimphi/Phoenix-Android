# NFC-Testassistent

Stand: 24. Oktober 2026. Die Kotlin-Oberfläche befindet sich unter Einstellungen →
NFC-Test und verwendet das helle App-Design. ADB ist für den Nutzer nicht nötig;
es wird ausschließlich für die automatisierten Entwicklertests verwendet.

## Ablauf und Geltungsbereich

Ein Master erstellt die temporäre Sitzung. Das zweite Telefon scannt den QR-Code
oder gibt den sechsstelligen Code ein. Beide Geräte benötigen dasselbe lokale
WLAN; Client-Isolation darf die direkte Verbindung nicht verhindern. QR koppelt
mit Zertifikats-Pin und zufälligem Token, die Code-Variante über NSD und
Bouncy-Castle-JPAKE mit Bindung an das tatsächlich verwendete TLS-Zertifikat.
Der Code wird nicht unverschlüsselt übertragen. Kopplungsangebote verfallen nach
fünf Minuten, Fehlversuche sind begrenzt. Die Verbindung bleibt bis zum Verlassen
der Ergebnisseite oder expliziten Trennen geöffnet; es gibt keine dauerhafte Bindung.

Der Master führt durch Vorbereitung, Messung und Ergebnis und zeigt Fortschritt.
Das zweite Telefon zeigt Logo und Status. Erst laufen ePassport, eID und der
separate Samsung-Auswahltest vollständig in einer NFC-Richtung, dann dieselbe
Folge mit vertauschten Karten-/Leserrollen. Der Master bleibt das Bediengerät.

Die vollständigen Dokumenttests verwenden die unveränderten, separat lizenzierten
nativen Protokollkerne: PACE, Secure Messaging, PA einschließlich CRL, CA, TA,
berechtigte Datengruppen und Abweisung eines geschützten Zugriffs vor TA. Isolierte
synthetische Profile ersetzen keine Nutzerprofile. Die reguläre Lizenz- und
Testaktivierung bleibt erforderlich und kann bei der ersten Nutzung Netz benötigen.

Der Samsung-Test ist zusätzlich ein authentifizierter synthetischer AES-GCM-
Transporttest mit einem 35-Byte-Befehl und geschütztem SELECT-Header. Er ist keine
zweite vollständige PACE/EAC-Session. Reader- und HCE-Nachweise werden korreliert;
nach RF-Deaktivierung dürfen ausstehende HCE-Antworten nicht in eine neue
RF-Verbindung gelangen.

## Patchbewertung

Vor dem Test steht ausdrücklich „Patchbedarf nicht bestimmbar – Test ausstehend“,
währenddessen „Patchbedarf nicht bestimmbar – Test läuft“. Wiederholen löscht
die bisherige Bewertung und startet einen neuen Lauf mit neuer ID. Erst das neue
Ergebnis darf eine Aussage liefern.

`not_observed` bedeutet, dass alle sechs Fälle dieses Laufs bestanden sind. Es
beweist keinen allgemeinen Patchverzicht: Ein bereits gepatchtes Gerät kann
selbstverständlich bestehen. `known_signature` setzt das kalibrierte ursprüngliche
SM-G991B-Build G991BXXSJHZC2, dessen exakten Bibliotheks-Hash, eine authentifizierte
Kontrolle und das korrelierte Fehlerbild voraus. Andere Fehler bleiben unbestimmt.
Das JSON-Feld `patch_required` bleibt deshalb null; die App bietet kein universelles
Firmware-Flashing an.

## Gemeinsames Log

Export über den Android-Dateidialog beendet die Kopplung nicht. Das JSON enthält
beide Gerätemodelle, verfügbare Build-/NFC-/App-Metadaten, native Bibliotheks-Hashes,
APK-Hash, aufgelöste App-Abhängigkeiten und gebundene Core-Versionen sowie UTC-
Zeitpunkte, Laufzeiten und begrenzte APDU-Metadaten. Reader-Sendezeit und HCE-
Empfangszeit bleiben getrennt. Keine vollständigen Dokumentdaten, privaten Schlüssel,
Kopplungscodes, Tokens oder Session-Materialien werden exportiert. Geräteuhren können
abweichen; Zeitdifferenzen zwischen Telefonen sind keine verlässliche Einweg-Latenz.

## Verifikation

* `python3 tests/test_nfc_test_assessment.py`: echte Kotlin-Implementierung für
  Bewertung, authentifizierten Probe-Befehl, TLS und Code-Kopplung.
* Native Hosttests: `cargo test --manifest-path native/Cargo.toml --locked --offline --lib`.
* Instrumentierung: Gradle `-PnfcTest :app:assembleDebug :app:assembleDebugAndroidTest`;
  Runner `de.phoenix.simulator.test/de.phoenix.simulator.NfcSessionInstrumentation`.
  `loopback=true` prüft vollständige lizenzierte Protokolle und RF-Deaktivierung,
  beweist aber keine physische NFC-Übertragung.
* TesterArmy: mit `PHOENIX_ANDROID_DEVICE=<ADB-Seriennummer>` und ADB im PATH
  `npx --no-install e2e run --config e2e.nfc-test.config.ts` aus diesem Projekt.
  `e2e.nfc-results.config.ts` setzt einen echten abgeschlossenen Hardwarelauf voraus
  und prüft Export und vollständige Wiederholung ohne neue Kopplung.
* `e2e.nfc-camera.config.ts`: zwei parallele Läufe mit `NFC_CAMERA_ROLE=master`
  bzw. `peer`; ein Mensch richtet die Kamera auf den QR-Code aus. Ein übertragener
  QR-Payload allein gilt ausdrücklich nicht als bestandener Kameratest.

Hardwarebelege liegen im übergeordneten `reports/nfc/`. Die Kampagne vom 24. Oktober
enthält vollständige erfolgreiche Läufe auf SM-G991B und SM-S918B einschließlich
Wiederholung und echtem Dateiexport. Ein früher RF-Abbruch wurde separat behalten;
seine ursprüngliche Ursache ist nicht bewiesen. Erfolgreiche spätere Läufe sind
kein Langzeit-Stabilitätsnachweis für alle Samsung-Firmwares.
