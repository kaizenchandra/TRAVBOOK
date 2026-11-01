-- Additive capability terms; no legacy quote/booking payload is rewritten.
CREATE TABLE journey_terms(quote_id UUID PRIMARY KEY REFERENCES journey_quote(id),payload JSONB NOT NULL,CHECK((payload->>'quoteId')::uuid=quote_id));
ALTER TABLE journey_booking ADD CONSTRAINT journey_booking_owner_identity UNIQUE(id,customer_id);
CREATE TABLE journey_balance(booking_id UUID PRIMARY KEY REFERENCES journey_booking(id),state TEXT NOT NULL CHECK(state IN('DUE','RUNNING','PAID','CANCELLED','OVERDUE','REVIEW')),amount NUMERIC(14,2) NOT NULL CHECK(amount>0),due_at TIMESTAMPTZ NOT NULL,payment_id UUID,action_id UUID, CHECK((state='PAID')=(payment_id IS NOT NULL)));
CREATE INDEX journey_balance_due ON journey_balance(due_at) WHERE state IN('DUE','OVERDUE');
CREATE TABLE journey_care_quote(id UUID PRIMARY KEY,booking_id UUID NOT NULL,customer_id UUID NOT NULL,payload JSONB NOT NULL,FOREIGN KEY(booking_id,customer_id) REFERENCES journey_booking(id,customer_id),CHECK((payload->>'id')::uuid=id AND (payload->>'bookingId')::uuid=booking_id));
CREATE TABLE journey_care_action(id UUID PRIMARY KEY,booking_id UUID NOT NULL,customer_id UUID NOT NULL,type TEXT NOT NULL CHECK(type IN('CANCEL','BALANCE','RESCHEDULE')),status TEXT NOT NULL CHECK(status IN('PENDING','WAITING','SUCCEEDED','FAILED','REVIEW')),version BIGINT NOT NULL CHECK(version>0),created_at TIMESTAMPTZ NOT NULL,payload JSONB NOT NULL,FOREIGN KEY(booking_id,customer_id) REFERENCES journey_booking(id,customer_id),CHECK((payload->>'id')::uuid=id AND (payload->'booking'->>'id')::uuid=booking_id AND (payload->'booking'->'quote'->>'owner')::uuid=customer_id AND (payload->>'version')::bigint=version AND payload->>'status'=status AND payload->>'type'=type));
CREATE UNIQUE INDEX journey_one_active_care ON journey_care_action(booking_id) WHERE status IN('PENDING','WAITING','REVIEW');
CREATE INDEX journey_care_by_booking ON journey_care_action(booking_id,created_at,id);
CREATE TABLE journey_care_work(action_id UUID PRIMARY KEY REFERENCES journey_care_action(id),state TEXT NOT NULL CHECK(state IN('READY','RUNNING','DONE','REVIEW')),attempts INT NOT NULL DEFAULT 0,next_at TIMESTAMPTZ NOT NULL,lease_token UUID,lease_until TIMESTAMPTZ);
CREATE INDEX journey_care_due ON journey_care_work(next_at) WHERE state IN('READY','RUNNING');
CREATE TABLE journey_component_cancelled(booking_id UUID NOT NULL REFERENCES journey_booking(id),component_id TEXT NOT NULL,action_id UUID NOT NULL REFERENCES journey_care_action(id),PRIMARY KEY(booking_id,component_id));
CREATE TABLE journey_document_revision(id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES journey_booking(id),booking_version BIGINT NOT NULL,payload JSONB NOT NULL,UNIQUE(booking_id,booking_version));
CREATE TABLE journey_replacement_quote(quote_id UUID PRIMARY KEY,booking_id UUID NOT NULL,customer_id UUID NOT NULL,booking_version BIGINT NOT NULL,FOREIGN KEY(quote_id,customer_id) REFERENCES journey_quote(id,customer_id),FOREIGN KEY(booking_id,customer_id) REFERENCES journey_booking(id,customer_id));
CREATE TABLE journey_replacement(booking_id UUID NOT NULL,replacement_id UUID PRIMARY KEY,customer_id UUID NOT NULL,action_id UUID NOT NULL UNIQUE REFERENCES journey_care_action(id),FOREIGN KEY(booking_id,customer_id) REFERENCES journey_booking(id,customer_id),FOREIGN KEY(replacement_id,customer_id) REFERENCES journey_booking(id,customer_id));
CREATE TABLE journey_care_review(action_id UUID NOT NULL REFERENCES journey_care_action(id),review_version BIGINT NOT NULL,booking_payload JSONB NOT NULL,action_payload JSONB NOT NULL,PRIMARY KEY(action_id,review_version));
CREATE TABLE journey_care_recovery(id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES journey_booking(id),action_id UUID NOT NULL REFERENCES journey_care_action(id),actor VARCHAR(64) NOT NULL,command_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(200) NOT NULL,payload JSONB NOT NULL,UNIQUE(actor,command_key));
CREATE TABLE journey_care_outbox(id UUID PRIMARY KEY,aggregate_id UUID NOT NULL,aggregate_version BIGINT NOT NULL,envelope JSONB NOT NULL,UNIQUE(aggregate_id,aggregate_version));
-- Provider-owned side-effect ledger and simulated cab telemetry, separate from platform work.
CREATE TABLE journey_supplier_care_receipt(operation_id UUID PRIMARY KEY,booking_id UUID NOT NULL,payload JSONB NOT NULL);
CREATE TABLE journey_supplier_dispatch(booking_id UUID PRIMARY KEY REFERENCES journey_supplier_reservation(booking_id),version BIGINT NOT NULL,payload JSONB NOT NULL);
CREATE TABLE journey_dispatch_command(customer_id UUID NOT NULL REFERENCES customer_account(id),command_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(100) NOT NULL,payload JSONB NOT NULL,PRIMARY KEY(customer_id,command_key));
CREATE TRIGGER immutable_journey_terms BEFORE UPDATE OR DELETE ON journey_terms FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_care_quote BEFORE UPDATE OR DELETE ON journey_care_quote FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_component_cancelled BEFORE UPDATE OR DELETE ON journey_component_cancelled FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_revision BEFORE UPDATE OR DELETE ON journey_document_revision FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_replacement_quote BEFORE UPDATE OR DELETE ON journey_replacement_quote FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_replacement BEFORE UPDATE OR DELETE ON journey_replacement FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_care_review BEFORE UPDATE OR DELETE ON journey_care_review FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_care_recovery BEFORE UPDATE OR DELETE ON journey_care_recovery FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_care_outbox BEFORE UPDATE OR DELETE ON journey_care_outbox FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_supplier_care BEFORE UPDATE OR DELETE ON journey_supplier_care_receipt FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_dispatch_command BEFORE UPDATE OR DELETE ON journey_dispatch_command FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();

CREATE FUNCTION preserve_journey_care_inputs() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.payload->'booking' IS DISTINCT FROM OLD.payload->'booking' OR NEW.payload->'componentIds' IS DISTINCT FROM OLD.payload->'componentIds' OR NEW.payload->'amount' IS DISTINCT FROM OLD.payload->'amount' OR NEW.payload->'deadline' IS DISTINCT FROM OLD.payload->'deadline' OR NEW.payload->'replacementId' IS DISTINCT FROM OLD.payload->'replacementId' OR NEW.type IS DISTINCT FROM OLD.type OR NEW.customer_id IS DISTINCT FROM OLD.customer_id OR NEW.booking_id IS DISTINCT FROM OLD.booking_id THEN RAISE EXCEPTION 'Accepted care inputs are immutable'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER immutable_journey_care_inputs BEFORE UPDATE ON journey_care_action FOR EACH ROW EXECUTE FUNCTION preserve_journey_care_inputs();
CREATE FUNCTION preserve_journey_balance_terms() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.amount IS DISTINCT FROM OLD.amount OR NEW.due_at IS DISTINCT FROM OLD.due_at OR NEW.booking_id IS DISTINCT FROM OLD.booking_id THEN RAISE EXCEPTION 'Milestone terms are immutable'; END IF; RETURN NEW; END $$;
CREATE TRIGGER immutable_journey_balance_terms BEFORE UPDATE ON journey_balance FOR EACH ROW EXECUTE FUNCTION preserve_journey_balance_terms();
CREATE TABLE journey_care_quote_use(action_id UUID PRIMARY KEY REFERENCES journey_care_action(id),quote_id UUID NOT NULL UNIQUE REFERENCES journey_care_quote(id));
CREATE TRIGGER immutable_journey_care_quote_use BEFORE UPDATE OR DELETE ON journey_care_quote_use FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE FUNCTION preserve_journey_refund_entitlements() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF jsonb_path_query_array(NEW.payload,'$.refunds[*].paymentId') IS DISTINCT FROM jsonb_path_query_array(OLD.payload,'$.refunds[*].paymentId') OR jsonb_path_query_array(NEW.payload,'$.refunds[*].amount') IS DISTINCT FROM jsonb_path_query_array(OLD.payload,'$.refunds[*].amount') THEN RAISE EXCEPTION 'Refund entitlements are immutable'; END IF; RETURN NEW; END $$;
CREATE TRIGGER immutable_journey_refund_entitlements BEFORE UPDATE ON journey_care_action FOR EACH ROW EXECUTE FUNCTION preserve_journey_refund_entitlements();
