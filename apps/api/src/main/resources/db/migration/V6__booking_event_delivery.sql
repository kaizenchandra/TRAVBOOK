CREATE TABLE booking_event_delivery (
  event_id UUID PRIMARY KEY REFERENCES booking_outbox(id),
  status VARCHAR(16) NOT NULL DEFAULT 'READY' CHECK(status IN ('READY','RUNNING','RETRY','DELIVERED','DEAD')),
  attempts INT NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
  generation INT NOT NULL DEFAULT 1 CHECK(generation>0),
  queued_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  lease_token UUID,
  lease_until TIMESTAMPTZ,
  failure_code VARCHAR(40),
  CHECK((status='RUNNING')=(lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX booking_delivery_due ON booking_event_delivery(next_attempt_at,event_id) WHERE status IN ('READY','RETRY');
CREATE INDEX booking_delivery_abandoned ON booking_event_delivery(lease_until) WHERE status='RUNNING';
CREATE FUNCTION schedule_booking_delivery() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO booking_event_delivery(event_id) VALUES(NEW.id);
  RETURN NEW;
END;
$$;
CREATE TRIGGER schedule_booking_event AFTER INSERT ON booking_outbox FOR EACH ROW EXECUTE FUNCTION schedule_booking_delivery();
INSERT INTO booking_event_delivery(event_id,status)
  SELECT id,CASE WHEN published_at IS NULL THEN 'READY' ELSE 'DELIVERED' END FROM booking_outbox;

-- This immutable receipt is also the inbox marker; receipt and projection cannot partially commit.
CREATE TABLE booking_timeline_event (
  event_id UUID PRIMARY KEY REFERENCES booking_outbox(id),
  booking_id UUID NOT NULL REFERENCES booking_intent(id),
  aggregate_version BIGINT NOT NULL CHECK(aggregate_version>=1),
  envelope JSONB NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(booking_id,aggregate_version)
);
CREATE TRIGGER immutable_booking_timeline BEFORE UPDATE OR DELETE ON booking_timeline_event
  FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TABLE booking_delivery_replay (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL REFERENCES booking_outbox(id),
  generation INT NOT NULL,
  actor VARCHAR(120) NOT NULL CHECK(length(trim(actor)) BETWEEN 1 AND 120),
  reason VARCHAR(500) NOT NULL CHECK(length(trim(reason)) BETWEEN 10 AND 500),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(event_id,generation)
);
CREATE TRIGGER immutable_booking_delivery_replay BEFORE UPDATE OR DELETE ON booking_delivery_replay
  FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE FUNCTION replay_booking_event(target UUID,operator_name TEXT,explanation TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE next_generation INT;
BEGIN
  SELECT generation+1 INTO next_generation FROM booking_event_delivery WHERE event_id=target AND status='DEAD' FOR UPDATE;
  IF next_generation IS NULL THEN RAISE EXCEPTION 'Only quarantined delivery may be replayed' USING ERRCODE='23514'; END IF;
  INSERT INTO booking_delivery_replay(id,event_id,generation,actor,reason) VALUES(gen_random_uuid(),target,next_generation,operator_name,explanation);
  UPDATE booking_event_delivery SET status='READY',attempts=0,generation=next_generation,next_attempt_at=clock_timestamp(),failure_code=NULL WHERE event_id=target;
END;
$$;
REVOKE ALL ON FUNCTION replay_booking_event(UUID,TEXT,TEXT) FROM PUBLIC;
COMMENT ON FUNCTION replay_booking_event(UUID,TEXT,TEXT) IS 'Database-owner operational action only; diagnose and record actor/reason before replay. Never edits immutable source content.';
