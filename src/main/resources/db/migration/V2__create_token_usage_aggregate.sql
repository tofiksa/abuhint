CREATE TABLE token_usage_aggregate (
    id                  BIGSERIAL     NOT NULL,
    user_id             VARCHAR(128)  NOT NULL,
    chat_id             VARCHAR(128)  NOT NULL,
    assistant           VARCHAR(64)   NOT NULL,
    client_platform     VARCHAR(64)   NOT NULL,
    model_name          VARCHAR(128)  NOT NULL,
    input_tokens        BIGINT        NOT NULL DEFAULT 0,
    cached_input_tokens BIGINT        NOT NULL DEFAULT 0,
    output_tokens       BIGINT        NOT NULL DEFAULT 0,
    request_count       INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_token_usage_aggregate PRIMARY KEY (id),
    CONSTRAINT uq_token_usage_aggregate UNIQUE (user_id, chat_id, assistant, client_platform, model_name)
);

CREATE INDEX idx_token_usage_aggregate_user_updated
    ON token_usage_aggregate (user_id, updated_at);
