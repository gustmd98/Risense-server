ALTER TABLE tasks ADD COLUMN start_date DATE;
ALTER TABLE tasks ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tasks ADD CONSTRAINT ck_task_date_range CHECK(start_date IS NULL OR due_date IS NULL OR start_date <= due_date);
ALTER TABLE projects ADD COLUMN project_type VARCHAR(20) NOT NULL DEFAULT 'other';
ALTER TABLE projects ADD CONSTRAINT ck_project_type CHECK(project_type IN ('presentation','report','development','design','research','other'));
