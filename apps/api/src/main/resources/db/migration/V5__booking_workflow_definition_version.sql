ALTER TABLE booking_workflow ADD COLUMN definition_version INT NOT NULL DEFAULT 1 CHECK(definition_version=1);
COMMENT ON COLUMN booking_workflow.definition_version IS 'Persisted workflow definition; v1 performs only read-only offer validation. Future workers must select supported definitions explicitly.';
