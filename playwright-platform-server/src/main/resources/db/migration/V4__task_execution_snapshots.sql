ALTER TABLE task
    ADD COLUMN resolved_branch VARCHAR(128) NULL,
    ADD COLUMN resolved_browser VARCHAR(32) NULL,
    ADD COLUMN resolved_env_json TEXT NULL,
    ADD COLUMN resolved_match_value VARCHAR(256) NULL,
    ADD COLUMN resolved_test_root VARCHAR(256) NULL,
    ADD COLUMN resolved_run_command VARCHAR(1024) NULL;
