CREATE TABLE reservation_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    audience VARCHAR(16) NOT NULL,
    recipient_id VARCHAR(73) NULL,
    reservation_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notice_audience_id (audience, occurred_at, id),
    INDEX idx_notice_recipient_id (recipient_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
