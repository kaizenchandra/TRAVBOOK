CREATE TABLE trip_document (
 id UUID PRIMARY KEY,customer_id UUID NOT NULL,booking_id UUID NOT NULL,checkout_id UUID NOT NULL,checkout_version BIGINT NOT NULL CHECK(checkout_version>0),
 kind VARCHAR(40) NOT NULL CHECK(kind='MOCK_CONFIRMATION_SUMMARY'),mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
 created_at TIMESTAMPTZ NOT NULL,body TEXT NOT NULL,sha256 CHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 byte_length INT NOT NULL CHECK(byte_length BETWEEN 1 AND 32768),CHECK(octet_length(body)=byte_length),
 UNIQUE(customer_id,booking_id,kind)
);
CREATE TABLE trip_document_command (
 customer_id UUID NOT NULL,command_key VARCHAR(100) NOT NULL CHECK(command_key ~ '^[A-Za-z0-9_-]{16,100}$'),
 booking_id UUID NOT NULL,document_id UUID NOT NULL REFERENCES trip_document(id),command_id UUID NOT NULL UNIQUE,PRIMARY KEY(customer_id,command_key)
);
CREATE TABLE trip_document_outbox (
 id UUID PRIMARY KEY,document_id UUID NOT NULL UNIQUE REFERENCES trip_document(id),envelope JSONB NOT NULL,published_at TIMESTAMPTZ
);
CREATE FUNCTION reject_trip_document_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Document history is immutable' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER immutable_trip_document BEFORE UPDATE OR DELETE ON trip_document FOR EACH ROW EXECUTE FUNCTION reject_trip_document_change();
CREATE TRIGGER immutable_trip_document_command BEFORE UPDATE OR DELETE ON trip_document_command FOR EACH ROW EXECUTE FUNCTION reject_trip_document_change();
CREATE FUNCTION protect_document_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Document event is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.id<>OLD.id OR NEW.document_id<>OLD.document_id OR NEW.envelope IS DISTINCT FROM OLD.envelope THEN RAISE EXCEPTION 'Document event is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_document_event BEFORE UPDATE OR DELETE ON trip_document_outbox FOR EACH ROW EXECUTE FUNCTION protect_document_event();
