CREATE TABLE booking_intent (
  id UUID PRIMARY KEY,
  quote_id UUID NOT NULL UNIQUE,
  customer_id UUID NOT NULL,
  fingerprint CHAR(64) NOT NULL,
  mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
  input_snapshot JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL CHECK(version>=1),
  status VARCHAR(32) NOT NULL CHECK(status IN ('CREATED','VALIDATING','AWAITING_PAYMENT','REQUIRES_REQUOTE','INVENTORY_UNAVAILABLE','EXPIRED','MANUAL_REVIEW')),
  failure_code VARCHAR(64)
);
CREATE INDEX booking_owner_history ON booking_intent(customer_id,created_at DESC,id DESC);
CREATE TABLE booking_command (
  customer_id UUID NOT NULL,
  operation VARCHAR(32) NOT NULL CHECK(operation='CREATE_BOOKING'),
  command_key VARCHAR(100) NOT NULL CHECK(command_key ~ '^[A-Za-z0-9_-]{16,100}$'),
  fingerprint CHAR(64) NOT NULL,
  booking_id UUID NOT NULL REFERENCES booking_intent(id),
  PRIMARY KEY(customer_id,operation,command_key)
);
CREATE TABLE booking_workflow (
  booking_id UUID PRIMARY KEY REFERENCES booking_intent(id),
  step VARCHAR(32) NOT NULL CHECK(step='VALIDATE_OFFER'),
  status VARCHAR(24) NOT NULL CHECK(status IN ('READY','RUNNING','WAITING_RETRY','SUCCEEDED','FAILED','NEEDS_REVIEW')),
  attempts INT NOT NULL DEFAULT 0 CHECK(attempts>=0 AND attempts<=8),
  claim_count INT NOT NULL DEFAULT 0 CHECK(claim_count>=0),
  next_attempt_at TIMESTAMPTZ,
  lease_token UUID,
  lease_until TIMESTAMPTZ,
  read_started_at TIMESTAMPTZ,
  CHECK((status='RUNNING')=(lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX booking_workflow_due ON booking_workflow(next_attempt_at,booking_id) WHERE status IN ('READY','WAITING_RETRY');
CREATE INDEX booking_workflow_abandoned ON booking_workflow(lease_until) WHERE status='RUNNING';
CREATE TABLE booking_reservation (
  booking_id UUID PRIMARY KEY REFERENCES booking_intent(id),
  mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
  status VARCHAR(24) NOT NULL CHECK(status='NOT_REQUESTED'),
  inventory_status VARCHAR(24) NOT NULL CHECK(inventory_status IN ('NOT_CHECKED','AVAILABLE','UNAVAILABLE','UNKNOWN')),
  checked_at TIMESTAMPTZ,
  external_reference VARCHAR(128) CHECK(external_reference IS NULL)
);
CREATE TABLE booking_outbox (
  id UUID PRIMARY KEY,
  aggregate_id UUID NOT NULL REFERENCES booking_intent(id),
  aggregate_version BIGINT NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
  envelope JSONB NOT NULL,
  published_at TIMESTAMPTZ,
  UNIQUE(aggregate_id,aggregate_version)
);
CREATE INDEX booking_outbox_pending ON booking_outbox(id) WHERE published_at IS NULL;
CREATE FUNCTION reject_booking_command_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Booking command history is immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER immutable_booking_command BEFORE UPDATE OR DELETE ON booking_command
  FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE FUNCTION protect_booking_input() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Booking input history is immutable' USING ERRCODE='23514'; END IF;
  IF NEW.id IS DISTINCT FROM OLD.id OR NEW.quote_id IS DISTINCT FROM OLD.quote_id OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
    OR NEW.fingerprint IS DISTINCT FROM OLD.fingerprint OR NEW.mode IS DISTINCT FROM OLD.mode OR NEW.input_snapshot IS DISTINCT FROM OLD.input_snapshot
    OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.version<>OLD.version+1 THEN
    RAISE EXCEPTION 'Booking input/version invariant violated' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_booking_input BEFORE UPDATE OR DELETE ON booking_intent FOR EACH ROW EXECUTE FUNCTION protect_booking_input();
CREATE FUNCTION protect_booking_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Booking event history is immutable' USING ERRCODE='23514'; END IF;
  IF NEW.id IS DISTINCT FROM OLD.id OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id OR NEW.aggregate_version IS DISTINCT FROM OLD.aggregate_version
    OR NEW.event_type IS DISTINCT FROM OLD.event_type OR NEW.mode IS DISTINCT FROM OLD.mode OR NEW.envelope IS DISTINCT FROM OLD.envelope THEN
    RAISE EXCEPTION 'Booking event history is immutable' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_booking_event BEFORE UPDATE OR DELETE ON booking_outbox FOR EACH ROW EXECUTE FUNCTION protect_booking_event();
