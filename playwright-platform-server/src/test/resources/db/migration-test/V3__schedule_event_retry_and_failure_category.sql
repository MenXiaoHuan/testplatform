ALTER TABLE schedule_event
    ADD COLUMN failure_category varchar(32) NULL AFTER error_message;
