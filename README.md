# Risense — 팀플 리스크 레이더

Java 21 · Spring Boot 4.0.6 · PostgreSQL/Supabase · Flyway · JPA.

백엔드 A의 DB·엔티티 정리와 회원가입·로그인·Bearer JWT 인증을 구현했다.
프로젝트·팀 권한, 작업 CRUD API는 이후 순차적으로 구현한다.
인증 API와 실행 설정은 [인증 안내](docs/backend-a-auth.md)를 확인한다.

## 데이터 모델

최신 ERD 중 A 영역 11개 테이블을 PostgreSQL과 JPA에 맞췄다.
현재 담당자는 `task_assignees`, 담당자 변경 기록은 `task_assignee_histories`에 보관한다.
프로젝트 체크인 요일은 `project_checkin_days`에 저장한다.
기존 B 체크인 3개 테이블은 참조하는 A 테이블명만 변경해 유지했다.
B의 체크인 판정·배치·리스크·집계 기능은 별도 담당 범위다.

자세한 변경 범위, 검증 상태, B와의 경계는 [1단계 안내](docs/backend-a-step1.md)를 확인한다.
참고용 A ERD는 `docs/backend-a-erd.mysql.sql`, 실행용 PostgreSQL SQL은
`src/main/resources/db/migration/V1__init.sql`이다.

## 실행

Java 21과 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` 환경변수가 필요하다.
Supabase Session Pooler에서 확인한 PostgreSQL JDBC 주소 및 자격증명을
IntelliJ 실행 환경에 설정한다. 비밀번호를 코드나 Git에 넣지 않는다.
`PORT`는 선택이며 기본 8080이다. CORS는 `CORS_ALLOWED_ORIGINS`로 설정한다.

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

앱 시작 시 Flyway가 스키마를 적용하고 Hibernate `ddl-auto=validate`가 검증한다.
`GET /api/health`로 앱 실행 상태를 확인한다.
V1은 아직 예전 V1이 적용되지 않은 빈 개발 DB를 기준으로 교체했다.
이미 적용된 DB에는 덮어 적용하지 않으며 이후 변경은 V2부터 추가한다.

Docker 배포 방식은 팀 협의 대기 중이다. 기존 배포 파일은 이번 단계에서 변경하지 않았다.

## Swagger 인증 명세

앱 실행 후 http://localhost:8080/swagger-ui/index.html 에서 확인한다.
전체 API 명세 JSON은 /v3/api-docs 이며 전달 방법은 docs/auth-swagger.md를 참고한다.
