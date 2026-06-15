ALTER TABLE test_repository
    ADD CONSTRAINT uk_test_repository_name UNIQUE (name);

ALTER TABLE scene
    ADD CONSTRAINT uk_scene_name UNIQUE (name);
