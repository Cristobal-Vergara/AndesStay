ALTER TABLE reservations ADD COLUMN guest_assigned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE outbox_events (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    destination VARCHAR(16) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    INDEX idx_outbox_ready (published_at,next_attempt_at,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE consumed_events (
    destination VARCHAR(16) NOT NULL,
    event_id VARCHAR(80) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (destination,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_audit_date_id ON audit_events (event_date,id);
CREATE INDEX idx_audit_reservation_action_date ON audit_events (reservation_id,action,event_date);
