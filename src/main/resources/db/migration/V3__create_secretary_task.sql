CREATE TABLE secretary_task (
    id                   UUID         NOT NULL,
    user_id              VARCHAR(128) NOT NULL,
    chat_id              VARCHAR(128) NOT NULL,
    title                VARCHAR(512) NOT NULL,
    description          TEXT,
    status               VARCHAR(32)  NOT NULL,
    assigned_agent_id    VARCHAR(64),
    delegated_brief      TEXT,
    result_summary       TEXT,
    error_message        TEXT,
    requires_confirmation BOOLEAN     NOT NULL DEFAULT FALSE,
    acceptance_criteria  TEXT,
    sort_order           INT          NOT NULL DEFAULT 0,
    list_version         INT          NOT NULL DEFAULT 0,
    artifacts_json       TEXT,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_secretary_task PRIMARY KEY (id)
);

CREATE INDEX idx_secretary_task_user_chat ON secretary_task (user_id, chat_id);
CREATE INDEX idx_secretary_task_chat_order ON secretary_task (chat_id, sort_order);
