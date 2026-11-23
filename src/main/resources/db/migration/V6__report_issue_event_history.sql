-- Append-only issue events: current task_issues alone cannot retain repeated report/resolve cycles.
CREATE TABLE report_history_metadata (
 id INTEGER PRIMARY KEY CHECK(id=1), issue_events_started_at TIMESTAMPTZ NOT NULL
);
INSERT INTO report_history_metadata VALUES(1,CURRENT_TIMESTAMP);
CREATE TABLE task_issue_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 task_id BIGINT NOT NULL REFERENCES tasks(id),
 kind VARCHAR(20) NOT NULL CHECK(kind IN ('ISSUE_REPORTED','ISSUE_RESOLVED')),
 member_id BIGINT REFERENCES project_members(id),
 issue_revision BIGINT NOT NULL,
 content TEXT,
 occurred_at TIMESTAMPTZ NOT NULL,
 backfilled BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(task_id,issue_revision,kind)
);
CREATE INDEX idx_issue_events_task_time ON task_issue_events(task_id,occurred_at DESC,id DESC);
-- Only events supported by surviving timestamps are backfilled; earlier cycles are unknown.
INSERT INTO task_issue_events(task_id,kind,member_id,issue_revision,content,occurred_at,backfilled)
 SELECT task_id,'ISSUE_REPORTED',reported_by,greatest(revision-CASE WHEN resolved_at IS NULL THEN 0 ELSE 1 END,1),content,reported_at,TRUE
 FROM task_issues WHERE reported_at IS NOT NULL;
INSERT INTO task_issue_events(task_id,kind,member_id,issue_revision,content,occurred_at,backfilled)
 SELECT task_id,'ISSUE_RESOLVED',resolved_by,revision,content,resolved_at,TRUE
 FROM task_issues WHERE resolved_at IS NOT NULL;
CREATE FUNCTION capture_task_issue_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.open AND NEW.reported_at IS NOT NULL THEN
  IF TG_OP='INSERT' THEN
   INSERT INTO task_issue_events(task_id,kind,member_id,issue_revision,content,occurred_at)
    VALUES(NEW.task_id,'ISSUE_REPORTED',NEW.reported_by,NEW.revision,NEW.content,NEW.reported_at);
  ELSIF NEW.revision<>OLD.revision AND (NEW.reported_at IS DISTINCT FROM OLD.reported_at OR NOT OLD.open OR NEW.content IS DISTINCT FROM OLD.content) THEN
   INSERT INTO task_issue_events(task_id,kind,member_id,issue_revision,content,occurred_at)
    VALUES(NEW.task_id,'ISSUE_REPORTED',NEW.reported_by,NEW.revision,NEW.content,NEW.reported_at);
  END IF;
 ELSIF NEW.resolved_at IS NOT NULL THEN
  IF TG_OP='INSERT' THEN
   INSERT INTO task_issue_events(task_id,kind,member_id,issue_revision,content,occurred_at)
    VALUES(NEW.task_id,'ISSUE_RESOLVED',NEW.resolved_by,NEW.revision,NEW.content,NEW.resolved_at);
  ELSIF NEW.revision<>OLD.revision AND OLD.open THEN
   INSERT INTO task_issue_events(task_id,kind,member_id,issue_revision,content,occurred_at)
    VALUES(NEW.task_id,'ISSUE_RESOLVED',NEW.resolved_by,NEW.revision,NEW.content,NEW.resolved_at);
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER task_issue_event_capture AFTER INSERT OR UPDATE ON task_issues
 FOR EACH ROW EXECUTE FUNCTION capture_task_issue_event();
