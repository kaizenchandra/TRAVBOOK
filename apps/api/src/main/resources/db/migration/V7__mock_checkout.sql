CREATE TABLE booking_checkout (
 id UUID PRIMARY KEY, booking_id UUID NOT NULL UNIQUE REFERENCES booking_intent(id), customer_id UUID NOT NULL,
 scenario VARCHAR(40) NOT NULL, snapshot JSONB NOT NULL, version BIGINT NOT NULL CHECK(version>0),
 status VARCHAR(16) NOT NULL CHECK(status IN ('RUNNING','CONFIRMED','FAILED','REVIEW')),
 step VARCHAR(20) NOT NULL CHECK(step IN ('REVALIDATE','AUTHORIZE','RESERVE','CAPTURE','CANCEL','VOID','DONE')),
 payment_status VARCHAR(20) NOT NULL CHECK(payment_status IN ('NOT_STARTED','AUTHORIZED','CAPTURED','DECLINED','VOIDED','UNKNOWN')),
 reservation_status VARCHAR(20) NOT NULL CHECK(reservation_status IN ('NOT_REQUESTED','CONFIRMED','REJECTED','CANCELLED','UNKNOWN')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CHECK(status<>'CONFIRMED' OR (payment_status='CAPTURED' AND reservation_status='CONFIRMED' AND step='DONE')),
 CHECK(snapshot->>'status'=status AND snapshot->>'step'=step AND snapshot->>'payment'=payment_status AND snapshot->>'reservation'=reservation_status),
 CHECK(snapshot->'quote'->>'mode'='MOCK'),
 CHECK((snapshot->>'id')::uuid=id AND (snapshot->>'bookingId')::uuid=booking_id AND (snapshot->'quote'->>'customerId')::uuid=customer_id AND snapshot->>'scenario'=scenario AND (snapshot->>'version')::bigint=version)
);
CREATE TABLE booking_checkout_command (
 customer_id UUID NOT NULL, command_key VARCHAR(100) NOT NULL CHECK(command_key ~ '^[A-Za-z0-9_-]{16,100}$'),
 fingerprint VARCHAR(100) NOT NULL, checkout_id UUID NOT NULL REFERENCES booking_checkout(id), PRIMARY KEY(customer_id,command_key)
);
CREATE TRIGGER immutable_checkout_command BEFORE UPDATE OR DELETE ON booking_checkout_command FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TABLE booking_checkout_work (
 checkout_id UUID PRIMARY KEY REFERENCES booking_checkout(id), definition_version INT NOT NULL DEFAULT 1 CHECK(definition_version=1),
 state VARCHAR(12) NOT NULL CHECK(state IN ('READY','RUNNING','RETRY','DONE','REVIEW')), attempts INT NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 6),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), lease_token UUID,lease_until TIMESTAMPTZ,
 CHECK((state='RUNNING')=(lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX checkout_work_due ON booking_checkout_work(next_attempt_at) WHERE state IN ('READY','RETRY');
CREATE INDEX checkout_work_abandoned ON booking_checkout_work(lease_until) WHERE state='RUNNING';
CREATE TABLE booking_checkout_outbox (
 id UUID PRIMARY KEY,aggregate_id UUID NOT NULL REFERENCES booking_checkout(id),aggregate_version BIGINT NOT NULL,
 event_type VARCHAR(80) NOT NULL,mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),envelope JSONB NOT NULL,published_at TIMESTAMPTZ,
 UNIQUE(aggregate_id,aggregate_version)
);
CREATE TRIGGER immutable_checkout_event BEFORE UPDATE OR DELETE ON booking_checkout_outbox FOR EACH ROW EXECUTE FUNCTION protect_booking_event();
CREATE FUNCTION protect_checkout_input() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Checkout history is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.id<>OLD.id OR NEW.booking_id<>OLD.booking_id OR NEW.customer_id<>OLD.customer_id OR NEW.scenario<>OLD.scenario OR NEW.created_at<>OLD.created_at OR NEW.version<>OLD.version+1
 OR (NEW.snapshot - ARRAY['updatedAt','version','status','step','payment','reservation','paymentReference','supplierReference','failureCode']) IS DISTINCT FROM
    (OLD.snapshot - ARRAY['updatedAt','version','status','step','payment','reservation','paymentReference','supplierReference','failureCode']) THEN
 RAISE EXCEPTION 'Checkout immutable input/version violation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_checkout_input BEFORE UPDATE OR DELETE ON booking_checkout FOR EACH ROW EXECUTE FUNCTION protect_checkout_input();

-- Durable deterministic provider fixtures. No actual inventory or money is represented.
CREATE TABLE mock_supplier_reservation (
 checkout_id UUID PRIMARY KEY, reference VARCHAR(100) NOT NULL UNIQUE CHECK(reference LIKE 'MOCK-%'),
 status VARCHAR(16) NOT NULL CHECK(status IN ('CONFIRMED','REJECTED','CANCELLED')), input JSONB NOT NULL
);
CREATE TABLE mock_supplier_operation (
 operation_id UUID PRIMARY KEY,checkout_id UUID NOT NULL,operation VARCHAR(12) NOT NULL CHECK(operation IN ('RESERVE','CANCEL')),
 request JSONB NOT NULL,receipt JSONB NOT NULL,UNIQUE(checkout_id,operation)
);
CREATE TRIGGER immutable_mock_supplier_receipt BEFORE UPDATE OR DELETE ON mock_supplier_operation FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TABLE payment_account (
 id UUID PRIMARY KEY,customer_id UUID NOT NULL,amount NUMERIC(18,2) NOT NULL CHECK(amount>0),currency CHAR(3) NOT NULL CHECK(currency='INR'),
 mode VARCHAR(10) NOT NULL CHECK(mode='MOCK'),status VARCHAR(16) NOT NULL CHECK(status IN ('AUTHORIZED','CAPTURED','DECLINED','VOIDED')),
 valid_until TIMESTAMPTZ NOT NULL,reference VARCHAR(100) NOT NULL CHECK(reference LIKE 'MOCK-%'),version BIGINT NOT NULL DEFAULT 1 CHECK(version>0)
);
CREATE TABLE payment_operation (
 operation_id UUID PRIMARY KEY,payment_id UUID NOT NULL REFERENCES payment_account(id),operation VARCHAR(12) NOT NULL CHECK(operation IN ('AUTHORIZE','CAPTURE','VOID')),
 request JSONB NOT NULL,receipt JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),UNIQUE(payment_id,operation)
);
CREATE TRIGGER immutable_payment_operation BEFORE UPDATE OR DELETE ON payment_operation FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TABLE payment_journal (
 id UUID PRIMARY KEY,operation_id UUID NOT NULL REFERENCES payment_operation(operation_id),payment_id UUID NOT NULL REFERENCES payment_account(id),
 account VARCHAR(24) NOT NULL CHECK(account IN ('MOCK_CLEARING','BOOKING_PAYABLE')),direction VARCHAR(6) NOT NULL CHECK(direction IN ('DEBIT','CREDIT')),
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0),currency CHAR(3) NOT NULL CHECK(currency='INR'),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(payment_id,account),CHECK((account='MOCK_CLEARING')=(direction='DEBIT'))
);
CREATE TRIGGER immutable_payment_journal BEFORE UPDATE OR DELETE ON payment_journal FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE FUNCTION balanced_mock_capture() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE net NUMERIC; entries INT;
BEGIN
 SELECT sum(CASE WHEN direction='DEBIT' THEN amount ELSE -amount END),count(*) INTO net,entries FROM payment_journal WHERE operation_id=NEW.operation_id;
 IF net<>0 OR entries<>2 THEN RAISE EXCEPTION 'Unbalanced mock capture' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER balanced_capture AFTER INSERT ON payment_journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION balanced_mock_capture();
CREATE FUNCTION protect_payment_account() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payment history is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.id<>OLD.id OR NEW.customer_id<>OLD.customer_id OR NEW.amount<>OLD.amount OR NEW.currency<>OLD.currency OR NEW.mode<>OLD.mode OR NEW.valid_until<>OLD.valid_until OR NEW.reference<>OLD.reference
 OR NEW.version<>OLD.version+1 OR OLD.status<>'AUTHORIZED' OR NEW.status NOT IN ('CAPTURED','VOIDED') THEN
 RAISE EXCEPTION 'Payment transition/input invariant' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_payment_input BEFORE UPDATE OR DELETE ON payment_account FOR EACH ROW EXECUTE FUNCTION protect_payment_account();
CREATE FUNCTION captured_payment_has_journal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE entries INT; debits NUMERIC; credits NUMERIC;
BEGIN
 IF NEW.status='CAPTURED' THEN
 SELECT count(*),sum(amount) FILTER(WHERE direction='DEBIT'),sum(amount) FILTER(WHERE direction='CREDIT') INTO entries,debits,credits FROM payment_journal WHERE payment_id=NEW.id;
 IF entries<>2 OR debits IS DISTINCT FROM NEW.amount OR credits IS DISTINCT FROM NEW.amount THEN RAISE EXCEPTION 'Capture journal missing or mismatched' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER capture_journal_required AFTER INSERT OR UPDATE ON payment_account DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION captured_payment_has_journal();
