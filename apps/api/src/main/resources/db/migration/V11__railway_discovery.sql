CREATE TABLE rail_station(code VARCHAR(5) PRIMARY KEY,name VARCHAR(80) NOT NULL,city VARCHAR(40) NOT NULL,timezone VARCHAR(60) NOT NULL);
-- Small factual identifier catalog; all schedules, services and fares are synthetic.
INSERT INTO rail_station VALUES
 ('NDLS','New Delhi','Delhi','Asia/Kolkata'),('MMCT','Mumbai Central','Mumbai','Asia/Kolkata'),
 ('AGC','Agra Cantt','Agra','Asia/Kolkata'),('JP','Jaipur','Jaipur','Asia/Kolkata'),
 ('MAO','Madgaon','Goa','Asia/Kolkata'),('HWH','Howrah','Howrah','Asia/Kolkata');
CREATE TABLE rail_search(id UUID PRIMARY KEY,mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,payload JSONB NOT NULL,CHECK(expires_at>created_at),CHECK((payload->>'id')::uuid=id));
CREATE INDEX rail_search_expiry ON rail_search(expires_at);
CREATE TABLE rail_journey_index(id UUID PRIMARY KEY,search_id UUID NOT NULL REFERENCES rail_search(id));
CREATE INDEX rail_journey_search ON rail_journey_index(search_id);
