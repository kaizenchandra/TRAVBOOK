-- Earlier local, unpublished records already have an authoritative mode column.
-- Add that metadata to their envelope without changing any business payload.
UPDATE pricing_outbox
SET envelope=jsonb_set(envelope,'{mode}',to_jsonb(mode::text))
WHERE NOT (envelope ? 'mode') AND published_at IS NULL;

CREATE FUNCTION protect_pricing_event_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN
    RAISE EXCEPTION 'Pricing event history is immutable' USING ERRCODE='23514';
  END IF;
  IF NEW.id IS DISTINCT FROM OLD.id OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
     OR NEW.event_type IS DISTINCT FROM OLD.event_type OR NEW.mode IS DISTINCT FROM OLD.mode
     OR NEW.envelope IS DISTINCT FROM OLD.envelope THEN
    RAISE EXCEPTION 'Pricing event history is immutable' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_pricing_event BEFORE UPDATE OR DELETE ON pricing_outbox
  FOR EACH ROW EXECUTE FUNCTION protect_pricing_event_history();
