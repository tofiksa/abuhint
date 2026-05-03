-- Task execution records for observability of delegated work
CREATE TABLE task_execution (
    id          UUID PRIMARY KEY,
    task_id     UUID NOT NULL REFERENCES secretary_task(id) ON DELETE CASCADE,
    agent_id    VARCHAR(64) NOT NULL,
    user_id     VARCHAR(128) NOT NULL,
    chat_id     VARCHAR(128) NOT NULL,
    brief       TEXT NOT NULL,
    status      VARCHAR(32) NOT NULL DEFAULT 'running',
    result_summary TEXT,
    error_message  TEXT,
    started_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT
);

CREATE INDEX idx_task_execution_task_id ON task_execution(task_id);
CREATE INDEX idx_task_execution_user_id ON task_execution(user_id);

-- Artifacts produced by task executions
CREATE TABLE task_artifact (
    id            UUID PRIMARY KEY,
    execution_id  UUID NOT NULL REFERENCES task_execution(id) ON DELETE CASCADE,
    artifact_type VARCHAR(64) NOT NULL,
    name          VARCHAR(512),
    content_url   TEXT,
    content_text  TEXT,
    metadata_json TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_artifact_execution_id ON task_artifact(execution_id);
