CREATE TABLE reports (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  request_id TEXT NOT NULL,
  nonce TEXT NOT NULL,
  created INTEGER NOT NULL,
  expires INTEGER NOT NULL,
  ticket_expires INTEGER NOT NULL,
  state TEXT NOT NULL CHECK(state IN ('reserved','complete','deleting')),
  sha256 TEXT,
  bytes INTEGER,
  payload BLOB CHECK(payload IS NULL OR length(payload)<=1900000),
  UNIQUE(owner, request_id)
);
CREATE INDEX reports_owner_created ON reports(owner, created);
CREATE INDEX reports_expiry ON reports(state, expires);

CREATE INDEX reports_created ON reports(created);
