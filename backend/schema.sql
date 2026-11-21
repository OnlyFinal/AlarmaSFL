CREATE TABLE IF NOT EXISTS price_history (
  market TEXT NOT NULL CHECK (market = 'p2p'),
  crop TEXT NOT NULL,
  price REAL NOT NULL CHECK (price >= 0),
  captured_at INTEGER NOT NULL,
  PRIMARY KEY (market, crop, captured_at)
);

CREATE INDEX IF NOT EXISTS idx_price_history_crop_time
ON price_history (market, crop, captured_at);
