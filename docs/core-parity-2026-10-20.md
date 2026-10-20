# Gemeinsame Rust-Core-Stände – 20. Oktober 2026

Android, lokaler Rust-Simulator und Vercel pinnen dieselben Quellen von eID,
ePassport, PQC und dem nativen Lizenzverifier, einschließlich verschachtelter
Abhängigkeiten. Für gemeinsam verwendete Registry-Versionslinien stimmen außerdem
Versionen und Paketprüfsummen überein. Die konkreten Git-Revisionen stehen in
`components.lock.json`.

Ein byteweiser Abgleich der Rust-Quellen und Cargo-Builddateien bestätigte:

| Core | Dateien | geprüfte identische Kopien |
| --- | ---: | ---: |
| eID | 105 | 3 |
| ePassport | 31 | 4 |
| Lizenzverifier | 60 | 4 |
| PQC | 55 | 5 |

Prüfungen: lokale Rust-Suite 66 bestanden / 75 vorhandene lizenzabhängige Tests
ignoriert; eID 56 bestanden / 2 vorhanden ignoriert; ePassport-Unit/PACE 7 bestanden;
Terminal 108 bestanden; lokale Clippy-Prüfung mit `-D warnings` erfolgreich.
Android baut mit dem offiziellen NDK und unveränderten gemeinsamen Core-Quellen.

Die Prüfungen gegen abweichende Gitlinks, nachträglich veränderte Rust-Quellen und
nicht versionierte Rust-Builddateien sind Teil der Build-/Integrationstests.
Der Vercel-spezifische Rust-Patch und der nachträgliche PQC-Austausch entfallen.
Der vorhandene reine Python-Ausgabepatch bleibt bestehen.

Keine neue Cloud-Anbindung, keine Änderung der Android-Oberfläche in diesem Commit.
Der vollständige Linux/Python-3.13-Worker und ein Live-Vercel-Deployment wurden auf
diesem Mac nicht ausgeführt; die Rust-Konfigurationen werden hier separat geprüft.
