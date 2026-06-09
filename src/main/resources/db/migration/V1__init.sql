-- =========================================================
-- 팀플 리스크 레이더 - 초기 스키마 (V1)
-- 정책: 하드 삭제 없음. 상태/소프트 플래그로만 관리.
-- =========================================================

-- 계정 (닉네임은 계정 기준 통일)
create table account (
    id            bigint generated always as identity primary key,
    email         varchar(255) not null unique,
    password_hash varchar(255) not null,
    nickname      varchar(100) not null,
    created_at    timestamptz  not null default now()
);

-- 프로젝트 (status: ACTIVE/COMPLETED, OVERDUE는 final_deadline로 파생)
create table project (
    id                    bigint generated always as identity primary key,
    created_by            bigint       not null references account (id),
    name                  varchar(200) not null,
    course_name           varchar(200),
    project_type          varchar(30)  not null,
    final_deadline        date,
    checkin_frequency     smallint     not null default 3 check (checkin_frequency between 1 and 7),
    checkin_deadline_time time         not null default '23:59',
    status                varchar(20)  not null default 'ACTIVE',
    completed_at          timestamptz,
    created_at            timestamptz  not null default now()
);

-- 프로젝트 멤버십: 역할 + 가입상태가 여기 (계정이 아니라 멤버십에)
create table project_member (
    id           bigint generated always as identity primary key,
    project_id   bigint      not null references project (id),
    account_id   bigint      not null references account (id),
    role         varchar(20) not null,   -- LEADER / CO_LEADER / MEMBER
    status       varchar(20) not null,   -- PENDING / ACTIVE / REJECTED / LEFT / REMOVED
    requested_at timestamptz,
    joined_at    timestamptz,
    left_at      timestamptz,
    unique (project_id, account_id)      -- 같은 계정 중복 가입 불가
);
create index idx_member_project on project_member (project_id);

-- 체크인 요일 (frequency 수만큼)
create table checkin_schedule (
    id          bigint generated always as identity primary key,
    project_id  bigint     not null references project (id),
    day_of_week varchar(3) not null,     -- MON..SUN
    unique (project_id, day_of_week)
);

-- 초대 링크 (재생성 시 기존 is_active=false)
create table invite_link (
    id             bigint generated always as identity primary key,
    project_id     bigint      not null references project (id),
    token          varchar(64) not null unique,
    is_active      boolean     not null default true,
    created_at     timestamptz not null default now(),
    deactivated_at timestamptz
);
create index idx_invite_project on invite_link (project_id);

-- 상위 작업
create table task (
    id               bigint generated always as identity primary key,
    project_id       bigint       not null references project (id),
    created_by       bigint       not null references project_member (id),
    name             varchar(200) not null,
    description      text,
    size             varchar(3),                       -- S/M/L/XL
    deadline         date,
    status           varchar(20)  not null default 'TODO',
    display_progress smallint check (display_progress between 0 and 100),
    cancelled_at     timestamptz,
    created_at       timestamptz  not null default now()
);
create index idx_task_project on task (project_id);

-- 작업 담당자 (M:N). is_active=false 행은 담당 해제 이력으로 남김.
create table task_assignee (
    id            bigint generated always as identity primary key,
    task_id       bigint      not null references task (id),
    member_id     bigint      not null references project_member (id),
    is_active     boolean     not null default true,
    assigned_at   timestamptz not null default now(),
    unassigned_at timestamptz
);
create index idx_assignee_task on task_assignee (task_id);
create index idx_assignee_member on task_assignee (member_id);
-- 같은 작업에 같은 멤버 중복 활성 배정 방지 (부분 유니크)
create unique index uq_active_assignee on task_assignee (task_id, member_id) where is_active;

-- 선행/후행 의존성 (순환 방지는 애플리케이션 레벨)
create table task_dependency (
    id             bigint generated always as identity primary key,
    predecessor_id bigint not null references task (id),
    successor_id   bigint not null references task (id),
    unique (predecessor_id, successor_id),
    check (predecessor_id <> successor_id)
);

-- 하위 작업 (작성자 = 담당 팀원)
create table sub_task (
    id         bigint generated always as identity primary key,
    task_id    bigint       not null references task (id),
    created_by bigint       not null references project_member (id),
    title      varchar(300) not null,
    is_done    boolean      not null default false,
    created_at timestamptz  not null default now()
);
create index idx_subtask_task on sub_task (task_id);

-- 체크인 회차 (누락/연속누락/회차 이력 계산용)
create table checkin_round (
    id             bigint generated always as identity primary key,
    project_id     bigint      not null references project (id),
    scheduled_date date        not null,
    deadline_at    timestamptz not null,
    unique (project_id, scheduled_date)
);

-- 체크인 제출 (회차 x 멤버). 누락 = 행이 없는 상태.
create table checkin_submission (
    id           bigint generated always as identity primary key,
    round_id     bigint      not null references checkin_round (id),
    member_id    bigint      not null references project_member (id),
    is_late      boolean     not null default false,
    submitted_at timestamptz not null default now(),
    updated_at   timestamptz,
    unique (round_id, member_id)
);

-- 체크인 내 작업별 업데이트
create table checkin_task_update (
    id              bigint generated always as identity primary key,
    submission_id   bigint      not null references checkin_submission (id),
    task_id         bigint      not null references task (id),
    status          varchar(20) not null,
    progress        smallint    not null check (progress in (0, 25, 50, 75, 100)),
    blocked_reason  varchar(30),               -- status=BLOCKED 시 필수 (앱 검증)
    request_to_team text,
    next_action     text,
    unique (submission_id, task_id)
);
create index idx_update_submission on checkin_task_update (submission_id);
create index idx_update_task on checkin_task_update (task_id);

-- 산출물 링크 (작성자/팀장만 삭제 가능 - 유일하게 삭제 허용되는 엔티티)
create table output_link (
    id            bigint generated always as identity primary key,
    task_id       bigint      not null references task (id),
    created_by    bigint      not null references project_member (id),
    submission_id bigint references checkin_submission (id),  -- 체크인에서 등록 시
    type          varchar(20) not null,                       -- DOC/SLIDE/DESIGN/CODE/PR/ETC
    url           text        not null,
    title         varchar(300),
    created_at    timestamptz not null default now()
);
create index idx_output_task on output_link (task_id);
