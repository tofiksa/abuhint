CREATE TABLE device_calendar_session (
    id VARCHAR(64) PRIMARY KEY,
    payload TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);
