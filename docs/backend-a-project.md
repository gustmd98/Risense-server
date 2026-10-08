# 프로젝트 API

Swagger의 projects 그룹 또는 `/v3/api-docs/projects`에서 확인한다.
모든 프로젝트 API는 Bearer JWT가 필요하다.

| 메서드 | 경로 | 접근 | 결과 |
| --- | --- | --- | --- |
| POST | /api/projects | 로그인 사용자 | 201, 생성자를 승인된 LEADER로 자동 등록 |
| GET | /api/projects | 로그인 사용자 | 승인된 멤버십의 프로젝트 배열, 생성일 내림차순 |
| GET | /api/projects/{id} | 승인된 멤버 | 프로젝트 상세 |
| PUT | /api/projects/{id} | LEADER/CO_LEADER | 전체 설정 수정 |
| POST | /api/projects/{id}/close | LEADER/CO_LEADER | CLOSED로 종료 |

생성/수정 요청 예시:

```json
{
  "title": "팀플 프로젝트",
  "className": "소프트웨어공학",
  "deadline": "2026-12-20",
  "checkinTime": "23:00:00",
  "checkinFrequency": 2,
  "checkinDays": ["MON", "WED"]
}
```

- title: 앞뒤 공백 제거 후 1~100자.
- className: nullable, 최대 100자. PUT에서 null을 보내면 비운다.
- deadline: 필수 날짜. 과거 날짜도 허용하며 상태를 자동으로 바꾸지 않는다.
- checkinTime: 현지 시간, 시간대 오프셋 없음. DB에는 마이크로초 정밀도로 저장한다.
  실제 체크인 회차 생성과 시간대 적용은 B 담당 범위다.
- checkinFrequency: 1~7. checkinDays의 개수와 일치해야 한다.
- checkinDays: MON/TUE/WED/THU/FRI/SAT/SUN, 중복 및 null 불가.

응답에는 id, 위 설정들, status, myRole, closedAt, createdAt, updatedAt이 포함된다.
요일은 월요일부터 일요일 순으로 반환한다. 멤버십이 없으면 목록은 빈 배열이다.
PENDING/REJECTED/REMOVED 멤버십은 목록 및 상세 조회에서 제외한다.
DONE/CLOSED도 승인된 멤버는 조회할 수 있으나 설정을 수정할 수 없다.
종료 요청을 재시도하면 기존 closedAt을 유지한다. 재개/삭제 API는 제공하지 않는다.

오류: 400 INVALID_REQUEST / INVALID_CHECKIN_SCHEDULE, 401 UNAUTHORIZED,
403 PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED,
404 PROJECT_NOT_FOUND, 409 PROJECT_NOT_WRITABLE.

## B 및 후속 A 기능 연결

이번 단계에는 DB 마이그레이션이 없다. 기존 V1 프로젝트 테이블을 사용한다.
프로젝트 생성·팀장 등록·요일 저장은 하나의 트랜잭션이다.
설정 수정/종료는 프로젝트 행에 PESSIMISTIC_WRITE 잠금을 건다.
앞으로 추가하는 팀/작업 쓰기도 같은 트랜잭션 안에서
ProjectAccess.lockedProject → 권한 검사 → requireWritable 순으로 처리해야
종료와의 동시 실행을 안전하게 차단할 수 있다.
B 담당자는 설정 변경 이후의 회차 생성 정책을 별도로 구현한다.

## 로컬 확인 순서

1. clean build 후 앱 재시작.
2. Swagger에서 로그인 및 Authorize.
3. 프로젝트 생성: IN_PROGRESS / myRole LEADER 확인.
4. 목록 및 상세 확인, 요일/횟수 변경 후 재조회.
5. 중복 요일이나 횟수 불일치: 400 확인.
6. 다른 계정으로 상세 조회: 403 확인.
7. 종료 후 상세 조회 가능, 설정 수정은 409 확인.
8. 종료 재호출 후 closedAt 유지 확인.

권한·입력 검증·상태 전이 테스트는 서비스 및 MVC 테스트로 작성했다.
실제 PostgreSQL 복합 키 저장과 잠금은 Supabase 실행으로 별도 확인한다.
