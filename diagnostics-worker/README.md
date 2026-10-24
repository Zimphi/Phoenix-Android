# Phoenix NFC diagnostics worker

Separate upload service: no license signing keys, private OpenPGP keys, or access
to the license/PKI database. Ciphertext and metadata use one dedicated D1 database in the Workers Free account.
No R2 subscription or paid plan is required.

## Contract and trust boundaries

The Android master previews an immutable diagnostic report and requests explicit
consent before encrypting it to the bundled, pinned team OpenPGP encryption subkey.
No automatic upload; the existing export and temporary phone pairing remain usable.
The original report is at most 1 MiB; ciphertext at most 1,900,000 bytes (below the D1 row limit).

Authentication reuses the enrolled Android device key and the existing fixed-origin
`/api/auth/device/status` verifier. The diagnostic worker has no device public-key
registry or new signing secret. Each request carries a fresh standard PhoenixDevice
proof, with its nonce bound to the diagnostic origin, method, path, SHA-256 body
hash and random salt. The worker recomputes that binding, forwards the standard
proof and literal `{}` to the configured existing status endpoint, and requires
matching authenticated device/user IDs. The backend consumes the nonce to prevent
replay. Arbitrary upstream origins, redirects and caller-supplied URLs are rejected.
One deployment accepts production and preview device proofs. The explicit
`X-Phoenix-Diagnostics-Environment` header selects one of two fixed status verifiers;
the existing signature binds that environment and verifier URL. Missing headers
retain production compatibility. Preview owner IDs are namespaced in the same
database, so matching UUIDs across environments do not share report access.

* `POST /v1/nfc-reports`: authenticated reservation, returns random report ID,
  upload nonce and a five-minute expiry. Per-owner quotas apply atomically in D1.
* `PUT /v1/nfc-reports/{id}`: authenticated owner uploads binary OpenPGP, atomically
  validates size and ciphertext hash, then commits the ciphertext BLOB and
  metadata in one conditional D1 update. Idempotent retry of identical complete content is accepted.
* `GET /v1/nfc-reports`: authenticated owner's metadata only, bounded result count.
* `GET /v1/nfc-reports/{id}`: authenticated owner downloads ciphertext with no-store.
* `DELETE /v1/nfc-reports/{id}`: authenticated owner deletes ciphertext and its content metadata. A minimal deletion/quota tombstone remains at most 24 hours from creation.
* Scheduled cleanup deletes expired reservations and reports after 14 days,
  including interrupted uploads. Delete state is durable and retryable.

The encrypted payload also includes report ID and nonce. The server cannot check
those inner fields or the log schema without the private key; an offline tool
must check integrity, recipient, context and schema before displaying decrypted data. Studio currently saves ciphertext only and does not claim to validate decrypted contents.
Metadata does not include device models, library inventory, email or raw IP.
No payloads, proof headers, tickets or encryption materials enter application logs.
No browser CORS, public storage binding or private-key server secret.

## Verification requirements

Use the actual Worker runtime with local D1 for happy-path, ownership,
nonce binding, replay, quotas, expiry, atomic write/delete races, cleanup,
size limits and malformed request tests. Encrypt a synthetic log on the JVM and
decrypt with local GnuPG to verify the actual pinned recipient and integrity.
TesterArmy checks consent, cancellation, errors and success in the Android UI.
Only synthetic data may be used for live acceptance.


## Operator collection

`GET /v1/operator/nfc-reports[?after=<uuid>]` returns up to 50 reports without owner identities and a next cursor. `GET /v1/operator/nfc-reports/{id}` returns ciphertext. These routes accept only a separate 256-bit Base64url bearer capability whose SHA-256 is the Worker secret `OPERATOR_TOKEN_SHA256`. They have no mutation rights. Operator credentials must never be reused for licensing or Supabase. Rotate by replacing the secret and the private local token file. Phoenix License Studio selects that file and collects verified, private, no-clobber downloads. It backs off on HTTP 429. New reports arriving behind the current UUID cursor are collected on the next run.

## Deployment

The deployment uses account `eae8c4b088e64e865f4552294a4397e0`, Worker
`phoenix-nfc-diagnostics` and its own D1 database `phoenix-nfc-diagnostics`.
The fixed endpoint is `https://phoenix-nfc-diagnostics.phoenix-license-relay.workers.dev`.
Only the new database ID belongs in this Worker configuration. Apply migrations
with `wrangler d1 migrations apply phoenix-nfc-diagnostics --remote`, then deploy
this directory with `wrangler deploy`. Existing license Workers and databases
must not be rebound or modified. Preview URLs and application observability are disabled.

Create a fresh 32-byte operator token locally; keep its 43-character Base64url
form in a private file (0600), and provide only its SHA-256 hex digest through
`wrangler secret put OPERATOR_TOKEN_SHA256`. No token belongs in Git or logs.
Studio selects this private file. Verify health, unauthenticated denial, operator
collection and a synthetic upload/download/delete before real collection.

The Free plan stops at provider limits instead of enabling paid overages. The
application also limits each owner to 20 reservations/day, all owners together
to 200/day and reserves at most 64 MiB of logical active capacity. This budget is
not a hard bound on SQLite physical allocation or provider backup space. At limits,
uploads fail closed and local export stays available. Cleanup runs every 15 minutes;
active reports expire after 14 days. Deleted/expired ciphertext can remain in
Cloudflare D1 Time Travel backup history for up to seven further days. Shared
recipient encryption does not provide immediate cryptographic erasure per report.
Workers Free has a 10 ms CPU limit; local Miniflare tests do not prove production
CPU acceptance for the maximum-size payload. Run a live synthetic limit test.

Revoking the Android device key prevents new uploads and owner API access, including later deletion requests. Existing ciphertext still expires after 14 days and can be removed by the Cloudflare operator if necessary. A client cancellation after a completed server write may leave an acknowledged-or-unknown encrypted copy until owner deletion or expiry; cancellation is not claimed to prove deletion.

Verification on 2026-10-24: all eleven Miniflare/D1 tests pass, including both
verifiers, environment substitution and cross-environment ownership denial.
The installed S23 build completed a real synthetic encrypted upload and owner
deletion using its existing preview enrollment against this single deployment.
