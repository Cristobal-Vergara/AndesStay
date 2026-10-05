CREATE TABLE units (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    price DECIMAL(38,2) NOT NULL,
    available BOOLEAN NOT NULL,
    description VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    guest_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    unit_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    event_date DATETIME(6) NOT NULL,
    reservation_id BIGINT NOT NULL,
    user VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
