-- Preserve legacy checkin_* tables. New records begin at rollout, never inferred backwards.
CREATE TABLE checkin_rollout (id INTEGER PRIMARY KEY CHECK(id=1), starts_at TIMESTAMPTZ NOT NULL);
INSERT INTO checkin_rollout VALUES (1, CURRENT_TIMESTAMP);
CREATE TABLE checkin_schedules (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 project_id BIGINT NOT NULL REFERENCES projects(id),
 effective_at TIMESTAMPTZ NOT NULL,
 stops_at TIMESTAMPTZ,
 weekdays VARCHAR(27) NOT NULL,
 next_date DATE NOT NULL,
 UNIQUE(project_id, effective_at),
 CHECK(stops_at IS NULL OR stops_at>=effective_at)
);
CREATE INDEX idx_checkin_schedules_due ON checkin_schedules(next_date);
CREATE TABLE checkin_rounds (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 project_id BIGINT NOT NULL REFERENCES projects(id),
 schedule_id BIGINT NOT NULL REFERENCES checkin_schedules(id),
 scheduled_date DATE NOT NULL,
 opens_at TIMESTAMPTZ NOT NULL,
 deadline_at TIMESTAMPTZ NOT NULL,
 late_until_at TIMESTAMPTZ NOT NULL,
 UNIQUE(project_id, scheduled_date),
 CHECK(opens_at<deadline_at AND deadline_at<=late_until_at)
);
CREATE INDEX idx_checkin_rounds_project ON checkin_rounds(project_id, scheduled_date DESC);
CREATE TABLE checkin_participants (
 round_id BIGINT NOT NULL REFERENCES checkin_rounds(id),
 member_id BIGINT NOT NULL REFERENCES project_members(id),
 joined_at TIMESTAMPTZ NOT NULL,
 departed_at TIMESTAMPTZ,
 excluded_reason VARCHAR(30) CHECK(excluded_reason IN ('PROJECT_CLOSED')),
 PRIMARY KEY(round_id,member_id)
);
CREATE TABLE checkin_targets (
 round_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 task_id BIGINT NOT NULL REFERENCES tasks(id),
 title_at_open VARCHAR(100) NOT NULL,
 excluded_reason VARCHAR(30) CHECK(excluded_reason IN ('UNASSIGNED','CANCELLED')),
 PRIMARY KEY(round_id,member_id,task_id),
 FOREIGN KEY(round_id,member_id) REFERENCES checkin_participants(round_id,member_id)
);
CREATE TABLE checkin_submissions (
 round_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 revision BIGINT NOT NULL CHECK(revision>0),
 is_late BOOLEAN NOT NULL,
 submitted_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 snapshot JSONB NOT NULL,
 PRIMARY KEY(round_id,member_id),
 FOREIGN KEY(round_id,member_id) REFERENCES checkin_participants(round_id,member_id)
);
CREATE TABLE checkin_requests (
 round_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 request_key UUID NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 response JSONB NOT NULL,
 PRIMARY KEY(round_id,member_id,request_key),
 FOREIGN KEY(round_id,member_id) REFERENCES checkin_participants(round_id,member_id)
);
