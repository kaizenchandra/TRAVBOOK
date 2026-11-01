CREATE TABLE stay_quote(id UUID PRIMARY KEY,customer_id UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,payload JSONB NOT NULL,CHECK(expires_at>created_at),CHECK((payload->>'id')::uuid=id));
CREATE TABLE stay_acceptance(quote_id UUID PRIMARY KEY REFERENCES stay_quote(id),accepted_at TIMESTAMPTZ NOT NULL,version INTEGER NOT NULL CHECK(version=1));
CREATE TABLE stay_command(id UUID PRIMARY KEY,customer_id UUID NOT NULL,operation VARCHAR(24) NOT NULL,command_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,resource_id UUID NOT NULL,UNIQUE(customer_id,operation,command_key));
CREATE TABLE stay_booking(id UUID PRIMARY KEY,quote_id UUID NOT NULL UNIQUE REFERENCES stay_quote(id),customer_id UUID NOT NULL,version BIGINT NOT NULL CHECK(version>0),created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,status VARCHAR(30) NOT NULL,payload JSONB NOT NULL,CHECK((payload->>'id')::uuid=id),CHECK((payload->>'version')::bigint=version),CHECK(payload->>'status'=status));
CREATE INDEX stay_booking_owner ON stay_booking(customer_id,created_at DESC,id DESC);
CREATE TABLE stay_work(booking_id UUID PRIMARY KEY REFERENCES stay_booking(id),state VARCHAR(12) NOT NULL CHECK(state IN('READY','RUNNING','DONE','REVIEW')),attempts INTEGER NOT NULL DEFAULT 0,next_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),lease_token UUID,lease_until TIMESTAMPTZ);
CREATE INDEX stay_work_ready ON stay_work(next_at) WHERE state IN('READY','RUNNING');
CREATE TABLE stay_cancellation_quote(id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES stay_booking(id),customer_id UUID NOT NULL,payload JSONB NOT NULL);
CREATE TABLE stay_voucher(id UUID PRIMARY KEY,booking_id UUID NOT NULL UNIQUE REFERENCES stay_booking(id),payload JSONB NOT NULL);
CREATE TABLE stay_outbox(id UUID PRIMARY KEY,aggregate_id UUID NOT NULL,aggregate_version BIGINT NOT NULL,event_type VARCHAR(80) NOT NULL,envelope JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),UNIQUE(aggregate_id,aggregate_version));
CREATE INDEX stay_outbox_aggregate ON stay_outbox(aggregate_id,aggregate_version);
CREATE TRIGGER immutable_stay_quote BEFORE UPDATE OR DELETE ON stay_quote FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_stay_acceptance BEFORE UPDATE OR DELETE ON stay_acceptance FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_stay_command BEFORE UPDATE OR DELETE ON stay_command FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_stay_cancellation_quote BEFORE UPDATE OR DELETE ON stay_cancellation_quote FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_stay_voucher BEFORE UPDATE OR DELETE ON stay_voucher FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_stay_outbox BEFORE UPDATE OR DELETE ON stay_outbox FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE FUNCTION guard_stay_booking() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Hotel booking history is immutable'; END IF;
 IF NEW.id<>OLD.id OR NEW.customer_id<>OLD.customer_id OR NEW.quote_id<>OLD.quote_id OR NEW.created_at<>OLD.created_at OR NEW.version<>OLD.version+1
 OR (NEW.payload-'version'-'updatedAt'-'status'-'payment'-'reservation'-'refund'-'step'-'supplierReference'-'paymentReference'-'refundReference'-'failureCode'-'cancellation') IS DISTINCT FROM (OLD.payload-'version'-'updatedAt'-'status'-'payment'-'reservation'-'refund'-'step'-'supplierReference'-'paymentReference'-'refundReference'-'failureCode'-'cancellation')
 OR (OLD.payload->'cancellation'<>'null'::jsonb AND NEW.payload->'cancellation' IS DISTINCT FROM OLD.payload->'cancellation') THEN RAISE EXCEPTION 'Hotel booking input/version guard'; END IF; RETURN NEW; END $$;
CREATE TRIGGER protect_stay_booking BEFORE UPDATE OR DELETE ON stay_booking FOR EACH ROW EXECUTE FUNCTION guard_stay_booking();

-- Deterministic provider-owned receipts and nightly allocations, not platform-authoritative supplier stock in LIVE.
CREATE TABLE hotel_provider_operation(operation_id UUID PRIMARY KEY,reservation_id UUID NOT NULL,operation VARCHAR(12) NOT NULL,request JSONB NOT NULL,receipt JSONB NOT NULL,UNIQUE(reservation_id,operation));
CREATE TABLE hotel_provider_reservation(id UUID PRIMARY KEY,property_id VARCHAR(80) NOT NULL,status VARCHAR(16) NOT NULL CHECK(status IN('CONFIRMED','REJECTED','CANCELLED')),reference VARCHAR(100) NOT NULL UNIQUE,payload JSONB NOT NULL);
CREATE TABLE hotel_room_allocation(reservation_id UUID NOT NULL REFERENCES hotel_provider_reservation(id),property_id VARCHAR(80) NOT NULL,night DATE NOT NULL,room_group VARCHAR(16) NOT NULL,quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 4),active BOOLEAN NOT NULL,PRIMARY KEY(reservation_id,night,room_group));
CREATE INDEX hotel_room_stock ON hotel_room_allocation(property_id,night,room_group) WHERE active;
CREATE TRIGGER immutable_hotel_provider_operation BEFORE UPDATE OR DELETE ON hotel_provider_operation FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
