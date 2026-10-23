CREATE TABLE space (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    name varchar(128) NOT NULL,
    description varchar(512) NULL,
    owner_user_id bigint NULL,
    created_by bigint NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE space_member (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    space_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role varchar(32) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at datetime NOT NULL DEFAULT current_timestamp,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_space_member_space FOREIGN KEY (space_id) REFERENCES space(id),
    CONSTRAINT uk_space_member UNIQUE (space_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE space_access_request (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    space_id bigint NOT NULL,
    applicant_user_id bigint NOT NULL,
    requested_role varchar(32) NOT NULL,
    reason varchar(512) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    review_comment varchar(512) NULL,
    reviewed_by bigint NULL,
    reviewed_at datetime NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_space_access_request_space FOREIGN KEY (space_id) REFERENCES space(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_space_access_request_space_status_created
    ON space_access_request (space_id, status, created_at);

CREATE INDEX idx_space_access_request_applicant_status_created
    ON space_access_request (applicant_user_id, status, created_at);

ALTER TABLE test_repository ADD COLUMN space_id bigint NULL AFTER id;
ALTER TABLE scene ADD COLUMN space_id bigint NULL AFTER id;
ALTER TABLE task ADD COLUMN space_id bigint NULL AFTER id;
ALTER TABLE schedule_event ADD COLUMN space_id bigint NULL AFTER id;

INSERT INTO space (id, name, description, owner_user_id, created_by)
VALUES (1, '默认空间', '存量数据迁移生成的默认空间', 1, 1);

UPDATE test_repository
SET space_id = 1
WHERE space_id IS NULL;

UPDATE scene
SET space_id = 1
WHERE space_id IS NULL;

UPDATE task
SET space_id = 1
WHERE space_id IS NULL;

UPDATE schedule_event
SET space_id = 1
WHERE space_id IS NULL;

ALTER TABLE test_repository MODIFY COLUMN space_id bigint NOT NULL;
ALTER TABLE scene MODIFY COLUMN space_id bigint NOT NULL;
ALTER TABLE task MODIFY COLUMN space_id bigint NOT NULL;
ALTER TABLE schedule_event MODIFY COLUMN space_id bigint NOT NULL;

ALTER TABLE test_repository
    ADD CONSTRAINT fk_test_repository_space FOREIGN KEY (space_id) REFERENCES space(id);

ALTER TABLE scene
    ADD CONSTRAINT fk_scene_space FOREIGN KEY (space_id) REFERENCES space(id);

ALTER TABLE task
    ADD CONSTRAINT fk_task_space FOREIGN KEY (space_id) REFERENCES space(id);

ALTER TABLE schedule_event
    ADD CONSTRAINT fk_schedule_event_space FOREIGN KEY (space_id) REFERENCES space(id);

CREATE INDEX idx_test_repository_space_id ON test_repository (space_id);
CREATE INDEX idx_scene_space_id ON scene (space_id);
CREATE INDEX idx_task_space_id ON task (space_id);
CREATE INDEX idx_schedule_event_space_id ON schedule_event (space_id);
