# Umsetzung: Profileditor und gemeinsamer ePassport-Katalog

## Ziel
Die angeforderten Android-Ansichten vereinheitlichen und alle 33 vollständigen
lokalen ePassport-Testprofile auf Android, lokal und Vercel mit identischen
Katalog-IDs, Daten und Protokollauswahlen verfügbar machen. Der 34. Ordner ist eine
leere Historie und wird nicht als ladbares Profil erfunden.

## Architektur
- Die vorhandenen Rust-Protokollcores bleiben die einzige Protokollimplementierung.
- Alle Profile bleiben unverändert. Nicht unterstützte Kombinationen werden mit
  dem tatsächlichen Fehlergrund als nicht simulierbar gekennzeichnet.
- Der versionierte Katalog mit Testschlüsseln bleibt beim privaten ePassport-Core.
  Öffentliche Android-Quellen enthalten keine privaten Profile oder Core-Binärdateien.
- Jeder Verbraucher prüft den Katalog-Hash. Schlüssel werden beim lokalen Import mit
  dem jeweiligen Profilspeicherschlüssel verpackt. Datengruppen und Protokolle bleiben
  über die Plattformen gleich; die ursprünglichen Signaturen/Schlüssel werden zentral geteilt.
- Bestehende Benutzerentwürfe und getrennte, derzeit vorhandene PKI-Geräteänderungen
  werden erhalten. Die neue Oberfläche wird getrennt von der bereits gepushten
  Rust-Pin-Vereinheitlichung committed.

## Reihenfolge
1. Effektive DG-Werte und automatische Übernahme im Android-Editor verifizieren.
2. Zentralen Katalog mit allen 33 Originalprofilen erstellen und Hashes vergleichen;
   Ergebnis der Ladeprüfung (8 ladbar, 25 nicht unterstützt) hinterlegen.
3. Gemeinsame lokale Kataloginstallation und Android-Laden anbinden; Quelle und
   Katalog-Pins für alle Plattformen aktualisieren.
4. Vercel-Paketierung und verschlüsselte Profildaten auf denselben Katalog ausrichten.
5. Dokumentdarstellungen, zweistufige Auswahl und Navigationsabstände am Gerät mit
   TesterArmy e2e prüfen; eigene Profile bleiben in der Auswahl sichtbar.

## Prüfungen
Vorhandene Rust-/Python-Verifikation bleibt erhalten. Zusätzlich tatsächliche
Katalog-Ladevorgänge für jedes Profil, Hash-/Identitätsvergleich der Verbraucher,
DG-/Protokollvergleich, Android-JNI-Smoke und UI-Regression. Keine stillen
Protokoll-Downgrades und keine erfundenen grünen Hardware- oder Cloud-Ergebnisse.
