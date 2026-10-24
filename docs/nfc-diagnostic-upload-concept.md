# Konzept: verschlüsselter Upload von NFC-Diagnoselogs

Stand 24. Oktober 2026. Vorschlag, nicht implementiert. Es werden weder Logs
automatisch hochgeladen noch ein Server eingerichtet.

## Nutzerablauf

Die Ergebnisseite erhält optional „Log sicher senden“. Vor dem Senden zeigt die
App Inhalt, Empfänger, Aufbewahrungsdauer und Zweck an. Erst eine ausdrückliche
Aktion sendet das gemeinsame Log beider Telefone. Lokales Speichern bleibt möglich.
Keine Wiederverwendung von Kopplungscode, QR-Token oder lokaler Sitzung als
Cloud-Zugang. Abbruch oder Netzwerkfehler lassen das lokale Ergebnis bestehen.

Die bestehenden Exporte enthalten Geräte-/Firmware- und Bibliotheksdaten und sind
deshalb trotz synthetischer Dokumentprofile vertraulich zu behandeln. Unmittelbar
vor dem Verschlüsseln wird das Export-Schema über eine Feld-Allowlist geprüft.
Keine privaten Schlüssel, vollständigen APDUs/Dokumente, QR-Bilder, Kopplungsdaten,
Seriennummern, IMEI, SSID oder MAC-Adressen. Zulässige Metadaten beider Telefone,
UTC-Zeitpunkte und begrenzte Messnachweise bleiben im verschlüsselten Inhalt.

## Verschlüsselung und Vertrauensgrenzen

Die App verschlüsselt den gesamten JSON-Inhalt vor dem Upload mit einer gepflegten
Bibliothek, vorgeschlagen Google Tink HPKE mit
`DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`. Die vorhandene TLS-Kopplung
zwischen den Telefonen wird hierfür nicht erweitert. HPKE schützt Vertraulichkeit
und Integrität zum Empfänger, authentifiziert in dieser Verwendung aber nicht den
Absender; dafür ist die separate Kontoanmeldung nötig.
[Tink Hybrid Encryption](https://developers.google.com/tink/hybrid).

Zusätzlich verwendet der Upload normales HTTPS mit Betriebssystem-CA-Vertrauen
und Hostnamenprüfung, vorzugsweise TLS 1.3, mindestens TLS 1.2. Die spezielle
lokale QR-Zertifikatsprüfung darf nicht zur Cloud-Trust-Konfiguration werden.
[Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config).

Der Empfänger-Public-Key wird mit der App ausgeliefert oder durch ein standardisiertes
signiertes Schlüsselmanifest aktualisiert, dessen Root-Verifikationsschlüssel in
der App liegt. Ein bloß vom Upload-Server gelieferter beliebiger Public-Key genügt
nicht: Ein kompromittierter Upload-Server könnte ihn austauschen. Manifestprüfung
umfasst Signatur, Gültigkeitszeit, freigegebene Suite und Schlüssel-ID. Bei einem
Fehler gibt es keinen Klartext-Fallback und keine selbst entworfene Kryptografie.

Upload-API und Objektspeicher besitzen keinen Entschlüsselungsschlüssel. Nur ein
getrennter, autorisierter Diagnoseprozess kann entschlüsseln. Sein privates Tink-
Keyset wird durch einen KMS-Schlüssel verschlüsselt gespeichert und erst im
gesondert berechtigten Prozess in RAM verfügbar gemacht. Das setzt keine native
X25519-Unterstützung im HSM voraus. Administratoren mit Zugriff auf diesen
Prozess bzw. dessen Schlüssel können Inhalte lesen: Das ist kein Versprechen,
dass niemand beim Betreiber entschlüsseln kann.

## Minimale API und Datenfluss

1. Die angemeldete App fordert einen Upload an. Der Server erzeugt eine zufällige
   Report-ID und einen zufälligen 32-Byte-Kontext, bindet beide an den Kontoinhaber
   und stellt ein einmaliges, fünf Minuten gültiges Uploadrecht aus.
2. Die App prüft Schema und Größenlimit und verschlüsselt das JSON mit dem
   freigegebenen Empfängerschlüssel. Der Server-Kontext wird als HPKE-Kontext
   gebunden. Außerhalb des Ciphertexts stehen nur Hüllenversion, Schlüssel-ID,
   Kontext und Ciphertext; Gerätemodelle und Diagnosebefund bleiben innen.
3. Ein authentifizierter HTTPS-POST überträgt die Hülle als undurchsichtige Daten.
   Der Server prüft Eigentümer, Ablauf, erlaubte Schlüssel-ID, erwarteten Kontext,
   einmalige Verwendung und harte Streaming-Größenlimits. Wiederholte Requests
   derselben Upload-ID liefern dieselbe Quittung statt doppelt zu speichern.
4. Die App erhält eine zufällige Vorgangsnummer und Quittung. Abruf und Löschen
   erfordern erneut Eigentümerrechte; eine erratene Report-ID genügt nicht.

Vorschlag für die erste Version: höchstens 1 MiB JSON und 2 MiB Hülle, ohne Archive
oder Kompression. Die derzeitigen vollständigen Logs liegen deutlich darunter.
Limits pro Konto und IP begrenzen Missbrauch. Objektpfade erzeugt allein der
Server; keine vom Client vorgegebenen Dateipfade oder beliebigen Download-URLs.
Ein allgemeiner anonymer Upload-Bucket ist nicht Teil dieses Vorschlags.

## Betrieb, Zugriff und Löschung

* Diagnosezugriff nur mit Rollenrechten, Mehrfaktoranmeldung und zugeordnetem
  Supportzweck; Entschlüsselungen werden pro Report revisionsfähig protokolliert.
  API-/Proxy-Logs enthalten keine Nutzlast, Tokens oder Schlüssel. Auch IP-Logs
  bekommen eine ausdrücklich begrenzte Aufbewahrung.
* Vorschlag: automatische Löschung nach 14 Tagen, konfigurierbare Obergrenze
  30 Tage, manuelles Löschen früher möglich. Objekt, Datenbankeintrag und
  Backup-Lebenszyklus müssen im Löschkonzept zusammenpassen. Keine Zusage
  sofortiger kryptografischer Einzelreport-Löschung bei gemeinsamem Empfängerschlüssel.
* Rotation mit versionierten Schlüssel-IDs; alte Schlüssel bleiben nur für die
  verbleibende Aufbewahrung nötig. Notfallsperre verhindert neue Uploads an einen
  kompromittierten Schlüssel. Ein Austausch setzt ein authentifiziertes Manifest
  bzw. App-Update voraus. Empfängerschlüssel-Kompromittierung kann bereits
  gespeicherte Ciphertexte offenlegen; langfristige Forward Secrecy wird nicht
  versprochen. [HPKE, RFC 9180](https://www.rfc-editor.org/rfc/rfc9180).

## Abnahmetests vor einer Freigabe

* Tink-Roundtrip mit synthetischen Logs, manipulierte Hülle/falscher Schlüssel/
  falscher Kontext schlagen fehl; keine geheime Nutzlast in Fehlern oder Telemetrie.
* Mitschnitt und direkter Zugriff auf Upload-Service/Objektspeicher ermöglichen
  ohne getrennten Empfängerschlüssel keine Entschlüsselung.
* Zweites Konto kann fremde Reports weder abrufen noch löschen; abgelaufene oder
  zweckfremde Uploadrechte, Replay und Größen-/Ratenüberschreitungen werden abgewiesen.
* Schlüsselmanifest-Signatur, Rotation, Sperre und ungültige Systemzeit werden
  geprüft; kein Trust-All und kein Klartext-Fallback.
* Abbruch/Wiederholung/Netzausfall verlieren kein lokales Log. Löschung,
  automatische Fristen, Backup-Retention und Audit-Zugriff sind praktisch überprüft.

Umsetzung in kleinen Schritten: Export-Allowlist festschreiben, Tink lokal testen,
authentifizierte API mit Limits bauen, getrennten Schlüsselbetrieb einrichten,
Angriffs- und Löschtests durchführen, dann optional in der App freigeben.
