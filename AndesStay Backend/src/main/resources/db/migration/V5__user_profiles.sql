CREATE TABLE user_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_key VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(320),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_user_profile_owner UNIQUE (owner_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
