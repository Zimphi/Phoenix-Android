# Aufgaben

- [x] Bisherige Rust-Cores und gemeinsame Cargo-Abhängigkeiten plattformübergreifend vereinheitlicht, geprüft und gepusht.
- [x] Android: effektive eID-DG-Werte aus dem gemeinsamen Core, Kotlin-Build und 13 native Tests grün.
- [x] Android: automatische Speicherung, Werteinhalte und Dokumentdarstellung am Gerät testen.
- [x] Alle 33 Originalprofile nativ auf Laden geprüft: 8 ladbar, 25 mit explizitem Fehler.
- [x] Alle 33 Quellprofile unverändert zentral übernommen, Datei-Hashes geprüft; leeren Historienordner ausgewiesen.
- [x] Katalog unverändert lokal und Android laden, Speicherschlüssel getrennt verpacken; bestehende Entwürfe erhalten.
- [x] Vercel-Paketierung und Katalog-Identitäten angleichen und prüfen.
- [x] Android-Auswahl eID/ePassport → alle Profile einschließlich eigener → geladenes ausgewähltes Profil testen.
- [x] Dokumentdarstellungen in Profilübersicht und Editor, Navigationsabstände sowie Einstellungen ohne Speicherbutton/Profilnamen mit e2e prüfen.
- [ ] Abschließende UI-Gesamtprüfung, Graphify und gezielter Android-Commit/Push (Core/Host/Cloud-Katalog bereits gepusht).

- [x] Unabhängige App-Quellcodeprüfung abgeschlossen: ein niedriger IFD-Verfügbarkeitsbefund, kein bestätigter Daten-/Schlüsselzugriffs-Bypass.
- [ ] Gemeinsame TLS-Härtung integrieren: geprüfter Patch mit 3 Tests vorbereitet, Ausnahme zur Core-Unverändert-Vorgabe ausdrücklich angefragt und noch offen.
- [x] Vorhandenen QR-Geräteclient aufgenommen, Benutzerkennung/Abmelden und PKI-Einstellungsseite ergänzt; Status bleibt ehrlich von echter zentraler PKI-Bereitschaft abhängig.
- [ ] Live-PKI-Ausstellung testen, sobald Signierdienst und Datenbankzugang aus dem anderen Chat freigegeben/bereitgestellt sind.
- [ ] Live-Benutzeranzeige/Abmelden gegen den aktualisierten Status-Endpunkt mit user_id prüfen; kein bestehendes Konto wurde für einen Test abgemeldet.
- [x] Angemeldeten Benutzer sichtbar anzeigen; Benutzerübersicht mit Kennung und Abmelden ergänzen.
