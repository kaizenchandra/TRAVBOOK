CREATE TABLE coupon_campaign(code VARCHAR(24) PRIMARY KEY,version INT NOT NULL CHECK(version>0),payload JSONB NOT NULL,
 CHECK(payload->>'code'=code AND (payload->>'version')::int=version),CHECK((payload->>'globalLimit')::int>0 AND (payload->>'customerLimit')::int>0));
INSERT INTO coupon_campaign VALUES('TRAVEL10',1,'{"code":"TRAVEL10","version":1,"scope":"FLIGHT_EXTRAS","percent":10,"maximumDiscount":{"amount":"500.00","currency":"INR"},"minimumBase":{"amount":"1000.00","currency":"INR"},"startsAt":"2026-01-01T00:00:00Z","endsAt":"2035-01-01T00:00:00Z","globalLimit":10000,"customerLimit":100,"eligibleCustomer":null,"enabled":true}'::jsonb);
INSERT INTO coupon_campaign VALUES('WELCOME15',1,'{"code":"WELCOME15","version":1,"scope":"FLIGHT_EXTRAS","percent":15,"maximumDiscount":{"amount":"750.00","currency":"INR"},"minimumBase":{"amount":"1000.00","currency":"INR"},"startsAt":"2026-01-01T00:00:00Z","endsAt":"2035-01-01T00:00:00Z","globalLimit":1000,"customerLimit":1,"eligibleCustomer":null,"enabled":true}'::jsonb);
CREATE TABLE coupon_award(id UUID PRIMARY KEY,quote_id UUID NOT NULL UNIQUE,owner_id UUID NOT NULL REFERENCES customer_account(id),code VARCHAR(24) NOT NULL REFERENCES coupon_campaign(code),payload JSONB NOT NULL,
 CHECK((payload->>'id')::uuid=id AND (payload->>'owner')::uuid=owner_id AND (payload->>'quoteId')::uuid=quote_id));
CREATE TABLE coupon_redemption(award_id UUID PRIMARY KEY REFERENCES coupon_award(id),reservation_id UUID NOT NULL UNIQUE,owner_id UUID NOT NULL REFERENCES customer_account(id),code VARCHAR(24) NOT NULL REFERENCES coupon_campaign(code),discount NUMERIC(14,2) NOT NULL CHECK(discount>0),created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX coupon_uses ON coupon_redemption(code,owner_id);
CREATE TABLE coupon_outbox(id UUID PRIMARY KEY,award_id UUID NOT NULL UNIQUE REFERENCES coupon_award(id),envelope JSONB NOT NULL);
CREATE TABLE fp_promotion(quote_id UUID PRIMARY KEY REFERENCES fp_quote(id),owner_id UUID NOT NULL REFERENCES customer_account(id),payload JSONB NOT NULL,CHECK((payload->>'quoteId')::uuid=quote_id AND (payload->>'owner')::uuid=owner_id));
CREATE FUNCTION immutable_coupon_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Coupon awards, redemptions and events are immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER coupon_award_immutable BEFORE UPDATE OR DELETE ON coupon_award FOR EACH ROW EXECUTE FUNCTION immutable_coupon_history();
CREATE TRIGGER coupon_redemption_immutable BEFORE UPDATE OR DELETE ON coupon_redemption FOR EACH ROW EXECUTE FUNCTION immutable_coupon_history();
CREATE TRIGGER coupon_event_immutable BEFORE UPDATE OR DELETE ON coupon_outbox FOR EACH ROW EXECUTE FUNCTION immutable_coupon_history();
CREATE TRIGGER fp_promotion_immutable BEFORE UPDATE OR DELETE ON fp_promotion FOR EACH ROW EXECUTE FUNCTION immutable_coupon_history();
