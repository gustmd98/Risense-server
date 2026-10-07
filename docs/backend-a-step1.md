# 백엔드 A — 1단계: DB·엔티티

이 단계는 DB·도메인 모델 정리이며 회원가입/로그인 등의 API 구현은 다음 단계다.
커밋·스테이징·푸시는 사용자가 직접 진행한다.

## A 모델

최신 ERD 중 A가 사용할 11개 테이블을 PostgreSQL과 JPA에 맞췄다.

- users, projects, project_checkin_days, project_members, invite_links
- tasks, task_assignees, task_prerequisites, sub_tasks, task_artifacts, task_assignee_histories

참고용 원본: `backend-a-erd.mysql.sql`.
실행용 SQL: `src/main/resources/db/migration/V1__init.sql`.
MySQL identity/datetime/boolean 표현을 PostgreSQL identity/timestamptz/boolean으로 바꿨다.
프로젝트·멤버·작업 상태와 담당자 변경 action은 문자열 enum 및 CHECK로 맞췄다.
요일·담당자·선행작업은 ERD 복합 기본 키와 EmbeddedId/MapsId로 매핑했다.
리스크 스냅샷·배치·집계 API는 이번 범위에 포함하지 않는다.

## B와의 경계

기존 B의 checkin_round/checkin_submission/checkin_task_update 테이블과 엔티티는 유지했다.
V1 안에서 이 3개 테이블이 참조하는 A 테이블 이름만 새 이름으로 변경했다.
B의 체크인 로직·상태 판정·리스크 엔진은 구현하지 않았다.
이 레거시 B 테이블은 최신 B ERD로 정리된 상태가 아니므로, B 담당자가 정식 구현할 때
체크인 스키마 전환 마이그레이션을 별도로 작성해야 한다.
task_blockers도 체크인의 막힘 사유 처리와 함께 B 담당자가 연결할 대상으로 남겼다.

프로젝트 설정인 기존 CheckinSchedule은 A 소유의 ProjectCheckinDay로 교체했다.

## 실행 및 검증

Java 21을 사용한다. DB_URL/DB_USERNAME/DB_PASSWORD는 필수 환경변수다.
Supabase Session Pooler의 PostgreSQL JDBC 주소와 사용자명, 비밀번호를 IDE 실행 환경에 설정한다.
기본 비밀번호는 제거했으며 값은 코드에 저장하지 않는다. CORS 설정은 유지한다.

```powershell
.\gradlew.bat clean build
```

이 명령의 기존 HealthControllerTest는 DB 연결이나 ddl-auto 검증까지 확인하지 않는다.
실제 DB 모델 검증은 빈 개발 DB로 애플리케이션을 실행해 Flyway 적용 후
Hibernate ddl-auto=validate가 통과하는지 확인해야 한다.
이미 예전 V1이 적용된 DB에서는 이 변경 V1을 덮어 적용하지 않는다.
Flyway repair/clean이나 DB 삭제는 실행하지 않는다.

이번 에이전트 환경에서는 Gradle 실행기 연결이 제한되어 전체 Gradle 빌드 및
DB 실행 검증은 완료하지 못했다. 캐시된 의존성으로 Java 21 메인·테스트 소스 컴파일은
통과했다. 원본 ERD의 A 11개 테이블·68개 컬럼 타입·필수값·PK·unique·FK 대조도 통과했다.

## 다음 순서

1. 이 단계 빌드·DB 검증
2. 회원가입/로그인·토큰 인증
3. 프로젝트 CRUD·종료 후 쓰기 차단
4. 초대·가입 승인·역할·내보내기·권한 규칙
5. 작업 CRUD·템플릿·담당자·선행작업·하위작업·산출물·변경 이력

종료 후 쓰기 차단, 마지막 팀장 보호, 작업 템플릿은 서비스 계층에서 구현할 규칙이다.
이번 도메인 모델 정리만으로 해당 기능이 구현된 것은 아니다.
