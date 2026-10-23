ALTER TABLE test_repository
    ADD COLUMN auth_type varchar(20) NOT NULL DEFAULT 'PUBLIC';

ALTER TABLE test_repository
    ADD COLUMN credential_username varchar(255) NULL;

ALTER TABLE test_repository
    ADD COLUMN credential_secret text NULL;

ALTER TABLE test_repository
    ADD COLUMN credential_updated_at datetime NULL;
