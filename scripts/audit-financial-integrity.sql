-- Read-only audit of locally recorded MOCK money movements, not gateway settlement.
-- A clean result requires every *_violations value to be zero.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
WITH capture_entries AS (
  SELECT payment_id, count(*) AS entries,
         sum(CASE WHEN direction='DEBIT' THEN amount ELSE -amount END) AS net,
         min(amount) AS min_amount, max(amount) AS max_amount,
         min(currency) AS min_currency, max(currency) AS max_currency
  FROM payment_journal GROUP BY payment_id
), refund_entries AS (
  SELECT operation_id, count(*) AS entries,
         sum(CASE WHEN direction='DEBIT' THEN amount ELSE -amount END) AS net,
         min(amount) AS min_amount, max(amount) AS max_amount,
         min(currency) AS min_currency, max(currency) AS max_currency
  FROM payment_refund_journal GROUP BY operation_id
), refund_totals AS (
  SELECT payment_id, sum(amount) AS amount FROM payment_refund GROUP BY payment_id
)
SELECT json_build_object(
  'captured_accounts', (SELECT count(*) FROM payment_account WHERE status='CAPTURED'),
  'completed_refunds', (SELECT count(*) FROM payment_refund WHERE status='SUCCEEDED'),
  'capture_journal_violations', (
    SELECT count(*) FROM payment_account p LEFT JOIN capture_entries e ON e.payment_id=p.id
    WHERE (p.status='CAPTURED' AND (
      coalesce(e.entries,0)<>2 OR e.net<>0 OR e.min_amount<>p.amount OR e.max_amount<>p.amount
      OR e.min_currency<>p.currency OR e.max_currency<>p.currency))
      OR (p.status<>'CAPTURED' AND e.payment_id IS NOT NULL)
  ),
  'refund_journal_violations', (
    SELECT count(*) FROM payment_refund r LEFT JOIN refund_entries e ON e.operation_id=r.operation_id
    WHERE (r.status='SUCCEEDED' AND (
      coalesce(e.entries,0)<>2 OR e.net<>0 OR e.min_amount<>r.amount OR e.max_amount<>r.amount
      OR e.min_currency<>r.currency OR e.max_currency<>r.currency))
      OR (r.status<>'SUCCEEDED' AND e.operation_id IS NOT NULL)
  ),
  'refund_entitlement_violations', (
    SELECT count(*) FROM refund_totals r JOIN payment_account p ON p.id=r.payment_id
    WHERE p.status<>'CAPTURED' OR r.amount>p.amount
  ),
  'refund_owner_currency_violations', (
    SELECT count(*) FROM payment_refund r JOIN payment_account p ON p.id=r.payment_id
    WHERE r.customer_id<>p.customer_id OR r.currency<>p.currency
  )
);
COMMIT;
