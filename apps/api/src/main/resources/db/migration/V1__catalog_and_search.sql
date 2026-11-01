CREATE TABLE catalog_location (
  id VARCHAR(3) PRIMARY KEY CHECK (id ~ '^[A-Z]{3}$'),
  name VARCHAR(160) NOT NULL,
  city VARCHAR(80) NOT NULL,
  country CHAR(2) NOT NULL,
  timezone VARCHAR(64) NOT NULL
);
INSERT INTO catalog_location VALUES
('DEL','Indira Gandhi International Airport','New Delhi','IN','Asia/Kolkata'),
('BOM','Chhatrapati Shivaji Maharaj International Airport','Mumbai','IN','Asia/Kolkata'),
('BLR','Kempegowda International Airport','Bengaluru','IN','Asia/Kolkata'),
('GOI','Dabolim Airport','Goa','IN','Asia/Kolkata'),
('MAA','Chennai International Airport','Chennai','IN','Asia/Kolkata'),
('HYD','Rajiv Gandhi International Airport','Hyderabad','IN','Asia/Kolkata'),
('CCU','Netaji Subhas Chandra Bose International Airport','Kolkata','IN','Asia/Kolkata'),
('JAI','Jaipur International Airport','Jaipur','IN','Asia/Kolkata');
CREATE TABLE search_snapshot (
  id UUID PRIMARY KEY,
  mode VARCHAR(10) NOT NULL CHECK (mode = 'MOCK'),
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
  payload JSONB NOT NULL
);
CREATE INDEX search_snapshot_expiry ON search_snapshot(expires_at);
CREATE TABLE search_offer (
  id UUID PRIMARY KEY,
  search_id UUID NOT NULL REFERENCES search_snapshot(id),
  total NUMERIC(14,2) NOT NULL CHECK (total >= 0),
  currency CHAR(3) NOT NULL CHECK(currency = 'INR'),
  payload JSONB NOT NULL
);
CREATE INDEX search_offer_snapshot ON search_offer(search_id);
COMMENT ON TABLE search_snapshot IS 'Public non-personal MOCK discovery snapshots; not quotations or guaranteed inventory.';

