-- Existing REVIEW rows are unchanged. A prior state is never inferred.
CREATE TABLE journey_review_checkpoint(
 booking_id UUID NOT NULL REFERENCES journey_booking(id),review_version BIGINT NOT NULL CHECK(review_version>1),payload JSONB NOT NULL,
 PRIMARY KEY(booking_id,review_version),
 CHECK((payload->>'id')::uuid=booking_id AND (payload->>'version')::bigint+1=review_version AND payload->>'status'<>'REVIEW' AND payload->>'step'<>'DONE' AND payload->>'mode'='MOCK')
);
CREATE TABLE journey_recovery_action(
 id UUID PRIMARY KEY,booking_id UUID NOT NULL REFERENCES journey_booking(id),actor VARCHAR(64) NOT NULL CHECK(actor ~ '^[0-9a-f]{64}$'),
 command_key VARCHAR(100) NOT NULL,fingerprint VARCHAR(200) NOT NULL,created_at TIMESTAMPTZ NOT NULL,payload JSONB NOT NULL,
 UNIQUE(actor,command_key),CHECK((payload->>'id')::uuid=id AND (payload->>'bookingId')::uuid=booking_id AND payload->>'actor'=actor)
);
CREATE INDEX journey_review_queue ON journey_booking((payload->'quote'->'selection'->'request'->>'kind'),created_at,id) WHERE status='REVIEW';
CREATE TRIGGER immutable_journey_checkpoint BEFORE UPDATE OR DELETE ON journey_review_checkpoint FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
CREATE TRIGGER immutable_journey_recovery_action BEFORE UPDATE OR DELETE ON journey_recovery_action FOR EACH ROW EXECUTE FUNCTION reject_booking_command_change();
