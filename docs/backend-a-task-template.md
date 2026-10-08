# 작업 템플릿 API

JWT Bearer 인증을 사용합니다. 기존 작업 관리 Swagger 그룹에 포함됩니다.

## 목록 조회

`GET /api/projects/{projectId}/tasks/templates`

승인된 팀원은 종료 프로젝트에서도 조회할 수 있습니다. 반환 목록의 각 항목에는
`template`, `name`, `tasks`가 있고, `tasks`에는 작업별 `title`, `size`가 있습니다.
프론트는 이 목록으로 템플릿 선택 및 생성 전 미리보기를 구성할 수 있습니다.

| template | name |
| --- | --- |
| PRESENTATION | 발표 |
| REPORT | 보고서 |
| DEVELOPMENT | 개발 |
| DESIGN | 디자인 |
| RESEARCH | 조사 |

각 템플릿은 작업 5개를 생성합니다. 제목과 크기는 목록 응답을 기준으로 표시합니다.

## 일괄 생성

`POST /api/projects/{projectId}/tasks/from-template`

```json
{
  "template": "PRESENTATION",
  "dueDate": "2026-12-15"
}
```

- 팀장·공동 팀장만 생성할 수 있습니다. DONE/CLOSED 프로젝트는 409입니다.
- `template`은 필수이고, `dueDate`는 생략하거나 null로 보내면 작업 마감일 없이 생성합니다.
- 공통 마감일을 지정하면 생성되는 모든 작업에 적용합니다. 생성 후 개별 작업 설정 API로 수정할 수 있습니다.
- 201 응답은 생성된 `TaskResponse` 배열입니다. TODO, 진행률 0, 담당자 없는 상태입니다.
- 기존 작업은 보존하고, 최대 sortOrder 뒤에 템플릿 순서대로 추가합니다.
- 프로젝트 잠금과 단일 트랜잭션을 사용합니다. 일부만 저장되는 일을 방지합니다.
- 같은 요청을 다시 보내면 작업 5개가 다시 생성됩니다. 자동 재시도나 중복 클릭에 주의합니다.
- 선행 작업·하위 작업·담당자는 자동 생성하지 않습니다. 기존 관리 API로 설정합니다.

## 오류

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 템플릿 누락·잘못된 enum·잘못된 날짜 |
| 401 | UNAUTHORIZED | 인증 없음·잘못된 토큰 |
| 403 | PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED | 미승인·외부 사용자·일반 팀원의 생성 요청 |
| 404 | PROJECT_NOT_FOUND | 프로젝트 없음 |
| 409 | PROJECT_NOT_WRITABLE / TASK_ORDER_LIMIT | 종료 프로젝트·정렬 범위 초과 |

DB 테이블이나 enum 제약 변경 없이 기존 tasks 테이블을 사용합니다.
