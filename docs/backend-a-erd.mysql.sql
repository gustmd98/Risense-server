-- Backend A subset of the supplied ERD. Reference only; not executable on PostgreSQL.

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
  email VARCHAR(255) NOT NULL COMMENT '이메일',
  password_hash VARCHAR(255) NOT NULL COMMENT '비밀번호 해시',
  nickname VARCHAR(50) NOT NULL COMMENT '닉네임',
  created_at DATETIME NOT NULL COMMENT '가입일시',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) COMMENT '사용자';

CREATE TABLE projects (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '프로젝트 ID',
  title VARCHAR(100) NOT NULL COMMENT '프로젝트명',
  class_name VARCHAR(100) NULL COMMENT '수업명',
  deadline DATE NOT NULL COMMENT '최종 마감일',
  checkin_time TIME NOT NULL COMMENT '체크인 마감 시간',
  checkin_frequency INT NOT NULL COMMENT '주당 체크인 횟수',
  status VARCHAR(20) NOT NULL COMMENT '상태: IN_PROGRESS/DONE/CLOSED',
  created_by BIGINT NOT NULL COMMENT '생성자 사용자 ID',
  closed_at DATETIME NULL COMMENT '종료일시',
  created_at DATETIME NOT NULL COMMENT '생성일시',
  updated_at DATETIME NOT NULL COMMENT '수정일시',
  PRIMARY KEY (id)
) COMMENT '프로젝트';

CREATE TABLE project_checkin_days (
  project_id BIGINT NOT NULL COMMENT '프로젝트 ID',
  day_of_week VARCHAR(3) NOT NULL COMMENT '요일: MON~SUN',
  PRIMARY KEY (project_id, day_of_week)
) COMMENT '프로젝트 체크인 요일';

CREATE TABLE project_members (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '팀원 ID',
  project_id BIGINT NOT NULL COMMENT '프로젝트 ID',
  user_id BIGINT NOT NULL COMMENT '사용자 ID',
  role VARCHAR(20) NOT NULL COMMENT '역할: LEADER/CO_LEADER/MEMBER',
  join_status VARCHAR(20) NOT NULL COMMENT '가입 상태: PENDING/APPROVED/REJECTED/REMOVED',
  requested_at DATETIME NOT NULL COMMENT '가입 요청일시',
  joined_at DATETIME NULL COMMENT '승인일시',
  removed_at DATETIME NULL COMMENT '내보낸 일시',
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_members (project_id, user_id)
) COMMENT '프로젝트 팀원';

CREATE TABLE invite_links (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '초대 링크 ID',
  project_id BIGINT NOT NULL COMMENT '프로젝트 ID',
  token VARCHAR(64) NOT NULL COMMENT '초대 토큰',
  created_by BIGINT NOT NULL COMMENT '발급한 팀원 ID',
  expires_at DATETIME NULL COMMENT '만료일시',
  is_active TINYINT(1) NOT NULL COMMENT '활성 여부',
  created_at DATETIME NOT NULL COMMENT '발급일시',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_links_token (token)
) COMMENT '초대 링크';

CREATE TABLE tasks (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '작업 ID',
  project_id BIGINT NOT NULL COMMENT '프로젝트 ID',
  title VARCHAR(100) NOT NULL COMMENT '작업 이름',
  size VARCHAR(2) NOT NULL COMMENT '크기: S/M/L/XL',
  status VARCHAR(20) NOT NULL COMMENT '상태: TODO/IN_PROGRESS/BLOCKED/DONE/CANCELLED',
  progress INT NOT NULL COMMENT '진행률(0~100)',
  due_date DATE NULL COMMENT '작업 마감일',
  sort_order INT NOT NULL COMMENT '정렬 순서',
  cancelled_at DATETIME NULL COMMENT '취소일시',
  completed_at DATETIME NULL COMMENT '완료일시',
  created_at DATETIME NOT NULL COMMENT '생성일시',
  updated_at DATETIME NOT NULL COMMENT '수정일시',
  PRIMARY KEY (id)
) COMMENT '작업';

CREATE TABLE task_assignees (
  task_id BIGINT NOT NULL COMMENT '작업 ID',
  member_id BIGINT NOT NULL COMMENT '담당 팀원 ID',
  assigned_at DATETIME NOT NULL COMMENT '배정일시',
  PRIMARY KEY (task_id, member_id)
) COMMENT '작업 담당자';

CREATE TABLE task_prerequisites (
  task_id BIGINT NOT NULL COMMENT '작업 ID',
  prerequisite_task_id BIGINT NOT NULL COMMENT '선행 작업 ID',
  PRIMARY KEY (task_id, prerequisite_task_id)
) COMMENT '선행 작업 관계';

CREATE TABLE sub_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '하위 작업 ID',
  task_id BIGINT NOT NULL COMMENT '작업 ID',
  title VARCHAR(100) NOT NULL COMMENT '하위 작업 이름',
  completed TINYINT(1) NOT NULL COMMENT '완료 여부',
  assignee_member_id BIGINT NULL COMMENT '담당 팀원 ID',
  sort_order INT NOT NULL COMMENT '정렬 순서',
  PRIMARY KEY (id)
) COMMENT '하위 작업';

CREATE TABLE task_artifacts (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '산출물 ID',
  task_id BIGINT NOT NULL COMMENT '작업 ID',
  title VARCHAR(100) NULL COMMENT '산출물 이름',
  url VARCHAR(500) NOT NULL COMMENT '산출물 링크',
  created_by BIGINT NOT NULL COMMENT '등록한 팀원 ID',
  created_at DATETIME NOT NULL COMMENT '등록일시',
  PRIMARY KEY (id)
) COMMENT '작업 산출물';

CREATE TABLE task_assignee_histories (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '이력 ID',
  task_id BIGINT NOT NULL COMMENT '작업 ID',
  member_id BIGINT NOT NULL COMMENT '대상 팀원 ID',
  action VARCHAR(10) NOT NULL COMMENT '변경: ASSIGN/UNASSIGN',
  changed_by BIGINT NOT NULL COMMENT '변경한 팀원 ID',
  changed_at DATETIME NOT NULL COMMENT '변경일시',
  PRIMARY KEY (id)
) COMMENT '담당자 변경 이력';

ALTER TABLE projects ADD CONSTRAINT fk_projects_user FOREIGN KEY (created_by) REFERENCES users (id);
ALTER TABLE project_checkin_days ADD CONSTRAINT fk_checkin_days_project FOREIGN KEY (project_id) REFERENCES projects (id);
ALTER TABLE project_members ADD CONSTRAINT fk_members_project FOREIGN KEY (project_id) REFERENCES projects (id);
ALTER TABLE project_members ADD CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE invite_links ADD CONSTRAINT fk_invite_project FOREIGN KEY (project_id) REFERENCES projects (id);
ALTER TABLE invite_links ADD CONSTRAINT fk_invite_member FOREIGN KEY (created_by) REFERENCES project_members (id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id);
ALTER TABLE task_assignees ADD CONSTRAINT fk_assignees_task FOREIGN KEY (task_id) REFERENCES tasks (id);
ALTER TABLE task_assignees ADD CONSTRAINT fk_assignees_member FOREIGN KEY (member_id) REFERENCES project_members (id);
ALTER TABLE task_prerequisites ADD CONSTRAINT fk_prereq_task FOREIGN KEY (task_id) REFERENCES tasks (id);
ALTER TABLE task_prerequisites ADD CONSTRAINT fk_prereq_prereq FOREIGN KEY (prerequisite_task_id) REFERENCES tasks (id);
ALTER TABLE sub_tasks ADD CONSTRAINT fk_subtasks_task FOREIGN KEY (task_id) REFERENCES tasks (id);
ALTER TABLE sub_tasks ADD CONSTRAINT fk_subtasks_member FOREIGN KEY (assignee_member_id) REFERENCES project_members (id);
ALTER TABLE task_artifacts ADD CONSTRAINT fk_artifacts_task FOREIGN KEY (task_id) REFERENCES tasks (id);
ALTER TABLE task_artifacts ADD CONSTRAINT fk_artifacts_member FOREIGN KEY (created_by) REFERENCES project_members (id);
ALTER TABLE task_assignee_histories ADD CONSTRAINT fk_hist_task FOREIGN KEY (task_id) REFERENCES tasks (id);
ALTER TABLE task_assignee_histories ADD CONSTRAINT fk_hist_member FOREIGN KEY (member_id) REFERENCES project_members (id);
ALTER TABLE task_assignee_histories ADD CONSTRAINT fk_hist_changer FOREIGN KEY (changed_by) REFERENCES project_members (id);
