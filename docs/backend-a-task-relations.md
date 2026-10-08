# 선행 작업·하위 작업·산출물

Swagger의 작업 관리 항목, JSON `/v3/api-docs`에 기존 작업 API와 함께 노출한다.
기본 경로는 `/api/projects/{projectId}/tasks/{taskId}`이며 Bearer JWT가 필요하다.
모든 조회는 승인된 팀원만 가능하다.

| 메서드 | 추가 경로 | 쓰기 권한 |
| --- | --- | --- |
| GET / PUT | /prerequisites | 변경은 팀장·공동 팀장 |
| GET / POST | /sub-tasks | 생성은 팀장·공동 팀장 |
| PUT / DELETE | /sub-tasks/{subTaskId} | 팀장·공동 팀장 |
| PATCH | /sub-tasks/{subTaskId}/completion | 관리자 또는 해당 담당자 |
| GET / POST | /artifacts | 등록은 승인된 팀원 |
| PUT / DELETE | /artifacts/{artifactId} | 작성자 또는 관리자 |

생성은 201, 조회·수정은 200, 삭제는 204이다.
DONE/CLOSED 프로젝트와 CANCELLED 작업은 모든 쓰기를 차단하나 조회는 허용한다.
다른 프로젝트의 taskId, 다른 부모 작업의 subTaskId/artifactId는 404다.
모든 쓰기는 기존 작업 API와 같은 프로젝트 행 잠금을 사용한다.

## 선행 작업

```json
{"prerequisiteTaskIds":[1,2]}
```

최종 선행 작업 목록이며 빈 배열은 전체 해제다.
자기 참조·중복·다른 프로젝트·취소된 작업 선택을 허용하지 않는다.
기존 간선을 교체한 후 프로젝트 그래프 전체를 위상 정렬로 검사한다.
직접/간접 순환은 409 TASK_DEPENDENCY_CYCLE이며 변경을 저장하지 않는다.
같은 목록 재요청은 기존 복합 키와 updatedAt을 유지한다.
선행 작업이 이후 취소된 경우 기존 관계는 보존한다. B가 집계 시 취소 상태를 고려한다.
영향받는 작업·병목 계산은 B 담당 범위다.

## 하위 작업

```json
{"title":"자료 조사","assigneeMemberId":3,"sortOrder":0}
```

title 1~100자, 담당자는 같은 프로젝트 APPROVED 멤버 또는 null.
sortOrder는 0 이상, 생성 시 생략하면 끝에 추가하며 수정 시 생략하면 기존 순서를 유지한다.
완료 상태는 생성 시 false이며 별도 completion API에 `{"completed":true}`로 변경한다.
부모 작업 진행률·수행 상태는 여기서 직접 수정하지 않는다. B가 하위 작업 완료율을 집계한다.
팀원 내보내기 시 하위 작업 담당자를 null로 해제하며 제목·완료 상태는 유지한다.
하위 작업 삭제는 해당 행만 제거한다. 부모 작업과 담당자 변경 이력은 보존한다.

## 산출물

```json
{"title":"발표 슬라이드","url":"https://docs.google.com/presentation/d/example"}
```

title은 nullable, 최대 100자. url은 최대 500자의 HTTP/HTTPS 절대 주소다.
javascript/file/ftp/상대 경로 및 사용자 인증정보를 포함한 URL은 400 INVALID_ARTIFACT_URL.
링크 내용은 서버에서 가져오지 않는다. 최초 작성자·작성 시간은 수정 시에도 유지한다.
작성자가 내보내져도 링크와 작성자 참조는 남는다. 관리자가 해당 링크를 수정·삭제할 수 있다.
프론트에서는 링크를 안전하게 표시하고 HTTP/HTTPS 검사를 유지한다.

## 오류

기존 프로젝트·작업 오류 외:

- 400 INVALID_PREREQUISITES / INVALID_ASSIGNEES / INVALID_ARTIFACT_URL / INVALID_REQUEST
- 403 SUBTASK_ASSIGNEE_REQUIRED / ARTIFACT_OWNER_REQUIRED
- 404 SUBTASK_NOT_FOUND / ARTIFACT_NOT_FOUND
- 409 TASK_DEPENDENCY_CYCLE / SUBTASK_ORDER_LIMIT

새 DB 마이그레이션 없음. 기존 task_prerequisites/sub_tasks/task_artifacts를 사용한다.
실제 저장·복합 키 교체·삭제는 Supabase에 연결한 로컬 Swagger로 확인한다.
테스트는 순환·긴 그래프·권한·프로젝트 경계·URL·HTTP 입력 검증을 포함한다.
