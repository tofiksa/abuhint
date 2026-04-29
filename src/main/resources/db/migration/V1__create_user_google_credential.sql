CREATE TABLE user_google_credential (
    user_id                 VARCHAR(128)  NOT NULL,
    encrypted_refresh_token VARCHAR(2048) NOT NULL,
    encrypted_access_token  VARCHAR(4096),
    access_token_expires_at TIMESTAMPTZ,
    scope                   VARCHAR(1024) NOT NULL,
    email                   VARCHAR(320),
    timezone                VARCHAR(64),
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_user_google_credential PRIMARY KEY (user_id)
);
