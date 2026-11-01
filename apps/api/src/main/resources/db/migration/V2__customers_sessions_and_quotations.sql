CREATE TABLE customer_account (
  id UUID PRIMARY KEY,
  issuer VARCHAR(512) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(issuer, subject)
);

-- Spring Session JDBC schema, aligned to the managed 4.0.x library. Sessions are
-- ephemeral security state, not financial records; cascading their attributes is intentional.
CREATE TABLE spring_session (
  primary_id CHAR(36) PRIMARY KEY,
  session_id CHAR(36) NOT NULL,
  creation_time BIGINT NOT NULL,
  last_access_time BIGINT NOT NULL,
  max_inactive_interval INT NOT NULL,
  expiry_time BIGINT NOT NULL,
  principal_name VARCHAR(512)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session(session_id);
CREATE INDEX spring_session_ix2 ON spring_session(expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session(principal_name);
CREATE TABLE spring_session_attributes (
  session_primary_id CHAR(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
  attribute_name VARCHAR(200) NOT NULL,
  attribute_bytes BYTEA NOT NULL,
  PRIMARY KEY(session_primary_id,attribute_name)
);

CREATE TABLE pricing_quotation (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL,
  offer_id UUID NOT NULL,
  version INT NOT NULL CHECK(version=1),
  mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
  total NUMERIC(14,2) NOT NULL CHECK(total>=0),
  currency CHAR(3) NOT NULL CHECK(currency='INR'),
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>created_at),
  payload JSONB NOT NULL
);
CREATE INDEX pricing_quotation_owner ON pricing_quotation(customer_id,id);
CREATE TABLE pricing_acceptance (
  id UUID PRIMARY KEY,
  quote_id UUID NOT NULL UNIQUE REFERENCES pricing_quotation(id),
  quote_version INT NOT NULL CHECK(quote_version=1),
  accepted_at TIMESTAMPTZ NOT NULL,
  payload JSONB NOT NULL
);
CREATE TABLE pricing_command (
  command_id UUID PRIMARY KEY,
  customer_id UUID NOT NULL,
  operation VARCHAR(32) NOT NULL CHECK(operation IN ('CREATE_QUOTE','ACCEPT_QUOTE')),
  command_key VARCHAR(100) NOT NULL CHECK(command_key ~ '^[A-Za-z0-9_-]{16,100}$'),
  fingerprint CHAR(64) NOT NULL,
  resource_id UUID NOT NULL REFERENCES pricing_quotation(id) DEFERRABLE INITIALLY DEFERRED,
  UNIQUE(customer_id,operation,command_key)
);
CREATE TABLE pricing_outbox (
  id UUID PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
  envelope JSONB NOT NULL,
  published_at TIMESTAMPTZ,
  UNIQUE(aggregate_id,event_type)
);
CREATE INDEX pricing_outbox_pending ON pricing_outbox(id) WHERE published_at IS NULL;

CREATE FUNCTION reject_pricing_history_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Pricing history is immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER immutable_quotation BEFORE UPDATE OR DELETE ON pricing_quotation
  FOR EACH ROW EXECUTE FUNCTION reject_pricing_history_change();
CREATE TRIGGER immutable_acceptance BEFORE UPDATE OR DELETE ON pricing_acceptance
  FOR EACH ROW EXECUTE FUNCTION reject_pricing_history_change();
CREATE TRIGGER immutable_pricing_command BEFORE UPDATE OR DELETE ON pricing_command
  FOR EACH ROW EXECUTE FUNCTION reject_pricing_history_change();
