CREATE TABLE customer_organizer (
 customer_id UUID PRIMARY KEY REFERENCES customer_account(id),version BIGINT NOT NULL CHECK(version>0),payload JSONB NOT NULL,
 CHECK((payload->>'customerId')::uuid=customer_id AND (payload->>'version')::bigint=version),
 CHECK(jsonb_array_length(payload->'fields'->'travellers')<=20 AND jsonb_array_length(payload->'fields'->'bookmarks')<=20 AND jsonb_array_length(payload->'fields'->'searches')<=20)
);
CREATE TABLE organizer_command (
 customer_id UUID NOT NULL REFERENCES customer_account(id),command_key VARCHAR(128) NOT NULL,fingerprint CHAR(64) NOT NULL,
 applied_version BIGINT NOT NULL,correlation_id UUID NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(customer_id,command_key),UNIQUE(customer_id,applied_version)
);
CREATE FUNCTION guard_organizer() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Clear saved fields through the versioned organizer API' USING ERRCODE='23514'; END IF;
 IF NEW.customer_id<>OLD.customer_id OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Organizer version invariant' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER organizer_guard BEFORE UPDATE OR DELETE ON customer_organizer FOR EACH ROW EXECUTE FUNCTION guard_organizer();
CREATE FUNCTION immutable_organizer_command() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Organizer receipt is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER organizer_command_immutable BEFORE UPDATE OR DELETE ON organizer_command FOR EACH ROW EXECUTE FUNCTION immutable_organizer_command();
