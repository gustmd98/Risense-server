-- Backend A: latest ERD converted to PostgreSQL. Fresh database only.
-- Backend B legacy checkin tables are retained below; no B implementation is added.

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE projects (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    title VARCHAR(100) NOT NULL,
    class_name VARCHAR(100),
    deadline DATE NOT NULL,
    checkin_time TIME(6) NOT NULL,
    checkin_frequency INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_projects_status CHECK (status IN ('IN_PROGRESS', 'DONE', 'CLOSED')),
    CONSTRAINT fk_projects_user FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE project_checkin_days (
    project_id BIGINT NOT NULL,
    day_of_week VARCHAR(3) NOT NULL,
    PRIMARY KEY (project_id, day_of_week),
    CONSTRAINT fk_checkin_days_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT ck_project_checkin_days_day_of_week CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'))
);

CREATE TABLE project_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    join_status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    joined_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    PRIMARY KEY (id),
    CONSTRAINT fk_members_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_project_members_role CHECK (role IN ('LEADER', 'CO_LEADER', 'MEMBER')),
    CONSTRAINT ck_project_members_join_status CHECK (join_status IN ('PENDING', 'APPROVED', 'REJECTED', 'REMOVED')),
    CONSTRAINT uk_project_members UNIQUE (project_id, user_id)
);

CREATE TABLE invite_links (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invite_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_invite_member FOREIGN KEY (created_by) REFERENCES project_members(id),
    CONSTRAINT uk_invite_links_token UNIQUE (token)
);

CREATE TABLE tasks (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    project_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    size VARCHAR(2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INTEGER NOT NULL,
    due_date DATE,
    sort_order INTEGER NOT NULL,
    cancelled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT ck_tasks_size CHECK (size IN ('S', 'M', 'L', 'XL')),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_tasks_progress_range CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE task_assignees (
    task_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (task_id, member_id),
    CONSTRAINT fk_assignees_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_assignees_member FOREIGN KEY (member_id) REFERENCES project_members(id)
);

CREATE TABLE task_prerequisites (
    task_id BIGINT NOT NULL,
    prerequisite_task_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, prerequisite_task_id),
    CONSTRAINT fk_prereq_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_prereq_prereq FOREIGN KEY (prerequisite_task_id) REFERENCES tasks(id)
);

CREATE TABLE sub_tasks (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    task_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    completed BOOLEAN NOT NULL,
    assignee_member_id BIGINT,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_subtasks_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_subtasks_member FOREIGN KEY (assignee_member_id) REFERENCES project_members(id)
);

CREATE TABLE task_artifacts (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    task_id BIGINT NOT NULL,
    title VARCHAR(100),
    url VARCHAR(500) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_artifacts_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_artifacts_member FOREIGN KEY (created_by) REFERENCES project_members(id)
);

CREATE TABLE task_assignee_histories (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    task_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    action VARCHAR(10) NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hist_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_hist_member FOREIGN KEY (member_id) REFERENCES project_members(id),
    CONSTRAINT ck_task_assignee_histories_action CHECK (action IN ('ASSIGN', 'UNASSIGN')),
    CONSTRAINT fk_hist_changer FOREIGN KEY (changed_by) REFERENCES project_members(id)
);

-- Legacy B tables: only foreign-key targets were updated for the A table names.

-- 체크인 회차 (누락/연속누락/회차 이력 계산용)
create table checkin_round (
    id             bigint generated always as identity primary key,
    project_id     bigint      not null references projects (id),
    scheduled_date date        not null,
    deadline_at    timestamptz not null,
    unique (project_id, scheduled_date)
);

-- 체크인 제출 (회차 x 멤버). 누락 = 행이 없는 상태.
create table checkin_submission (
    id           bigint generated always as identity primary key,
    round_id     bigint      not null references checkin_round (id),
    member_id    bigint      not null references project_members (id),
    is_late      boolean     not null default false,
    submitted_at timestamptz not null default now(),
    updated_at   timestamptz,
    unique (round_id, member_id)
);

-- 체크인 내 작업별 업데이트
create table checkin_task_update (
    id              bigint generated always as identity primary key,
    submission_id   bigint      not null references checkin_submission (id),
    task_id         bigint      not null references tasks (id),
    status          varchar(20) not null,
    progress        smallint    not null check (progress in (0, 25, 50, 75, 100)),
    blocked_reason  varchar(30),               -- status=BLOCKED 시 필수 (앱 검증)
    request_to_team text,
    next_action     text,
    unique (submission_id, task_id)
);
create index idx_update_submission on checkin_task_update (submission_id);
create index idx_update_task on checkin_task_update (task_id);


COMMENT ON TABLE users IS '사용자';
COMMENT ON COLUMN users.id IS '사용자 ID';
COMMENT ON COLUMN users.email IS '이메일';
COMMENT ON COLUMN users.password_hash IS '비밀번호 해시';
COMMENT ON COLUMN users.nickname IS '닉네임';
COMMENT ON COLUMN users.created_at IS '가입일시';
COMMENT ON TABLE projects IS '프로젝트';
COMMENT ON COLUMN projects.id IS '프로젝트 ID';
COMMENT ON COLUMN projects.title IS '프로젝트명';
COMMENT ON COLUMN projects.class_name IS '수업명';
COMMENT ON COLUMN projects.deadline IS '최종 마감일';
COMMENT ON COLUMN projects.checkin_time IS '체크인 마감 시간';
COMMENT ON COLUMN projects.checkin_frequency IS '주당 체크인 횟수';
COMMENT ON COLUMN projects.status IS '상태: IN_PROGRESS/DONE/CLOSED';
COMMENT ON COLUMN projects.created_by IS '생성자 사용자 ID';
COMMENT ON COLUMN projects.closed_at IS '종료일시';
COMMENT ON COLUMN projects.created_at IS '생성일시';
COMMENT ON COLUMN projects.updated_at IS '수정일시';
COMMENT ON TABLE project_checkin_days IS '프로젝트 체크인 요일';
COMMENT ON COLUMN project_checkin_days.project_id IS '프로젝트 ID';
COMMENT ON COLUMN project_checkin_days.day_of_week IS '요일: MON~SUN';
COMMENT ON TABLE project_members IS '프로젝트 팀원';
COMMENT ON COLUMN project_members.id IS '팀원 ID';
COMMENT ON COLUMN project_members.project_id IS '프로젝트 ID';
COMMENT ON COLUMN project_members.user_id IS '사용자 ID';
COMMENT ON COLUMN project_members.role IS '역할: LEADER/CO_LEADER/MEMBER';
COMMENT ON COLUMN project_members.join_status IS '가입 상태: PENDING/APPROVED/REJECTED/REMOVED';
COMMENT ON COLUMN project_members.requested_at IS '가입 요청일시';
COMMENT ON COLUMN project_members.joined_at IS '승인일시';
COMMENT ON COLUMN project_members.removed_at IS '내보낸 일시';
COMMENT ON TABLE invite_links IS '초대 링크';
COMMENT ON COLUMN invite_links.id IS '초대 링크 ID';
COMMENT ON COLUMN invite_links.project_id IS '프로젝트 ID';
COMMENT ON COLUMN invite_links.token IS '초대 토큰';
COMMENT ON COLUMN invite_links.created_by IS '발급한 팀원 ID';
COMMENT ON COLUMN invite_links.expires_at IS '만료일시';
COMMENT ON COLUMN invite_links.is_active IS '활성 여부';
COMMENT ON COLUMN invite_links.created_at IS '발급일시';
COMMENT ON TABLE tasks IS '작업';
COMMENT ON COLUMN tasks.id IS '작업 ID';
COMMENT ON COLUMN tasks.project_id IS '프로젝트 ID';
COMMENT ON COLUMN tasks.title IS '작업 이름';
COMMENT ON COLUMN tasks.size IS '크기: S/M/L/XL';
COMMENT ON COLUMN tasks.status IS '상태: TODO/IN_PROGRESS/BLOCKED/DONE/CANCELLED';
COMMENT ON COLUMN tasks.progress IS '진행률(0~100)';
COMMENT ON COLUMN tasks.due_date IS '작업 마감일';
COMMENT ON COLUMN tasks.sort_order IS '정렬 순서';
COMMENT ON COLUMN tasks.cancelled_at IS '취소일시';
COMMENT ON COLUMN tasks.completed_at IS '완료일시';
COMMENT ON COLUMN tasks.created_at IS '생성일시';
COMMENT ON COLUMN tasks.updated_at IS '수정일시';
COMMENT ON TABLE task_assignees IS '작업 담당자';
COMMENT ON COLUMN task_assignees.task_id IS '작업 ID';
COMMENT ON COLUMN task_assignees.member_id IS '담당 팀원 ID';
COMMENT ON COLUMN task_assignees.assigned_at IS '배정일시';
COMMENT ON TABLE task_prerequisites IS '선행 작업 관계';
COMMENT ON COLUMN task_prerequisites.task_id IS '작업 ID';
COMMENT ON COLUMN task_prerequisites.prerequisite_task_id IS '선행 작업 ID';
COMMENT ON TABLE sub_tasks IS '하위 작업';
COMMENT ON COLUMN sub_tasks.id IS '하위 작업 ID';
COMMENT ON COLUMN sub_tasks.task_id IS '작업 ID';
COMMENT ON COLUMN sub_tasks.title IS '하위 작업 이름';
COMMENT ON COLUMN sub_tasks.completed IS '완료 여부';
COMMENT ON COLUMN sub_tasks.assignee_member_id IS '담당 팀원 ID';
COMMENT ON COLUMN sub_tasks.sort_order IS '정렬 순서';
COMMENT ON TABLE task_artifacts IS '작업 산출물';
COMMENT ON COLUMN task_artifacts.id IS '산출물 ID';
COMMENT ON COLUMN task_artifacts.task_id IS '작업 ID';
COMMENT ON COLUMN task_artifacts.title IS '산출물 이름';
COMMENT ON COLUMN task_artifacts.url IS '산출물 링크';
COMMENT ON COLUMN task_artifacts.created_by IS '등록한 팀원 ID';
COMMENT ON COLUMN task_artifacts.created_at IS '등록일시';
COMMENT ON TABLE task_assignee_histories IS '담당자 변경 이력';
COMMENT ON COLUMN task_assignee_histories.id IS '이력 ID';
COMMENT ON COLUMN task_assignee_histories.task_id IS '작업 ID';
COMMENT ON COLUMN task_assignee_histories.member_id IS '대상 팀원 ID';
COMMENT ON COLUMN task_assignee_histories.action IS '변경: ASSIGN/UNASSIGN';
COMMENT ON COLUMN task_assignee_histories.changed_by IS '변경한 팀원 ID';
COMMENT ON COLUMN task_assignee_histories.changed_at IS '변경일시';
