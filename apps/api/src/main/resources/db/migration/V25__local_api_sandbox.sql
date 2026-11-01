CREATE TABLE demo_operation (
  id UUID PRIMARY KEY,owner_id UUID NOT NULL REFERENCES customer_account(id),command_key VARCHAR(128) NOT NULL,
  fingerprint VARCHAR(100) NOT NULL,mode TEXT NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK'),
  status TEXT NOT NULL CHECK(status IN ('QUEUED','RUNNING','RETRY','SIMULATED_DELIVERED','SIMULATED_REVIEW_REQUIRED','FAILED')),
  attempts INT NOT NULL CHECK(attempts BETWEEN 0 AND 3),version BIGINT NOT NULL CHECK(version>0),
  created_at TIMESTAMPTZ NOT NULL,next_at TIMESTAMPTZ NOT NULL,payload JSONB NOT NULL,
  lease_token UUID,lease_until TIMESTAMPTZ,
  UNIQUE(owner_id,command_key),CHECK((lease_token IS NULL)=(lease_until IS NULL)),
  CHECK((payload->>'id')::uuid=id AND (payload->>'owner')::uuid=owner_id AND payload->>'status'=status AND (payload->>'attempts')::int=attempts AND (payload->>'version')::bigint=version)
);
CREATE INDEX demo_owner_history ON demo_operation(owner_id,created_at DESC,id DESC);
CREATE INDEX demo_due ON demo_operation(next_at,created_at) WHERE status IN ('QUEUED','RUNNING','RETRY');
CREATE TABLE demo_event (
  id UUID PRIMARY KEY,operation_id UUID NOT NULL REFERENCES demo_operation(id),version BIGINT NOT NULL,envelope JSONB NOT NULL,
  UNIQUE(operation_id,version),CHECK((envelope->>'eventId')::uuid=id AND (envelope->>'aggregateId')::uuid=operation_id AND (envelope->>'aggregateVersion')::bigint=version)
);
-- Provider-owned fixture receipt deliberately has no platform-job FK: effects survive lost acknowledgements.
CREATE TABLE demo_delivery_receipt (operation_id UUID PRIMARY KEY,fingerprint VARCHAR(100) NOT NULL,accepted_at TIMESTAMPTZ NOT NULL);
CREATE FUNCTION guard_demo_operation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Simulation history is retained' USING ERRCODE='23514'; END IF;
  IF NEW.id<>OLD.id OR NEW.owner_id<>OLD.owner_id OR NEW.command_key<>OLD.command_key OR NEW.fingerprint<>OLD.fingerprint OR NEW.created_at<>OLD.created_at
    OR NEW.version<>OLD.version+1 OR (NEW.payload-ARRAY['status','attempts','version','updatedAt','nextAttemptAt','reference']) IS DISTINCT FROM (OLD.payload-ARRAY['status','attempts','version','updatedAt','nextAttemptAt','reference']) THEN
    RAISE EXCEPTION 'Simulation immutable input/version invariant' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER demo_input_guard BEFORE UPDATE OR DELETE ON demo_operation FOR EACH ROW EXECUTE FUNCTION guard_demo_operation();
CREATE FUNCTION immutable_demo_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Simulation event/receipt is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER demo_event_immutable BEFORE UPDATE OR DELETE ON demo_event FOR EACH ROW EXECUTE FUNCTION immutable_demo_history();
CREATE TRIGGER demo_receipt_immutable BEFORE UPDATE OR DELETE ON demo_delivery_receipt FOR EACH ROW EXECUTE FUNCTION immutable_demo_history();
CREATE INDEX payment_account_customer_report ON payment_account(customer_id,status);
