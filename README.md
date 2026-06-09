# 팀플 리스크 레이더 (Teamplay Risk Radar)

대학생 팀 프로젝트의 위험 신호(체크인 누락·일정 지연·미배정 작업·역할 편중·병목·산출물 근거 부족)를
팀장이 조기에 확인하도록 돕는 웹 서비스의 백엔드.

## 스택

- Java 21 (LTS)
- Spring Boot 4.0.6 (Web, Data JPA, Validation)
- PostgreSQL 16
- Flyway (스키마 마이그레이션)
- Gradle (wrapper 포함)
- Lombok

## 빠른 시작 (로컬)

```bash
# 1) Postgres 띄우기
docker compose up -d db

# 2) 앱 실행 (Flyway가 V1 마이그레이션 자동 적용)
./gradlew bootRun

# 3) 헬스체크
curl http://localhost:8080/api/health   # {"status":"UP"}
```

기본 접속정보(로컬): db `riskradar` / user `riskradar` / pw `riskradar`, 포트 `5432`.
운영 환경에서는 환경변수로 덮어쓴다: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`.

## 스키마 / 마이그레이션

- 스키마의 주인은 **Flyway** (`src/main/resources/db/migration/V1__init.sql`).
- JPA는 `ddl-auto: validate` 로 엔티티와 스키마 일치만 검증한다.
- 변경 시 `V2__xxx.sql` 처럼 새 마이그레이션을 추가 (기존 파일 수정 금지).

### 핵심 설계

- **역할/가입상태는 `project_member`에** — 한 계정이 프로젝트마다 다른 역할 가능.
- **하드 삭제 없음** — 상태/소프트 플래그로만 관리.
  - `task_assignee.is_active=false` 행은 담당 변경 이력으로 보존.
  - 멤버 탈퇴/내보내기 후에도 `project_member` 행 보존 → 기록에 "탈퇴한 팀원" 표시.
- **체크인 3단 구조** — `checkin_round` → `checkin_submission` → `checkin_task_update`.
  - 누락 = 해당 회차에 submission 행이 없는 상태. 연속 누락/회차 이력 계산 용이.
- **진행률은 계산값** — 하위작업 완료율 + 체크인 평균 보정. `task.display_progress`는 캐시(선택).
- **미배정 = active 담당자가 없는 작업.** 별도 플래그 불필요.
- **리스크 점수는 엔티티가 아님** — 런타임 계산.

## 프로젝트 구조

```
src/main/java/com/teamplay/riskradar/
├── RiskRadarApplication.java
├── api/                 # 컨트롤러 (현재 헬스체크만)
└── domain/
    ├── account/         # Account
    ├── project/         # Project, ProjectStatus, ProjectType
    ├── member/          # ProjectMember, MemberRole, MemberStatus
    ├── invite/          # InviteLink
    ├── task/            # Task, TaskAssignee, TaskDependency, SubTask, TaskStatus, TaskSize
    ├── checkin/         # CheckinSchedule, CheckinRound, CheckinSubmission,
    │                    #   CheckinTaskUpdate, CheckinDay, BlockedReason
    └── output/          # OutputLink, OutputType
src/main/resources/
├── application.yml
└── db/migration/V1__init.sql
```

## 배포

`Dockerfile`(멀티스테이지)이 포함되어 있어 컨테이너 기반 PaaS(Railway/Render/Fly.io 등) 어디든 올릴 수 있다.
필요 환경변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`.

## 현재 범위 / 다음 단계

이 레포는 **데이터 모델(엔티티 + 마이그레이션) + 실행 가능한 골격**까지다.
다음 작업: Repository → Service → Controller(REST API) → 인증/인가 → 리스크 계산 로직.
```

## 깃허브 올리기

```bash
git remote add origin https://github.com/<your-id>/teamplay-risk-radar.git
git branch -M main
git push -u origin main
```
