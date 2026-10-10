# 개발·실행 안내

Java 21 · Spring Boot 4.0.6 · PostgreSQL/Supabase · Flyway · JPA.

## 구현 현황

백엔드 A의 계정·프로젝트·팀·작업 관리 API가 구현되어 있다.

| 영역 | 구현 내용 | 상세 안내 |
| --- | --- | --- |
| 인증 | 회원가입, 로그인, 내 정보 조회, Bearer JWT 인증 | [인증](backend-a-auth.md) |
| 프로젝트 | 생성·목록·상세·설정 수정, 종료 및 종료 후 쓰기 차단 | [프로젝트](backend-a-project.md) |
| 팀 | 초대 링크 발급·조회·폐기, 가입 요청·승인·거절, 역할 변경·내보내기, 권한 검사 | [팀](backend-a-team.md) |
| 작업 | 생성·조회·수정·취소, 담당자 설정 및 변경 이력 | [작업](backend-a-task.md) |
| 작업 관계 | 선행 작업 설정, 하위 작업 CRUD·완료 변경, 산출물 링크 CRUD | [작업 관계](backend-a-task-relations.md) |
| 템플릿 | 템플릿 조회 및 템플릿 기반 작업 일괄 생성 | [템플릿](backend-a-task-template.md) |

백엔드 B의 구현은 아래 기능 브랜치로 준비했다. 기능별 PR 머지와 실제 서버 배포는 별도로 진행한다.
실행 가능한 기능은 현재 체크아웃한 브랜치와 서버에 배포된 이미지 기준으로 확인한다.

| 순서 | 기능 브랜치 | 구현 내용 |
| --- | --- | --- |
| 1 | `feat/task-progress` | 하위 작업 기반 진행률 자동 계산, 완료·재개 |
| 2 | `feat/task-issues` | 현재 이슈 조회·즉시 해결, revision 충돌 검사 |
| 3 | `feat/checkin-lifecycle` | KST 자동 회차, 일정 이력, 당시 대상, 담당 변경·내보내기·종료 연결 |
| 4 | `feat/checkin-api` | 현재 조회·전체 제출·정시 수정·스냅샷·재요청 처리 |
| 5 | `feat/checkin-history` | 내 기록·팀 현황·지각·누락·누적 집계·통합 검증 |

2026-10-10 최종 통합 브랜치에서 임시 PostgreSQL을 포함한 171개 테스트와 실제 JWT HTTP 흐름을 확인했다.
리스크 점수·가중치는 정책 확정 후 별도로 진행한다. 현재 모델에 없는 실제 완료자 정보로 개인 기여도를 추정하지 않는다.

## 로컬 개발 환경

### IntelliJ IDEA

1. 저장소를 Clone하고 Gradle 프로젝트로 연다.
2. 프로젝트 SDK와 Gradle JVM을 Java 21로 설정한다.
3. Gradle 배포는 저장소의 Wrapper를 사용한다.
4. `RiskRadarApplication` 실행 구성에 아래 환경변수를 설정한다.

| 변수 | 용도 | 기본값 |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC 연결 주소 | 필수 |
| `DB_USERNAME` | DB 사용자 이름 | 필수 |
| `DB_PASSWORD` | DB 비밀번호 | 필수 |
| `JWT_SECRET` | 32바이트 이상 난수를 Base64로 인코딩한 서명 키 | 필수 |
| `PORT` | 서버 포트 | `8080` |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트 Origin | `http://localhost:5173` |

Supabase를 사용할 때는 Session Pooler의 JDBC 주소와 자격증명을 확인한다.
개발용 DB를 사용하고, 비밀번호와 JWT 키는 Git에 저장하지 않는다.
IntelliJ 실행 구성은 `Store as project file`을 선택하지 않는다.
`.env` 파일만 생성해도 IntelliJ나 `bootRun`에 자동으로 적용되는 것은 아니므로,
IDE 실행 구성 또는 명령을 실행하는 셸 환경에 변수를 제공한다.

### 빌드 및 실행

저장소 루트에서 실행한다. 서버 실행에는 위 환경변수와 접근 가능한 DB가 필요하다.

macOS / Linux:

```sh
./gradlew clean build
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

앱 시작 시 Flyway가 마이그레이션을 적용하고 Hibernate의 `ddl-auto=validate`가 스키마를 검증한다.
실행 후 [상태 확인 API](http://localhost:8080/api/health)에서 응답을 확인한다.

B 브랜치의 일반 `./gradlew test`는 DB 통합 검증을 생략한다.
일회용 PostgreSQL에만 DB 환경변수와 `CHECKIN_TEST_DB=disposable`을 설정하고
`./gradlew test --rerun-tasks`를 실행해 전체 검증한다. 운영·공유 DB를 사용하지 않는다.
자동 회차 스캔은 기본 60초이며 `app.checkin.scheduler-enabled=false`로 비활성화할 수 있다.


## API 문서

앱 실행 후 다음 주소에서 확인한다.

- [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

로그인으로 받은 `accessToken`을 Swagger의 Authorize에 입력해 인증이 필요한 API를 호출한다.
요청 헤더 형식은 `Authorization: Bearer <accessToken>`이다.
명세 전달 방법은 [Swagger 안내](auth-swagger.md)를 참고한다.
이전에 전달한 인증 전용 JSON보다 실행 중인 서버의 전체 명세를 우선 확인한다.

B 체크인 경로는 `/api/projects/{projectId}/checkins` 기준이다.

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/current` | 현재 회차·대상·제출 가능 여부 |
| POST / PUT / GET | `/{roundId}/submission` | 최초 제출·정시 수정·내 제출 당시 정보 |
| GET | `/{roundId}/status` | 회차별 팀 현황 |
| GET | `/mine` | 내 기록 커서 페이지 |
| GET | `/summary` | 누적 체크인율 |

이슈 조회는 `GET /api/projects/{projectId}/tasks/{taskId}/issue`, 즉시 해결은 같은 경로의 `PATCH /resolve`다.
제출에는 전체 대상, 조회한 작업 버전, 제출 revision, 재전송용 requestKey가 필요하다.
현재 작업과 제출 당시 스냅샷을 구분하며 제출 기한이 남은 미제출을 누락으로 계산하지 않는다.

최종 B 브랜치의 `docs/backend-b-checkin-api.md`에 API 계약,
`docs/backend-b-checkin-lifecycle.md`에 PostgreSQL ERD,
`docs/backend-b-prs.md`에 PR 제목·본문, `docs/backend-b-verification.md`에 검증 결과를 정리했다.
공개 Swagger는 main 머지 이후 실제 서버 이미지를 갱신해야 새 API가 나타난다.


## 데이터 모델과 마이그레이션

현재 담당자는 `task_assignees`, 담당자 변경 기록은 `task_assignee_histories`,
프로젝트 체크인 요일은 `project_checkin_days`에 저장한다.
B는 기존 단수형 legacy 체크인 테이블을 보존하고 새 회차·대상·제출 테이블을 사용한다.

실행용 PostgreSQL 마이그레이션은 `src/main/resources/db/migration`에 있다.

- `V1__init.sql`: 초기 스키마
- `V2__unique_normalized_user_email.sql`: 정규화된 이메일의 유일성 인덱스

이미 적용된 V1/V2는 수정하지 않는다. 스키마 변경은 팀과 번호를 조율한 새 마이그레이션으로 추가한다.
B 기능 브랜치에는 다음 마이그레이션을 추가했다.

- `V3__task_issues.sql`: 현재 작업 이슈
- `V4__checkin_lifecycle.sql`: 일정 이력·회차·참여자·대상·제출 스냅샷·재요청 기록

배포 전 원격 마이그레이션 번호와 중복 여부를 다시 확인한다.
첫 회차는 V4 적용 시점과 프로젝트 생성 시점 중 늦은 시점 이후 첫 지정 요일부터 생성한다.
기존 과거 대상을 추정하거나 배포 전 의무·누락을 소급 생성하지 않는다.
[참고용 MySQL ERD](backend-a-erd.mysql.sql)는 PostgreSQL 서버에 실행하지 않는다.
초기 모델 정리 배경은 [1단계 안내](backend-a-step1.md)를 참고한다.
기능별 문서에는 작성 당시의 단계별 설명과 검증 기록이 포함되어 있다.

## Docker 배포

GitHub Actions는 `main` 대상 PR에서 빌드·테스트를 수행한다.
`main` 푸시 시 빌드·테스트 후 `amd64`/`arm64` 이미지를 Docker Hub에 게시한다.
이미지 태그는 `latest`와 `sha-<전체 커밋 SHA>`다.

현재 `docker-compose.yml`은 게시된 백엔드 이미지를 실행하며 DB 컨테이너는 포함하지 않는다.
DB는 외부 PostgreSQL/Supabase에 연결한다.
Oracle 서버에서 이미지를 가져와 실행하는 절차, 환경변수 및 롤백 방법은
[배포 안내](deployment.md)와 [.env.example](../.env.example)을 참고한다.
Compose 실행 시에는 `DOCKER_IMAGE`와 `CORS_ALLOWED_ORIGINS`도 반드시 설정한다.

이미지 게시와 실제 서버 배포는 별도 단계이며, 현재 워크플로에는 Oracle 자동 배포 단계가 없다.
실제 배포 상태와 공개 API 주소는 서버에서 별도로 확인해야 한다.
