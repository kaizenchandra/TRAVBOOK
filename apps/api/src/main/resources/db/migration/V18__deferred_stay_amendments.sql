-- Original quotes, financial receipts and voucher bytes are preserved.
CREATE TABLE stay_quote_use(quote_id UUID PRIMARY KEY REFERENCES stay_quote(id),booking_id UUID NOT NULL);
INSERT INTO stay_quote_use SELECT quote_id,id FROM stay_booking;
ALTER TABLE stay_quote_use ADD CONSTRAINT stay_quote_use_booking FOREIGN KEY(booking_id) REFERENCES stay_booking(id) DEFERRABLE INITIALLY DEFERRED;
CREATE TRIGGER immutable_stay_quote_use BEFORE UPDATE OR DELETE ON stay_quote_use FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
ALTER TABLE stay_voucher ADD COLUMN revision UUID;
-- Existing immutable payload trigger permits neither changes nor deletion; temporarily disable only for the additive metadata backfill.
ALTER TABLE stay_voucher DISABLE TRIGGER immutable_stay_voucher;
UPDATE stay_voucher v SET revision=b.quote_id FROM stay_booking b WHERE b.id=v.booking_id;
ALTER TABLE stay_voucher ENABLE TRIGGER immutable_stay_voucher;
ALTER TABLE stay_voucher ALTER COLUMN revision SET NOT NULL;
ALTER TABLE stay_voucher ADD CONSTRAINT stay_voucher_revision_fk FOREIGN KEY(revision) REFERENCES stay_quote(id);
ALTER TABLE stay_voucher DROP CONSTRAINT stay_voucher_booking_id_key;
ALTER TABLE stay_voucher ADD CONSTRAINT stay_voucher_revision_unique UNIQUE(booking_id,revision);
ALTER TABLE hotel_provider_operation DROP CONSTRAINT hotel_provider_operation_reservation_id_operation_key;
CREATE UNIQUE INDEX hotel_provider_single_initial_effect ON hotel_provider_operation(reservation_id,operation) WHERE operation IN('RESERVE','CANCEL');
ALTER TABLE stay_booking DROP CONSTRAINT stay_booking_confirmed_effects;
ALTER TABLE stay_booking ADD CONSTRAINT stay_booking_confirmed_effects CHECK(status<>'CONFIRMED' OR (payload->>'reservation'='CONFIRMED' AND (payload->>'payment'='CAPTURED' AND NOT jsonb_path_exists(payload,'$.quote.selection.offers[*] ? (@.paymentCondition != "MOCK_PAY_NOW")') OR payload->>'payment'='DEFERRED' AND jsonb_array_length(payload->'quote'->'selection'->'offers')>0 AND NOT jsonb_path_exists(payload,'$.quote.selection.offers[*] ? (@.paymentCondition != "MOCK_PAY_AT_PROPERTY" || @.planCode != "FLEX")'))));
CREATE OR REPLACE FUNCTION guard_stay_booking() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Hotel booking history is immutable'; END IF;
 IF NEW.id<>OLD.id OR NEW.customer_id<>OLD.customer_id OR NEW.quote_id<>OLD.quote_id OR NEW.created_at<>OLD.created_at OR NEW.version<>OLD.version+1
 OR (NEW.payload-'amendments'-'version'-'updatedAt'-'status'-'payment'-'reservation'-'refund'-'step'-'supplierReference'-'paymentReference'-'refundReference'-'failureCode'-'cancellation') IS DISTINCT FROM (OLD.payload-'amendments'-'version'-'updatedAt'-'status'-'payment'-'reservation'-'refund'-'step'-'supplierReference'-'paymentReference'-'refundReference'-'failureCode'-'cancellation')
 OR (OLD.payload->'cancellation'<>'null'::jsonb AND NEW.payload->'cancellation' IS DISTINCT FROM OLD.payload->'cancellation') THEN RAISE EXCEPTION 'Hotel booking input/version guard'; END IF;
 -- Closed amendment history cannot be rewritten; only append a pending request or close the last pending entry.
 IF jsonb_array_length(COALESCE(NEW.payload->'amendments','[]'))>10 OR EXISTS(SELECT 1 FROM jsonb_array_elements(COALESCE(OLD.payload->'amendments','[]')) WITH ORDINALITY AS a(value,n) WHERE (a.value->>'state'<>'PENDING' AND NEW.payload->'amendments'->(a.n::int-1) IS DISTINCT FROM a.value) OR (a.value->>'state'='PENDING' AND ((NEW.payload->'amendments'->(a.n::int-1))-'state') IS DISTINCT FROM (a.value-'state'))) THEN RAISE EXCEPTION 'Hotel amendment history guard' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
