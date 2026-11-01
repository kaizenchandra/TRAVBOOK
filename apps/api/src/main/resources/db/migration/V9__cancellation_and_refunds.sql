CREATE TABLE booking_cancellation_quote (
 id UUID PRIMARY KEY,customer_id UUID NOT NULL,booking_id UUID NOT NULL,checkout_id UUID NOT NULL REFERENCES booking_checkout(id),
 snapshot JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE booking_cancellation (
 id UUID PRIMARY KEY,customer_id UUID NOT NULL,booking_id UUID NOT NULL UNIQUE,quote_id UUID NOT NULL REFERENCES booking_cancellation_quote(id),
 snapshot JSONB NOT NULL,version BIGINT NOT NULL CHECK(version>0),cancellation_status VARCHAR(12) NOT NULL CHECK(cancellation_status IN ('PENDING','CANCELLED','REJECTED','REVIEW')),
 refund_status VARCHAR(16) NOT NULL CHECK(refund_status IN ('NOT_REQUESTED','PENDING','SUCCEEDED','NOT_DUE','REVIEW')),
 CHECK(refund_status='NOT_REQUESTED' OR cancellation_status='CANCELLED'),
 CHECK(snapshot->>'cancellationStatus'=cancellation_status AND snapshot->>'refundStatus'=refund_status AND (snapshot->>'version')::bigint=version)
);
CREATE TABLE booking_cancellation_command (
 customer_id UUID NOT NULL,operation VARCHAR(12) NOT NULL CHECK(operation IN ('QUOTE','CANCEL')),command_key VARCHAR(100) NOT NULL CHECK(command_key ~ '^[A-Za-z0-9_-]{16,100}$'),fingerprint VARCHAR(100) NOT NULL,resource_id UUID NOT NULL,
 PRIMARY KEY(customer_id,operation,command_key)
);
CREATE TABLE booking_cancellation_work (
 cancellation_id UUID PRIMARY KEY REFERENCES booking_cancellation(id),definition_version INT NOT NULL DEFAULT 1 CHECK(definition_version=1),
 state VARCHAR(12) NOT NULL CHECK(state IN ('READY','RUNNING','RETRY','DONE','REVIEW')),attempts INT NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 6),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),lease_token UUID,lease_until TIMESTAMPTZ,
 CHECK((state='RUNNING')=(lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX cancellation_due ON booking_cancellation_work(next_attempt_at) WHERE state IN ('READY','RETRY');
CREATE INDEX cancellation_abandoned ON booking_cancellation_work(lease_until) WHERE state='RUNNING';
CREATE TABLE booking_aftercare_outbox (
 id UUID PRIMARY KEY,aggregate_id UUID NOT NULL,aggregate_version BIGINT NOT NULL,event_type VARCHAR(80) NOT NULL,mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),envelope JSONB NOT NULL,published_at TIMESTAMPTZ,UNIQUE(aggregate_id,aggregate_version)
);
CREATE TRIGGER immutable_aftercare_event BEFORE UPDATE OR DELETE ON booking_aftercare_outbox FOR EACH ROW EXECUTE FUNCTION protect_booking_event();
CREATE FUNCTION reject_aftercare_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Aftercare history is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER immutable_cancellation_quote BEFORE UPDATE OR DELETE ON booking_cancellation_quote FOR EACH ROW EXECUTE FUNCTION reject_aftercare_history();
CREATE TRIGGER immutable_cancellation_command BEFORE UPDATE OR DELETE ON booking_cancellation_command FOR EACH ROW EXECUTE FUNCTION reject_aftercare_history();
CREATE FUNCTION protect_cancellation_input() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Cancellation history is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.id<>OLD.id OR NEW.customer_id<>OLD.customer_id OR NEW.booking_id<>OLD.booking_id OR NEW.quote_id<>OLD.quote_id OR NEW.version<>OLD.version+1
 OR (NEW.snapshot - ARRAY['updatedAt','version','cancellationStatus','refundStatus','step','supplierReference','refundReference','failureCode']) IS DISTINCT FROM
    (OLD.snapshot - ARRAY['updatedAt','version','cancellationStatus','refundStatus','step','supplierReference','refundReference','failureCode']) THEN
 RAISE EXCEPTION 'Cancellation input/version invariant' USING ERRCODE='23514'; END IF; RETURN NEW;
END; $$;
CREATE TRIGGER immutable_cancellation_input BEFORE UPDATE OR DELETE ON booking_cancellation FOR EACH ROW EXECUTE FUNCTION protect_cancellation_input();
CREATE TABLE mock_customer_cancellation (
 operation_id UUID PRIMARY KEY,checkout_id UUID NOT NULL UNIQUE,request JSONB NOT NULL,receipt JSONB NOT NULL
);
CREATE TRIGGER immutable_customer_cancellation BEFORE UPDATE OR DELETE ON mock_customer_cancellation FOR EACH ROW EXECUTE FUNCTION reject_aftercare_history();

CREATE TABLE payment_refund (
 operation_id UUID PRIMARY KEY,payment_id UUID NOT NULL REFERENCES payment_account(id),customer_id UUID NOT NULL,entitlement_id UUID NOT NULL,
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0),currency CHAR(3) NOT NULL CHECK(currency='INR'),mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),
 status VARCHAR(12) NOT NULL CHECK(status IN ('PENDING','SUCCEEDED')),version BIGINT NOT NULL DEFAULT 1 CHECK(version>0),
 reference VARCHAR(100) NOT NULL CHECK(reference LIKE 'MOCK-%'),request JSONB NOT NULL,ready_at TIMESTAMPTZ NOT NULL,created_at TIMESTAMPTZ NOT NULL,completed_at TIMESTAMPTZ,
 settlement_attempts INT NOT NULL DEFAULT 0 CHECK(settlement_attempts BETWEEN 0 AND 5),settlement_token UUID,settlement_lease TIMESTAMPTZ,
 CHECK((settlement_token IS NULL)=(settlement_lease IS NULL)),
 UNIQUE(payment_id,entitlement_id),CHECK((status='SUCCEEDED')=(completed_at IS NOT NULL))
);
CREATE INDEX refund_due ON payment_refund(ready_at,operation_id) WHERE status='PENDING';
CREATE TABLE payment_refund_journal (
 id UUID PRIMARY KEY,operation_id UUID NOT NULL REFERENCES payment_refund(operation_id),account VARCHAR(24) NOT NULL CHECK(account IN ('MOCK_CLEARING','BOOKING_PAYABLE')),
 direction VARCHAR(6) NOT NULL CHECK(direction IN ('DEBIT','CREDIT')),amount NUMERIC(18,2) NOT NULL CHECK(amount>0),currency CHAR(3) NOT NULL CHECK(currency='INR'),
 UNIQUE(operation_id,account),CHECK((account='BOOKING_PAYABLE')=(direction='DEBIT'))
);
CREATE TRIGGER immutable_refund_journal BEFORE UPDATE OR DELETE ON payment_refund_journal FOR EACH ROW EXECUTE FUNCTION reject_aftercare_history();
CREATE TABLE payment_refund_outbox (
 id UUID PRIMARY KEY,aggregate_id UUID NOT NULL REFERENCES payment_refund(operation_id),aggregate_version BIGINT NOT NULL,event_type VARCHAR(80) NOT NULL,mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),envelope JSONB NOT NULL,published_at TIMESTAMPTZ,UNIQUE(aggregate_id,aggregate_version)
);
CREATE TRIGGER immutable_refund_event BEFORE UPDATE OR DELETE ON payment_refund_outbox FOR EACH ROW EXECUTE FUNCTION protect_booking_event();
CREATE FUNCTION validate_refund_reservation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE captured payment_account%ROWTYPE; committed NUMERIC;
BEGIN
 SELECT * INTO captured FROM payment_account WHERE id=NEW.payment_id FOR UPDATE;
 SELECT coalesce(sum(amount),0) INTO committed FROM payment_refund WHERE payment_id=NEW.payment_id;
 IF captured.id IS NULL OR captured.status<>'CAPTURED' OR captured.mode<>NEW.mode OR captured.customer_id<>NEW.customer_id OR captured.currency<>NEW.currency OR committed+NEW.amount>captured.amount THEN
 RAISE EXCEPTION 'Refund exceeds captured entitlement or ownership' USING ERRCODE='23514'; END IF; RETURN NEW;
END; $$;
CREATE TRIGGER reserve_refund_amount BEFORE INSERT ON payment_refund FOR EACH ROW EXECUTE FUNCTION validate_refund_reservation();
CREATE FUNCTION protect_refund_input() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Refund history is immutable' USING ERRCODE='23514'; END IF;
 IF (to_jsonb(NEW)-ARRAY['status','version','completed_at','settlement_attempts','settlement_token','settlement_lease']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','version','completed_at','settlement_attempts','settlement_token','settlement_lease']) OR OLD.status<>'PENDING' THEN
 RAISE EXCEPTION 'Refund input/transition invariant' USING ERRCODE='23514'; END IF;
 IF NEW.status='PENDING' AND NEW.version=OLD.version AND NEW.completed_at IS NULL AND NEW.settlement_attempts=OLD.settlement_attempts+1 AND NEW.settlement_token IS NOT NULL AND NEW.settlement_lease IS NOT NULL THEN RETURN NEW; END IF;
 IF NEW.status='SUCCEEDED' AND NEW.version=OLD.version+1 AND NEW.settlement_attempts=OLD.settlement_attempts AND NEW.settlement_token IS NULL AND NEW.settlement_lease IS NULL THEN RETURN NEW; END IF;
 RAISE EXCEPTION 'Refund transition invariant' USING ERRCODE='23514';
END; $$;
CREATE TRIGGER immutable_refund_input BEFORE UPDATE OR DELETE ON payment_refund FOR EACH ROW EXECUTE FUNCTION protect_refund_input();
CREATE FUNCTION require_refund_journal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE refund payment_refund%ROWTYPE; entries INT; debits NUMERIC; credits NUMERIC;
BEGIN
 SELECT * INTO refund FROM payment_refund WHERE operation_id=NEW.operation_id;
 SELECT count(*),sum(amount) FILTER(WHERE direction='DEBIT'),sum(amount) FILTER(WHERE direction='CREDIT') INTO entries,debits,credits FROM payment_refund_journal WHERE operation_id=NEW.operation_id;
 IF (refund.status='SUCCEEDED' AND (entries<>2 OR debits IS DISTINCT FROM refund.amount OR credits IS DISTINCT FROM refund.amount)) OR (refund.status='PENDING' AND entries<>0) THEN
 RAISE EXCEPTION 'Refund journal missing or mismatched' USING ERRCODE='23514'; END IF; RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER refund_journal_required AFTER INSERT OR UPDATE ON payment_refund DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_refund_journal();
CREATE CONSTRAINT TRIGGER refund_journal_balanced AFTER INSERT ON payment_refund_journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_refund_journal();
