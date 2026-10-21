# eID profile identity and municipality lookup

Each Android eID draft stores a fictional document serial in `document_number`.
The document preview, generated MRZ and profile search use the same value.
Serials are unique within the local profile store: save and import reject a
collision, existing duplicate serials are migrated to unused random values,
and a separate editable copy gets a separate serial. Personalization reset
preserves the serial. This is a simulator identifier, not a certificate serial
or an additional identifier returned by the eID protocol. Separate installations
have no central serial allocation service.

The visible eID profile name follows DG5 (surname). Given names and the serial
in the list distinguish people sharing a surname. The original native profile
name stays stable internally so this display migration does not reset existing
native credentials or RI personalization.

Saving a structured German DG17 address resolves DG18 through the existing
shared Rust Community-ID offline catalogue using postal code and municipality.
Street and house number are not used. Unresolved addresses do not invent a key;
the editor reports that lookup failed. Signed PersoSim originals are not changed.

## Device verification

On the connected SM-S918B, all 21 stored eID profiles had unique serials, surname
names and an unambiguous serial-to-profile lookup. The municipality audit found:

- 14 matching DG18 values;
- 3 mismatches in signed originals: PersoSim 07 (Halle), 09 and UB03 (Eisenach);
- 4 profiles outside automatic matching: one unstructured address, one synthetic
  invalid postal code and two foreign addresses.

The mismatching signed originals remain byte-for-byte unchanged. The lookup is
against the pinned bundled catalogue; it is not a live official register check.

Android build and lint passed. Device instrumentation verified duplicate-import
rejection, distinct copy serials, reset persistence, RI personalization, background
activation and SELECT APDUs. Relevant TesterArmy UI tests cover search and surname
persistence after restart, PKI selection, structured-address changes and RI settings.
Vercel was neither built nor deployed. Full AusweisApp/physical-reader authentication
was not repeated for these editor changes.
