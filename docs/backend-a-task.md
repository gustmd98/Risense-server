# 작업 CRUD 및 담당자 API

Swagger의 작업 관리 항목, OpenAPI JSON `/v3/api-docs`.
모든 API는 Bearer JWT 필요. 승인된 팀원은 조회 가능하며,
생성·설정 변경·취소·담당자 변경은 팀장·공동 팀장만 가능하다.

기본 경로: `/api/projects/{projectId}/tasks`

| 메서드 | 추가 경로 | 기능 |
| --- | --- | --- |
| POST | 없음 | 작업 생성, 201 |
| GET | 없음 | 작업 목록, sortOrder/id 오름차순 |
| GET | /{taskId} | 상세 |
| PUT | /{taskId} | 전체 기본 설정 수정 |
| POST | /{taskId}/cancel | 작업 취소 |
| PUT | /{taskId}/assignees | 최종 담당자 목록 지정 |
| GET | /{taskId}/assignee-histories | 변경 이력 |

생성 및 수정 예시:

```json
{"title":"발표 자료 작성","size":"M","dueDate":"2026-12-15","sortOrder":0}
```

- title: 공백 제거 후 1~100자. size: S/M/L/XL.
- dueDate는 nullable. 과거 날짜도 허용한다. 프로젝트 마감일과 별개다.
- sortOrder는 0 이상. 생성 시 생략하면 최대 순서 + 1, 수정 시 생략하면 기존 순서를 유지한다.
- 초기 상태는 TODO, 진행률 0. 이 API는 진행률·작업 수행 상태를 직접 수정하지 않는다.
  수행 상태와 진행률은 B 담당자의 체크인 제출 기능에서 변경한다.
- 취소는 CANCELLED/cancelledAt을 기록하며 기존 진행률·완료 시각·담당자·이력을 보존한다.
  다시 취소해도 최초 취소 시각을 유지한다. 취소 작업은 기본 설정·담당자 변경 불가다.
- DONE/CLOSED 프로젝트는 조회 가능하지만 모든 작업 쓰기를 차단한다.
- 다른 프로젝트의 taskId는 TASK_NOT_FOUND. 목록에는 취소된 작업도 포함한다.
- 응답 assignees는 memberId/닉네임/assignedAt이다. userId와 memberId를 혼동하지 않는다.

담당자 변경 예시:

```json
{"memberIds":[3,4]}
```

같은 프로젝트의 APPROVED 멤버만 지정할 수 있다. 중복·null·음수는 불가하다.
빈 배열은 전체 해제다. 변경분만 적용하며 기존 담당자의 assignedAt은 유지한다.
동일 목록 재요청은 이력과 updatedAt을 추가로 변경하지 않는다.
ASSIGN/UNASSIGN 이력에는 대상 멤버, 변경자 멤버, 변경 시간이 저장된다.
팀원 내보내기에도 담당 배정을 자동 해제하고 내보내기 실행자를 변경자로 기록한다.
멤버·작업·이력은 하드 삭제하지 않는다. 현재 담당자 연결만 삭제할 수 있다.

## 구현 경계

새 마이그레이션 없음. 기존 tasks/task_assignees/task_assignee_histories 사용.
모든 쓰기는 프로젝트 잠금·관리자 권한·프로젝트 상태 검사 후 같은 트랜잭션에서 처리한다.
배정·해제와 이력 저장은 원자적이며, 내보내기도 동일 프로젝트 잠금을 사용한다.
목록은 담당자를 일괄 조회하여 작업마다 별도 쿼리하지 않는다.
선행 작업·하위 작업·산출물은 backend-a-task-relations.md를 참고한다.
템플릿 목록 및 일괄 생성은 `backend-a-task-template.md`를 참고한다.

## 오류

400 INVALID_REQUEST / INVALID_ASSIGNEES

401 UNAUTHORIZED

403 PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED

404 PROJECT_NOT_FOUND / TASK_NOT_FOUND

409 PROJECT_NOT_WRITABLE / TASK_CANCELLED / TASK_ORDER_LIMIT

## 로컬 확인

1. 진행 중 프로젝트의 팀장으로 작업 생성(TODO/0).
2. 제목·크기·마감일 수정 후 재조회.
3. APPROVED 멤버 ID를 담당자로 지정 후 이력 ASSIGN 확인.
4. 같은 목록 재요청 후 이력 개수가 증가하지 않는지 확인.
5. 빈 목록으로 해제 후 UNASSIGN 확인.
6. 작업 취소 후 조회 가능, 설정 수정 409 확인.
7. 팀원 내보내기 시 배정 해제 및 변경자 이력 확인.

실제 Supabase 저장·복합 키 변경은 앱 재시작 후 확인한다.
