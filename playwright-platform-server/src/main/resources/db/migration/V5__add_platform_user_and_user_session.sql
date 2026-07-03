CREATE TABLE platform_user (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    username varchar(128) NOT NULL,
    nickname varchar(128) NULL,
    password_hash varchar(255) NOT NULL,
    avatar_object_key varchar(512) NULL,
    enabled tinyint(1) NOT NULL DEFAULT 1,
    last_space_id bigint NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT uk_platform_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_session (
    session_id varchar(64) PRIMARY KEY,
    user_id bigint NOT NULL,
    expires_at datetime NOT NULL,
    created_at datetime NOT NULL DEFAULT current_timestamp,
    updated_at datetime NOT NULL DEFAULT current_timestamp ON UPDATE current_timestamp,
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES platform_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_session_user_id ON user_session (user_id);
CREATE INDEX idx_user_session_expires_at ON user_session (expires_at);

INSERT INTO platform_user (
    id, username, nickname, password_hash, avatar_object_key, enabled
)
SELECT
    1,
    'admin',
    '未命名用户',
    '$2a$10$klLc4mpiRtJ2TXtjxrXlN.cgQ2RYYRKPD0cBirSx86XnWTUHPv4aO',
    'avatars/admin.png',
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM platform_user
    WHERE username = 'admin'
);
