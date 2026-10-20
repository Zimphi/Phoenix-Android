# Profile editor and shared catalogue

The app uses the exact private core catalogue pinned in passport-catalog.lock.json:
33 original synthetic ePassport profiles with original file hashes, signed data,
test keys and protocol choices. Eight passed native load checks; 25 are explicitly
unavailable, with the actual rejection reason. One source directory is history-only.
Unsupported combinations are not silently migrated or downgraded.

Catalogue data is supplied by the private native host, not public app assets.
Original catalogue groups/settings remain read-only; own drafts remain editable.
Existing drafts are preserved. An older own draft colliding with a newly reserved
catalogue ID is retained under a new internal own-* ID with unchanged data/name.
The 100-own-profile limit excludes bundled templates; total storage remains bounded.

The editor reads effective eID values and decodes loaded passport MRZ data through
the existing Rust parser. DG values appear above their DG/description label, with a
schematic document below the selected profile name. Valid settings autosave; an
invalid incomplete field does not replace the last valid persisted settings.
Simulation chooses document type and profile, mounts it without arming either
external transport, and requires a separate explicit start. Unsupported profiles
have no enabled simulation start.

Validation: 13 native adapter tests, 11 Python build/repository checks, Android
build and Lint, isolated on-device store/JNI/Keystore checks and five TesterArmy
catalogue/editor tests passed. The local shared adapter actually loaded 8 originals
and rejected the other 25. Cloud packaging preserved every file hash of all 33.
No full authentication claim is made for all catalogue profiles; no live cloud
catalogue replacement or deployment was performed by this task.

Account UI uses the authenticated device-status user_id and offers confirmed
server-side device disconnect as Abmelden. Actual central certificate issuance
still depends on the PKI-service deployment/configuration in the other task.
