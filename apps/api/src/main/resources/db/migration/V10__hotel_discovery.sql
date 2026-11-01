-- Independent hotel observations. No flight pricing, booking or financial data changes.
CREATE TABLE hotel_search (
 id UUID PRIMARY KEY, mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
 created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 payload JSONB NOT NULL,
 CHECK(expires_at>created_at), CHECK((payload->>'id')::uuid=id)
);
CREATE INDEX hotel_search_expiry ON hotel_search(expires_at);
CREATE TABLE hotel_offer_index (
 id UUID PRIMARY KEY, search_id UUID NOT NULL REFERENCES hotel_search(id),
 property_id VARCHAR(80) NOT NULL, room_index INTEGER NOT NULL CHECK(room_index BETWEEN 0 AND 3)
);
CREATE INDEX hotel_offer_search ON hotel_offer_index(search_id);
