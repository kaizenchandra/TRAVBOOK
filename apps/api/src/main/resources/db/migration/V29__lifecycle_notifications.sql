CREATE TABLE lifecycle_forwarded(source VARCHAR(40) NOT NULL,event_id UUID NOT NULL,forwarded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(source,event_id));
CREATE TABLE lifecycle_rejected(source VARCHAR(40) NOT NULL,event_id UUID NOT NULL,reason TEXT NOT NULL CHECK(reason='INVALID_ENVELOPE'),rejected_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(source,event_id));
CREATE TABLE notification_epoch(id INT PRIMARY KEY CHECK(id=1),started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp());
INSERT INTO notification_epoch(id) VALUES(1);
CREATE TABLE notification_cursor(source VARCHAR(40) NOT NULL,aggregate_id UUID NOT NULL,version BIGINT NOT NULL CHECK(version>=0),state_key TEXT NOT NULL,PRIMARY KEY(source,aggregate_id));
CREATE TABLE notification_seen(source VARCHAR(40) NOT NULL,event_id UUID NOT NULL,aggregate_id UUID NOT NULL,version BIGINT NOT NULL,PRIMARY KEY(source,event_id));
CREATE TABLE customer_notification(id UUID PRIMARY KEY,owner_id UUID NOT NULL REFERENCES customer_account(id),source VARCHAR(40) NOT NULL,event_id UUID NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,read_at TIMESTAMPTZ,payload JSONB NOT NULL,UNIQUE(source,event_id),CHECK((payload->>'id')::uuid=id AND (payload->>'owner')::uuid=owner_id AND payload->>'mode'='MOCK'));
CREATE INDEX notification_owner_history ON customer_notification(owner_id,occurred_at DESC,id DESC);
CREATE TABLE notification_delivery(id UUID PRIMARY KEY,notice_id UUID NOT NULL REFERENCES customer_notification(id),owner_id UUID NOT NULL REFERENCES customer_account(id),channel TEXT NOT NULL CHECK(channel IN ('EMAIL','SMS')),state TEXT NOT NULL CHECK(state IN ('QUEUED','RUNNING','RETRY','SIMULATED_DELIVERED','SUPPRESSED','FAILED','REVIEW')),attempts INT NOT NULL CHECK(attempts BETWEEN 0 AND 3),version BIGINT NOT NULL CHECK(version>0),next_at TIMESTAMPTZ NOT NULL,payload JSONB NOT NULL,lease_token UUID,lease_until TIMESTAMPTZ,UNIQUE(notice_id,channel),CHECK((lease_token IS NULL)=(lease_until IS NULL)),CHECK((payload->>'id')::uuid=id AND (payload->>'noticeId')::uuid=notice_id AND (payload->>'owner')::uuid=owner_id AND payload->>'state'=state AND (payload->>'version')::bigint=version AND (payload->>'attempts')::int=attempts));
CREATE INDEX notification_due ON notification_delivery(next_at,id) WHERE state IN ('QUEUED','RUNNING','RETRY');
CREATE TABLE notification_delivery_event(id UUID PRIMARY KEY,delivery_id UUID NOT NULL REFERENCES notification_delivery(id),version BIGINT NOT NULL,state TEXT NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,UNIQUE(delivery_id,version));
CREATE TABLE notification_provider_receipt(delivery_id UUID PRIMARY KEY,fingerprint VARCHAR(200) NOT NULL,reference VARCHAR(100) NOT NULL UNIQUE CHECK(reference LIKE 'MOCK-NOTIFICATION-%'),accepted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp());
CREATE FUNCTION immutable_notification_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Notification delivery history is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER forwarded_immutable BEFORE UPDATE OR DELETE ON lifecycle_forwarded FOR EACH ROW EXECUTE FUNCTION immutable_notification_history();
CREATE TRIGGER seen_immutable BEFORE UPDATE OR DELETE ON notification_seen FOR EACH ROW EXECUTE FUNCTION immutable_notification_history();
CREATE TRIGGER delivery_event_immutable BEFORE UPDATE OR DELETE ON notification_delivery_event FOR EACH ROW EXECUTE FUNCTION immutable_notification_history();
CREATE TRIGGER provider_notification_immutable BEFORE UPDATE OR DELETE ON notification_provider_receipt FOR EACH ROW EXECUTE FUNCTION immutable_notification_history();
CREATE FUNCTION guard_notice() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Notification history is retained' USING ERRCODE='23514'; END IF;
IF NEW.id<>OLD.id OR NEW.owner_id<>OLD.owner_id OR NEW.source<>OLD.source OR NEW.event_id<>OLD.event_id OR NEW.payload<>OLD.payload OR NEW.occurred_at<>OLD.occurred_at OR (OLD.read_at IS NOT NULL AND NEW.read_at IS DISTINCT FROM OLD.read_at) THEN RAISE EXCEPTION 'Notification content/read history invariant' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER notification_guard BEFORE UPDATE OR DELETE ON customer_notification FOR EACH ROW EXECUTE FUNCTION guard_notice();
CREATE FUNCTION guard_notification_delivery() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Notification delivery is retained' USING ERRCODE='23514'; END IF;
IF NEW.id<>OLD.id OR NEW.notice_id<>OLD.notice_id OR NEW.owner_id<>OLD.owner_id OR NEW.channel<>OLD.channel OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Notification delivery identity/version invariant' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER notification_delivery_guard BEFORE UPDATE OR DELETE ON notification_delivery FOR EACH ROW EXECUTE FUNCTION guard_notification_delivery();
