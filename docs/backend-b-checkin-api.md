# 체크인 API 계약

Bearer JWT의 subject(사용자 ID)로 현재 프로젝트 소속을 확인한다. 클라이언트가 멤버 ID나 역할을 선택해 대신 제출할 수 없다. 모든 경로는 `/api/projects/{projectId}/checkins` 기준이다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | /current | 현재 회차·대상·현재 정보·내 제출·버튼 가능 여부 |
| POST | /{roundId}/submission | 전체 대상 최초 제출 |
| PUT | /{roundId}/submission | 정시 제출 수정 |
| GET | /{roundId}/submission | 내 제출 당시 스냅샷 |
| GET | /{roundId}/status | 해당 회차 팀 현황·집계 |
| GET | /mine?limit=20&beforeRoundId=123 | 내 기록, 최신 순·커서 페이지 |
| GET | /summary | 프로젝트 전체 회차의 누적 집계 |

## 조회 → 제출 → 재조회

1. `/current`의 `round.id`, 제외 사유가 없는 `targets`, 각 대상의 `version`, `current.issue.revision`을 사용한다.
2. `canSubmit`이면 POST, `canEdit`이면 PUT한다. POST의 `expectedRevision`은 0, PUT은 `submission.revision`이다.
3. 모든 유효 대상을 포함한다. 값이 바뀌지 않아도 제출할 수 있다. 상태·진행률·담당자·산출물을 요청으로 덮어쓰지 않는다.
4. 성공 시 반환된 제출을 표시하거나 GET submission으로 다시 읽는다.

```json
{
  "requestKey": "6bfb1793-e84d-47de-98b0-c8226313c233",
  "expectedRevision": 0,
  "tasks": [
    {
      "taskId": 101,
      "expectedVersion": "조회한 targets[].version의 64자리 값을 그대로 사용",
      "expectedIssueRevision": 0,
      "issueContent": null,
      "requestToTeam": "리뷰 부탁드립니다",
      "nextAction": "API 연결"
    }
  ]
}
```

예시 version 설명 문자열은 실제 요청 값이 아니다. 서버에서 받은 64자리 값을 그대로 사용한다.

- requestKey는 요청마다 새 UUID를 만든다. 네트워크 재시도에는 같은 키와 같은 본문을 사용한다. 같은 키를 다른 요청이나 POST/PUT에 재사용하면 409다.
- 같은 키·본문 재요청은 최초 응답을 반환한다. 성공 후 화면을 새로 편집하면 새 키와 최신 revision을 사용한다.
- issueContent=null은 현재 이슈 유지, 값이 있으면 1~2000자 이슈 보고다. 해결은 별도 PATCH issue/resolve로 즉시 처리한다. 빈 문자열로 해결하지 않는다.
- requestToTeam·nextAction은 각 2000자 이하의 개인 보고 필드다. 선택 사항이며 작업 공통 상태를 수정하지 않는다.
- 작업 또는 이슈가 조회 이후 바뀌면 CHECKIN_TASK_CHANGED 409다. 최신 current를 다시 조회하고 사용자 입력을 유지한 채 재확인한다.
- 이슈 보고와 제출 스냅샷은 한 트랜잭션이다. A API로 이미 저장한 하위 작업·산출물은 체크인 실패로 되돌리지 않는다.

## 응답과 기록

`Current`는 round, serverTime, phase, canSubmit, canEdit, unavailableReason, targets, submission을 반환한다. 회차가 아직 없으면 round/phase/submission=null, targets=[], unavailableReason=NO_ROUND다. 대상이 없거나 퇴장·종료 상태이면 제출 가능 값은 false다.

phase는 NOT_OPEN/ON_TIME/LATE/CLOSED다. 시간은 UTC ISO 8601이며 서버가 판정한다. 연속 요일에는 지각 구간이 없다.

`targets[].current`는 최신 작업 정보다. `submission.tasks`는 제출 당시 정보이며 서로 구분해 표시한다. 스냅샷에는 제목·크기·상태·정수 진행률·마감일·당시 담당자·작업 변경 시각·하위 작업과 당시 배정·산출물·이슈·개인 보고가 포함된다. 수행자 기록이 없는 A 모델에서 하위 작업 담당자를 실제 완료자로 해석하지 않는다.

제출에는 revision, late, submittedAt, updatedAt이 있다. 정시 수정은 최신 스냅샷만 갱신하고 최초 시각·정시 여부를 유지한다. 지각 기간에는 최초 제출만 허용한다. 지각 종료 정각부터 이전 회차를 제출할 수 없다.

`/mine`은 items와 nextBeforeRoundId를 반환한다. nextBeforeRoundId=null이면 끝이다. limit은 1~100이다. 제외된 과거 기록도 개인 이력에는 EXCLUDED로 남고, 이미 제출한 스냅샷은 조회할 수 있다.

팀 현황 status는 ON_TIME/LATE/PENDING/MISSED다. 유효 대상 0명은 팀 표시·집계에서 제외한다. PENDING은 최종 기한이 남은 미제출이며 누락으로 계산하지 않는다. 누적 집계는 제출자 수 합 / 대상자 수 합 ×100, 최종 소수 둘째 자리 반올림이다. 분모 0이면 submissionRate=null이다.

## 오류

기존 ApiError(code,message,fieldErrors) 형식을 사용한다. Swagger에도 공통 오류가 등록되어 있다.

| HTTP | 코드 |
| --- | --- |
| 400 | INVALID_REQUEST, CHECKIN_INVALID_TASK_IDS, INVALID_ISSUE_CONTENT |
| 401 | UNAUTHORIZED |
| 403 | PROJECT_ACCESS_DENIED, CHECKIN_NOT_ELIGIBLE |
| 404 | PROJECT_NOT_FOUND, CHECKIN_ROUND_NOT_FOUND, CHECKIN_SUBMISSION_NOT_FOUND |
| 409 | PROJECT_NOT_WRITABLE, CHECKIN_NOT_OPEN, CHECKIN_CLOSED, CHECKIN_LATE_EDIT_NOT_ALLOWED, CHECKIN_NO_TARGETS, CHECKIN_TARGETS_CHANGED |
| 409 | CHECKIN_ALREADY_SUBMITTED, CHECKIN_SUBMISSION_REQUIRED, CHECKIN_VERSION_CONFLICT, CHECKIN_TASK_CHANGED, CHECKIN_REQUEST_KEY_REUSED, ISSUE_VERSION_CONFLICT |

리스크 점수·가중치·개인 실제 기여도는 정책/수행자 데이터가 확정되지 않아 제공하지 않는다.
