-- Isolated test-double state. No existing booking, quote, financial record or supplier mode is rewritten.
CREATE TABLE fp_quote (
 id UUID PRIMARY KEY,owner_id UUID NOT NULL REFERENCES customer_account(id),payload JSONB NOT NULL,
 mode TEXT NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK'),
 CHECK((payload->>'id')::uuid=id AND (payload->>'owner')::uuid=owner_id AND (payload->>'version')::int=1)
);
CREATE TABLE fp_flight (id TEXT PRIMARY KEY CHECK(id ~ '^MOCK-FA-[0-9]{4}-[0-9]{2}-[0-9]{2}$'));
CREATE TABLE fp_reservation (
 id UUID PRIMARY KEY,owner_id UUID NOT NULL REFERENCES customer_account(id),quote_id UUID NOT NULL UNIQUE REFERENCES fp_quote(id),
 version BIGINT NOT NULL CHECK(version>0),payload JSONB NOT NULL,mode TEXT NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK'),
 CHECK((payload->>'id')::uuid=id AND (payload->>'owner')::uuid=owner_id AND (payload->>'version')::bigint=version AND (payload->'quote'->>'id')::uuid=quote_id AND (payload->'quote'->>'owner')::uuid=owner_id),
 CHECK(payload->>'reservation' IN ('RESERVED','CANCELLED')),
 CHECK(payload->>'payment' IN ('UNPAID','DECLINED','CAPTURED','REFUNDED')),
 CHECK(payload->>'ticket' IN ('NOT_ISSUED','ISSUED','REJECTED','VOID')),
 CHECK(payload->>'refund' IN ('NONE','PENDING','COMPLETED')),
 CHECK((payload->>'paymentAttempts')::int BETWEEN 0 AND 10),
 CHECK(payload->>'ticket'<>'ISSUED' OR (payload->>'payment'='CAPTURED' AND payload->>'reservation'='RESERVED')),
 CHECK(payload->>'payment'<>'REFUNDED' OR (payload->>'reservation'='CANCELLED' AND payload->>'refund'='COMPLETED'))
);
CREATE INDEX fp_reservation_owner ON fp_reservation(owner_id,id);
CREATE TABLE fp_seat (
 flight_id TEXT NOT NULL REFERENCES fp_flight(id),seat TEXT NOT NULL CHECK(seat ~ '^[1-3][A-F]$' AND seat<>'3F'),
 reservation_id UUID NOT NULL REFERENCES fp_reservation(id),passenger_id TEXT NOT NULL CHECK(passenger_id ~ '^P[1-6]$'),
 PRIMARY KEY(flight_id,seat),UNIQUE(reservation_id,passenger_id)
);
CREATE TABLE fp_command (
 owner_id UUID NOT NULL REFERENCES customer_account(id),operation TEXT NOT NULL,command_key VARCHAR(128) NOT NULL,
 fingerprint CHAR(64) NOT NULL,aggregate_id UUID NOT NULL,response JSONB NOT NULL,
 PRIMARY KEY(owner_id,operation,command_key),
 CHECK(operation IN ('QUOTE','RESERVE','PAYMENT','TICKET','CHECK_IN','CANCEL','REFUND')),
 CHECK((response->>'owner')::uuid=owner_id AND (response->>'id')::uuid=aggregate_id)
);
CREATE TABLE fp_money_effect (
 id UUID PRIMARY KEY,reservation_id UUID NOT NULL REFERENCES fp_reservation(id),kind TEXT NOT NULL CHECK(kind IN ('CAPTURE','REFUND')),
 amount NUMERIC(14,2) NOT NULL CHECK(amount>0),currency TEXT NOT NULL DEFAULT 'INR' CHECK(currency='INR'),
 reference TEXT NOT NULL UNIQUE CHECK(reference LIKE 'MOCK-%'),occurred_at TIMESTAMPTZ NOT NULL,
 mode TEXT NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK'),UNIQUE(reservation_id,kind)
);
CREATE TABLE fp_journal (
 effect_id UUID NOT NULL REFERENCES fp_money_effect(id),line INT NOT NULL CHECK(line IN (1,2)),
 account TEXT NOT NULL CHECK(account IN ('SIMULATED_CASH','SIMULATED_CUSTOMER_LIABILITY')),
 side TEXT NOT NULL CHECK(side IN ('DEBIT','CREDIT')),amount NUMERIC(14,2) NOT NULL CHECK(amount>0),PRIMARY KEY(effect_id,line),UNIQUE(effect_id,side)
);
CREATE TABLE fp_event (
 id UUID PRIMARY KEY,aggregate_id UUID NOT NULL,version BIGINT NOT NULL,envelope JSONB NOT NULL,UNIQUE(aggregate_id,version),
 CHECK((envelope->>'eventId')::uuid=id AND (envelope->>'aggregateId')::uuid=aggregate_id AND (envelope->>'aggregateVersion')::bigint=version AND envelope->>'mode'='MOCK')
);
CREATE FUNCTION guard_fp_reservation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Fictional provider history is retained' USING ERRCODE='23514'; END IF;
 IF NEW.id<>OLD.id OR NEW.owner_id<>OLD.owner_id OR NEW.quote_id<>OLD.quote_id OR NEW.mode<>OLD.mode OR NEW.version<>OLD.version+1
  OR NEW.payload->'quote' IS DISTINCT FROM OLD.payload->'quote' OR NEW.payload->'createdAt' IS DISTINCT FROM OLD.payload->'createdAt'
  OR NEW.payload->'reference' IS DISTINCT FROM OLD.payload->'reference' THEN
  RAISE EXCEPTION 'Fictional provider immutable input/version invariant' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER fp_reservation_guard BEFORE UPDATE OR DELETE ON fp_reservation FOR EACH ROW EXECUTE FUNCTION guard_fp_reservation();
CREATE FUNCTION immutable_fp_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Fictional provider quote, receipt, financial record and event history is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER fp_quote_immutable BEFORE UPDATE OR DELETE ON fp_quote FOR EACH ROW EXECUTE FUNCTION immutable_fp_history();
CREATE TRIGGER fp_command_immutable BEFORE UPDATE OR DELETE ON fp_command FOR EACH ROW EXECUTE FUNCTION immutable_fp_history();
CREATE TRIGGER fp_money_immutable BEFORE UPDATE OR DELETE ON fp_money_effect FOR EACH ROW EXECUTE FUNCTION immutable_fp_history();
CREATE TRIGGER fp_journal_immutable BEFORE UPDATE OR DELETE ON fp_journal FOR EACH ROW EXECUTE FUNCTION immutable_fp_history();
CREATE TRIGGER fp_event_immutable BEFORE UPDATE OR DELETE ON fp_event FOR EACH ROW EXECUTE FUNCTION immutable_fp_history();
-- Balanced, amount-matched journal pairs must be present before transaction commit.
CREATE FUNCTION check_fp_journal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE effect UUID; expected NUMERIC; entries BIGINT; debits NUMERIC; credits NUMERIC;
BEGIN
 IF TG_TABLE_NAME='fp_money_effect' THEN effect=NEW.id; ELSE effect=NEW.effect_id; END IF;
 SELECT amount INTO expected FROM fp_money_effect WHERE id=effect;
 SELECT count(*),coalesce(sum(amount) FILTER(WHERE side='DEBIT'),0),coalesce(sum(amount) FILTER(WHERE side='CREDIT'),0)
 INTO entries,debits,credits FROM fp_journal WHERE effect_id=effect;
 IF entries<>2 OR debits<>expected OR credits<>expected THEN RAISE EXCEPTION 'Fictional payment journal must balance to effect amount' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER fp_effect_balance AFTER INSERT ON fp_money_effect DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_fp_journal();
CREATE CONSTRAINT TRIGGER fp_entry_balance AFTER INSERT ON fp_journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_fp_journal();
