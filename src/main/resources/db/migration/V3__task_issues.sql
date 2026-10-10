-- Backend B: shared current issue. Check-in snapshots are separate.
CREATE TABLE task_issues (
    task_id BIGINT PRIMARY KEY REFERENCES tasks(id),
    open BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    reported_by BIGINT REFERENCES project_members(id),
    resolved_by BIGINT REFERENCES project_members(id),
    reported_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    CHECK (NOT open OR (content IS NOT NULL AND length(trim(content)) BETWEEN 1 AND 2000)),
    CHECK (NOT open OR (resolved_by IS NULL AND resolved_at IS NULL))
);
