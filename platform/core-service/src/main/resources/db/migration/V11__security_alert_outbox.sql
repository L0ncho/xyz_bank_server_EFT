ALTER TABLE outbox_events ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE outbox_events ADD COLUMN card_id UUID;

CREATE TABLE recorded_security_alerts (
    event_id VARCHAR(128) PRIMARY KEY,
    card_id UUID NOT NULL,
    occurred_on DATE NOT NULL
);
