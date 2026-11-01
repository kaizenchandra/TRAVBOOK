-- V12 was already exercised locally; add guards without editing applied migrations.
ALTER TABLE stay_quote ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK');
ALTER TABLE stay_booking ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'MOCK' CHECK(mode='MOCK');
ALTER TABLE stay_quote ADD CONSTRAINT stay_quote_owner_binding CHECK((payload->>'owner')::uuid=customer_id AND (payload->>'version')::integer=1);
ALTER TABLE stay_booking ADD CONSTRAINT stay_booking_owner_binding CHECK((payload->'quote'->>'owner')::uuid=customer_id AND (payload->'quote'->>'id')::uuid=quote_id);
ALTER TABLE stay_booking ADD CONSTRAINT stay_booking_status CHECK(status IN('PROCESSING','CONFIRMED','FAILED','CANCELLATION_PENDING','CANCELLED','REVIEW'));
ALTER TABLE stay_booking ADD CONSTRAINT stay_booking_confirmed_effects CHECK(status<>'CONFIRMED' OR (payload->>'payment'='CAPTURED' AND payload->>'reservation'='CONFIRMED'));
ALTER TABLE stay_booking ADD CONSTRAINT stay_booking_cancelled_reservation CHECK(status<>'CANCELLED' OR payload->>'reservation'='CANCELLED');
ALTER TABLE stay_cancellation_quote ADD CONSTRAINT stay_cancellation_owner_binding CHECK((payload->>'id')::uuid=id AND (payload->>'bookingId')::uuid=booking_id AND (payload->>'owner')::uuid=customer_id);
ALTER TABLE stay_voucher ADD CONSTRAINT stay_voucher_binding CHECK((payload->>'id')::uuid=id AND (payload->>'bookingId')::uuid=booking_id AND (payload->>'byteLength')::integer BETWEEN 1 AND 32768);
ALTER TABLE stay_outbox ADD CONSTRAINT stay_event_binding CHECK((envelope->>'eventId')::uuid=id AND (envelope->>'aggregateId')::uuid=aggregate_id AND (envelope->>'aggregateVersion')::bigint=aggregate_version AND envelope->>'eventType'=event_type AND envelope->>'mode'='MOCK' AND envelope->>'producer'='stays' AND (envelope->>'schemaVersion')::integer=1);
ALTER TABLE stay_outbox ADD CONSTRAINT stay_event_no_guest_payload CHECK(envelope->'payload' ?& ARRAY['status','payment','reservation','refund'] AND ((envelope->'payload')-'status'-'payment'-'reservation'-'refund')='{}'::jsonb);
ALTER TABLE hotel_provider_operation ADD CONSTRAINT hotel_effect_binding CHECK((receipt->>'operationId')::uuid=operation_id AND (receipt->>'reservationId')::uuid=reservation_id AND receipt->>'mode'='MOCK');
ALTER TABLE hotel_provider_reservation ADD CONSTRAINT hotel_reference_mode CHECK(reference LIKE 'MOCK-STAY-%');
