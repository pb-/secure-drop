CREATE TABLE datoms (
    entity TEXT NOT NULL,
    attribute TEXT NOT NULL,
    value TEXT NOT NULL,
    time REAL NOT NULL,
    retraction INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX t ON datoms (time);
