# Konzept: verschlüsselter Upload von NFC-Diagnoselogs

Stand 24. Oktober 2026. Vorschlag, nicht implementiert. Es werden weder Logs
automatisch hochgeladen noch ein Server eingerichtet.

## Empfohlenes Serverziel

Ein eigener Cloudflare-Worker nimmt Diagnose-Uploads an. Der bestehende Lizenz-
Worker und seine Signier- und Lizenzberechtigungen bleiben getrennt. Vorgeschlagenes,
noch nicht eingerichtetes Ziel: `https://diagnostics.epassport-simulator.de/v1/nfc-reports`.
Die Domain gehört zum Worker, nicht zu einem öffentlich freigegebenen Bucket.

Der Worker schreibt die bereits PGP-verschlüsselte Datei in einen eigenen privaten
R2-Bucket per Bucket-Binding. Kein `r2.dev`-Zugang und keine öffentliche Bucket-
Domain. R2-Buckets sind standardmäßig privat; die Bindung erlaubt Zugriff aus
dem Worker. [R2 aus Workers](https://developers.cloudflare.com/r2/api/workers/workers-api-usage/),
[Private/public R2-Buckets](https://developers.cloudflare.com/r2/buckets/public-buckets/).

Die Datenbank enthält nur Report-ID, Besitzerzuordnung, Objektpfad, Dateigröße,
Ciphertext-Hash, Empfänger-Fingerabdruck, Uploadstatus sowie Erstellungs- und
Löschfrist. Dafür wird eine eigene Tabelle mit engen Zugriffsrechten verwendet;
der Diagnose-Worker erhält keinen allgemeinen Zugang zu Lizenz- oder PKI-Daten.
Gerätemetadaten und Loginhalt stehen nur in der verschlüsselten Datei.

Vercel mit privatem Blob-Storage wäre ebenfalls möglich. Für diesen Vorschlag ist
der getrennte Worker mit R2 die bevorzugte Variante; Datenbank und Dateispeicher
haben unterschiedliche Aufgaben. Es wurde kein Cloud-Ressource angelegt oder deployt.

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

Auf Nutzerwunsch ersetzt OpenPGP die frühere HPKE/Tink-Empfehlung. Die App
verschlüsselt den gesamten JSON-Inhalt vor dem Upload zu einer integritätsgeschützten
OpenPGP-Datei (`.pgp`) mit einer gepflegten OpenPGP-Bibliothek. Nachrichtenversion,
Algorithmen und Integritätsschutz müssen mit dem geprüften Empfängerschlüssel und
dem tatsächlichen Entschlüsselungswerkzeug kompatibel getestet werden; keine
selbst entworfene Verschlüsselung. [OpenPGP, RFC 9580](https://www.rfc-editor.org/rfc/rfc9580.html).

Lokal geprüfter öffentlicher Empfänger am 24. Oktober 2026:

* UID: `ePassport Simulator Team <contact@epassport-simulator.de>`.
  Der vorhandene Schlüssel schreibt `contact` mit c, nicht `contakt`.
* Primärer Fingerabdruck: `FD3DF3CC2F3B4D2116D413A5C59CD4AAABD99160`.
* RSA-4096-Verschlüsselungs-Unterschlüssel:
  `0A0C0B92467C2A50EB9578DA64FE711F9A5E204A`.

Die Adresse ist nur eine lesbare Identität, keine sichere Schlüsselauswahl und kein
E-Mail-Versandziel für diese Funktion. Maßgeblich sind der festgelegte Fingerabdruck,
gültige Bindung des Verschlüsselungs-Unterschlüssels sowie Ablauf-/Widerrufsprüfung.
Der Signier-/Zertifizierschlüssel wird nicht als Verschlüsselungsschlüssel benutzt.
Verschlüsselung allein authentifiziert keinen App-Nutzer; dafür bleibt die
separate Kontoanmeldung erforderlich. Die lokale Telefonkopplung wird nicht geändert.

Zusätzlich verwendet der Upload normales HTTPS mit Betriebssystem-CA-Vertrauen
und Hostnamenprüfung, vorzugsweise TLS 1.3, mindestens TLS 1.2. Die spezielle
lokale QR-Zertifikatsprüfung darf nicht zur Cloud-Trust-Konfiguration werden.
[Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config).

Der Empfänger-Public-Key wird mit der App ausgeliefert oder durch ein standardisiertes
signiertes Schlüsselmanifest aktualisiert, dessen Root-Verifikationsschlüssel in
der App liegt. Ein bloß vom Upload-Server gelieferter beliebiger Public-Key genügt
nicht: Ein kompromittierter Upload-Server könnte ihn austauschen. Manifestprüfung
umfasst Signatur, Gültigkeitszeit, freigegebene Fingerabdrücke und Widerrufe. Bei einem
Fehler gibt es keinen Klartext-Fallback und keine selbst entworfene Kryptografie.

Upload-Worker, Datenbank und R2 besitzen keinen privaten OpenPGP-Schlüssel und
keine Entsperrpassphrase. Berechtigte Supportmitarbeiter laden die `.pgp`-Datei
herunter; entschlüsselt wird separat mit dem privaten Verschlüsselungs-Unterschlüssel
des oben genannten Empfängers, zunächst lokal/offline. Die private Schlüsselhaltung
verbleibt beim Betreiber; kein privater Team-Schlüssel als Worker-/Vercel-Secret.
Wer den privaten Schlüssel besitzt, kann die Logs lesen. Eine spätere automatisierte
Entschlüsselung wäre eine separate Entscheidung samt eigener Schlüsselverwaltung.

## Minimale API und Datenfluss

1. Die angemeldete App fordert einen Upload an. Der Server erzeugt eine zufällige
   Report-ID und eine zufällige 32-Byte-Nonce, bindet beide an den Kontoinhaber
   und stellt ein einmaliges, fünf Minuten gültiges Uploadrecht aus.
2. Die App prüft Schema und Größenlimit und verschlüsselt das JSON mit dem
   freigegebenen OpenPGP-Empfängerschlüssel. Report-ID und Nonce stehen auch im
   verschlüsselten Inhalt. Geräte-/Diagnosefelder bleiben ausschließlich innen.
3. Ein authentifizierter HTTPS-POST überträgt die OpenPGP-Datei als undurchsichtige
   Daten. Der Server prüft Uploadrecht, Besitzer, Ablauf, erwartete Report-ID/Nonce,
   deklarierte Empfänger-Fingerabdrücke und harte Streaming-Größenlimits. Er kann
   weder den tatsächlichen Empfänger noch den inneren Kontext ohne Entschlüsselung
   bestätigen. Das getrennte Entschlüsselungswerkzeug prüft Empfänger, Integrität,
   inneren Kontext gegen die Metadaten und das JSON-Schema vor der Anzeige.
   Ein atomarer Statuswechsel verhindert doppelte Annahme. Wiederholte Requests
   derselben Upload-ID liefern dieselbe Quittung statt doppelt zu speichern.
4. Die App erhält eine zufällige Vorgangsnummer und Quittung. Abruf und Löschen
   erfordern erneut Eigentümerrechte; eine erratene Report-ID genügt nicht.

Vorschlag für die erste Version: höchstens 1 MiB JSON und 2 MiB OpenPGP-Datei, ohne Archive
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
  versprochen.

## Abnahmetests vor einer Freigabe

* OpenPGP-Roundtrip mit synthetischen Logs gegen das tatsächliche lokale GnuPG-
  Entschlüsselungswerkzeug; manipulierte Datei, falscher Schlüssel, unpassender
  Kontext und ungültiges JSON schlagen fehl. Keine Nutzlast in Fehlern/Telemetrie.
* Abgelaufene, widerrufene oder nicht an den festgelegten Primärschlüssel gebundene
  Verschlüsselungs-Unterschlüssel werden abgewiesen; Adresssuche ersetzt kein Pinning.
* Mitschnitt und direkter Zugriff auf Upload-Service/Objektspeicher ermöglichen
  ohne getrennten Empfängerschlüssel keine Entschlüsselung.
* Zweites Konto kann fremde Reports weder abrufen noch löschen; abgelaufene oder
  zweckfremde Uploadrechte, Replay und Größen-/Ratenüberschreitungen werden abgewiesen.
* Schlüsselmanifest-Signatur, Rotation, Sperre und ungültige Systemzeit werden
  geprüft; kein Trust-All und kein Klartext-Fallback.
* Abbruch/Wiederholung/Netzausfall verlieren kein lokales Log. Löschung,
  automatische Fristen, Backup-Retention und Audit-Zugriff sind praktisch überprüft.

Umsetzung in kleinen Schritten: Export-Allowlist festschreiben, OpenPGP lokal testen,
eigenen Diagnose-Worker mit privatem R2 und authentifizierter API mit Limits bauen,
separate Entschlüsselung und Schlüsselrotation prüfen,
Angriffs- und Löschtests durchführen, dann optional in der App freigeben.
