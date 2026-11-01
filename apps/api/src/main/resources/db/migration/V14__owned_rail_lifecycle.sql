CREATE TABLE rail_ticket_quote(id UUID PRIMARY KEY,customer_id UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,payload JSONB NOT NULL,CHECK(expires_at>created_at),CHECK((payload->>'id')::uuid=id));
CREATE TABLE rail_ticket_acceptance(quote_id UUID PRIMARY KEY REFERENCES rail_ticket_quote(id),accepted_at TIMESTAMPTZ NOT NULL,version INTEGER NOT NULL CHECK(version=1));
CREATE TABLE rail_ticket_command(id UUID PRIMARY KEY,customer_id UUID NOT NULL,operation VARCHAR(24) NOT NULL,command_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,resource_id UUID NOT NULL,UNIQUE(customer_id,operation,command_key));
CREATE TABLE rail_ticket_booking(id UUID PRIMARY KEY,quote_id UUID NOT NULL UNIQUE REFERENCES rail_ticket_quote(id),customer_id UUID NOT NULL,version BIGINT NOT NULL CHECK(version>0),created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,status VARCHAR(30) NOT NULL,payload JSONB NOT NULL,CHECK((payload->>'id')::uuid=id),CHECK((payload->>'version')::bigint=version),CHECK(payload->>'status'=status));
CREATE INDEX rail_ticket_booking_owner ON rail_ticket_booking(customer_id,created_at DESC,id DESC);
CREATE TABLE rail_ticket_work(booking_id UUID PRIMARY KEY REFERENCES rail_ticket_booking(id),state VARCHAR(12) NOT NULL CHECK(state IN('READY','RUNNING','DONE','REVIEW')),attempts INTEGER NOT NULL DEFAULT 0,next_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),lease_token UUID,lease_until TIMESTAMPTZ);
CREATE INDEX rail_ticket_work_ready ON rail_ticket_work(next_at) WHERE state IN('READY','RUNNING');
CREATE TABLE rail_ticket_cancellation_quote(id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES rail_ticket_booking(id),customer_id UUID NOT NULL,payload JSONB NOT NULL);
CREATE TABLE rail_ticket_voucher(id UUID PRIMARY KEY,booking_id UUID NOT NULL UNIQUE REFERENCES rail_ticket_booking(id),payload JSONB NOT NULL);
CREATE TABLE rail_ticket_outbox(id UUID PRIMARY KEY,aggregate_id UUID NOT NULL,aggregate_version BIGINT NOT NULL,event_type VARCHAR(80) NOT NULL,envelope JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),UNIQUE(aggregate_id,aggregate_version));
CREATE INDEX rail_ticket_outbox_aggregate ON rail_ticket_outbox(aggregate_id,aggregate_version);
CREATE TRIGGER immutable_rail_ticket_quote BEFORE UPDATE OR DELETE ON rail_ticket_quote FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_rail_ticket_acceptance BEFORE UPDATE OR DELETE ON rail_ticket_acceptance FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_rail_ticket_command BEFORE UPDATE OR DELETE ON rail_ticket_command FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_rail_ticket_cancellation_quote BEFORE UPDATE OR DELETE ON rail_ticket_cancellation_quote FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_rail_ticket_voucher BEFORE UPDATE OR DELETE ON rail_ticket_voucher FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_rail_ticket_outbox BEFORE UPDATE OR DELETE ON rail_ticket_outbox FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE FUNCTION guard_rail_ticket_booking() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Rail booking history is immutable'; END IF;
 IF NEW.id<>OLD.id OR NEW.customer_id<>OLD.customer_id OR NEW.quote_id<>OLD.quote_id OR NEW.created_at<>OLD.created_at OR NEW.version<>OLD.version+1
 OR (NEW.payload-'version'-'updatedAt'-'status'-'payment'-'reservation'-'refund'-'step'-'supplierReference'-'paymentReference'-'refundReference'-'failureCode'-'cancellation') IS DISTINCT FROM (OLD.payload-'version'-'updatedAt'-'status'-'payment'-'reservation'-'refund'-'step'-'supplierReference'-'paymentReference'-'refundReference'-'failureCode'-'cancellation')
 OR (OLD.payload->'cancellation'<>'null'::jsonb AND NEW.payload->'cancellation' IS DISTINCT FROM OLD.payload->'cancellation') THEN RAISE EXCEPTION 'Rail booking input/version guard'; END IF; RETURN NEW; END $$;
CREATE TRIGGER protect_rail_ticket_booking BEFORE UPDATE OR DELETE ON rail_ticket_booking FOR EACH ROW EXECUTE FUNCTION guard_rail_ticket_booking();

-- V12 was already exercised locally; add guards without editing applied migrations.
ALTER TABLE rail_ticket_quote ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK');
ALTER TABLE rail_ticket_booking ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK');
ALTER TABLE rail_ticket_quote ADD CONSTRAINT rail_ticket_quote_owner_binding CHECK((payload->>'owner')::uuid=customer_id AND (payload->>'version')::integer=1);
ALTER TABLE rail_ticket_booking ADD CONSTRAINT rail_ticket_booking_owner_binding CHECK((payload->'quote'->>'owner')::uuid=customer_id AND (payload->'quote'->>'id')::uuid=quote_id);
ALTER TABLE rail_ticket_booking ADD CONSTRAINT rail_ticket_booking_status CHECK(status IN('PROCESSING','ISSUED','FAILED','CANCELLATION_PENDING','CANCELLED','REVIEW'));
ALTER TABLE rail_ticket_booking ADD CONSTRAINT rail_ticket_booking_confirmed_effects CHECK(status<>'ISSUED' OR (payload->>'payment'='CAPTURED' AND payload->>'reservation' IN('CONFIRMED','RAC','WAITLIST')));
ALTER TABLE rail_ticket_booking ADD CONSTRAINT rail_ticket_booking_cancelled_reservation CHECK(status<>'CANCELLED' OR payload->>'reservation'='CANCELLED');
ALTER TABLE rail_ticket_cancellation_quote ADD CONSTRAINT rail_ticket_cancellation_owner_binding CHECK((payload->>'id')::uuid=id AND (payload->>'bookingId')::uuid=booking_id AND (payload->>'owner')::uuid=customer_id);
ALTER TABLE rail_ticket_voucher ADD CONSTRAINT rail_ticket_voucher_binding CHECK((payload->>'id')::uuid=id AND (payload->>'bookingId')::uuid=booking_id AND (payload->>'byteLength')::integer BETWEEN 1 AND 32768);
ALTER TABLE rail_ticket_outbox ADD CONSTRAINT rail_ticket_event_binding CHECK((envelope->>'eventId')::uuid=id AND (envelope->>'aggregateId')::uuid=aggregate_id AND (envelope->>'aggregateVersion')::bigint=aggregate_version AND envelope->>'eventType'=event_type AND envelope->>'mode'='MOCK' AND envelope->>'producer'='railtickets' AND (envelope->>'schemaVersion')::integer=1);
ALTER TABLE rail_ticket_outbox ADD CONSTRAINT rail_ticket_event_no_guest_payload CHECK(envelope->'payload' ?& ARRAY['status','payment','reservation','refund'] AND ((envelope->'payload')-'status'-'payment'-'reservation'-'refund')='{}'::jsonb);

CREATE INDEX rail_ticket_pnr_owner ON rail_ticket_booking(customer_id, (payload->>'supplierReference'));
CREATE TABLE rail_provider_operation(operation_id UUID PRIMARY KEY,reservation_id UUID NOT NULL,operation VARCHAR(12) NOT NULL CHECK(operation IN('RESERVE','CANCEL')),fingerprint VARCHAR(200) NOT NULL,receipt JSONB NOT NULL,UNIQUE(reservation_id,operation),CHECK((receipt->>'operationId')::uuid=operation_id AND (receipt->>'reservationId')::uuid=reservation_id AND receipt->>'mode'='MOCK'));
CREATE TABLE rail_provider_reservation(id UUID PRIMARY KEY,stock_key VARCHAR(300) NOT NULL,status VARCHAR(16) NOT NULL CHECK(status IN('CONFIRMED','RAC','WAITLIST','REJECTED','CANCELLED')),reference VARCHAR(100) NOT NULL UNIQUE CHECK(reference LIKE 'MOCK-PNR-%'),quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 6),capacity INTEGER NOT NULL CHECK(capacity>0),active BOOLEAN NOT NULL,CHECK(active=(status IN('CONFIRMED','RAC','WAITLIST'))));
CREATE INDEX rail_provider_stock ON rail_provider_reservation(stock_key) WHERE active;
CREATE TRIGGER immutable_rail_provider_operation BEFORE UPDATE OR DELETE ON rail_provider_operation FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
