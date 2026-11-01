-- Customer-owned data: no travel integration-mode marker, no invented contact verification.
CREATE TABLE customer_profile (
  customer_id UUID PRIMARY KEY REFERENCES customer_account(id),
  version BIGINT NOT NULL CHECK(version BETWEEN 1 AND 9007199254740991),
  display_name VARCHAR(80) NOT NULL, email VARCHAR(254) NOT NULL, phone VARCHAR(16) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CHECK(phone='' OR phone ~ '^\+[1-9][0-9]{7,14}$')
);
CREATE TABLE customer_profile_command (
  id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customer_account(id),
  command_key VARCHAR(128) NOT NULL, fingerprint CHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
  applied_version BIGINT NOT NULL CHECK(applied_version>0), created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(customer_id,command_key), UNIQUE(customer_id,applied_version), UNIQUE(id,customer_id,applied_version)
);
CREATE TABLE customer_profile_audit (
  id UUID PRIMARY KEY, customer_id UUID NOT NULL, profile_version BIGINT NOT NULL,
  changed_fields VARCHAR(64) NOT NULL CHECK(changed_fields ~ '^(|displayName|email|phone|displayName,email|displayName,phone|email,phone|displayName,email,phone)$'),
  correlation_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
  FOREIGN KEY(id,customer_id,profile_version) REFERENCES customer_profile_command(id,customer_id,applied_version),
  UNIQUE(customer_id,profile_version)
);
CREATE FUNCTION protect_customer_profile() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.customer_id<>OLD.customer_id OR NEW.version<>OLD.version+1 THEN
    RAISE EXCEPTION 'Profile owner/version invariant' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER profile_version_guard BEFORE UPDATE ON customer_profile FOR EACH ROW EXECUTE FUNCTION protect_customer_profile();
CREATE FUNCTION immutable_customer_profile_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Profile operation/audit history is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER immutable_profile_command BEFORE UPDATE OR DELETE ON customer_profile_command FOR EACH ROW EXECUTE FUNCTION immutable_customer_profile_history();
CREATE TRIGGER immutable_profile_audit BEFORE UPDATE OR DELETE ON customer_profile_audit FOR EACH ROW EXECUTE FUNCTION immutable_customer_profile_history();
