ALTER TABLE task
    ADD COLUMN cancel_requested_by varchar(64) NULL AFTER cancel_requested_at;
