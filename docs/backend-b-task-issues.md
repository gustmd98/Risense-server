# 작업 이슈

- GET `/api/projects/{projectId}/tasks/{taskId}/issue`: 승인된 팀원이 현재 이슈 조회.
- PATCH `/api/projects/{projectId}/tasks/{taskId}/issue/resolve`: 관리자 또는 현재 메인 작업 담당자가 즉시 해결.
- 요청: `{"expectedRevision":1}`. 조회한 revision을 보내며 불일치하면 409 ISSUE_VERSION_CONFLICT.
- 아직 이슈가 없으면 open=false, content=null, revision=0을 반환한다.
- 보고는 체크인 제출 서비스에서 같은 트랜잭션으로 TaskIssueService.report를 호출한다. 별도 즉시 보고 API는 추가하지 않는다.
- 내용은 공백 제외 1~2000자이며 이슈 변경은 진행률·작업 상태를 바꾸지 않는다.
- 이슈 해결은 체크인 시간 제한과 독립적이다. 종료 프로젝트·취소 작업의 변경은 차단한다.
- 같은 프로젝트 잠금으로 변경을 직렬화하고 revision으로 오래된 입력의 덮어쓰기를 막는다.
- 기존 보고 내용과 보고자를 유지하며 해결자·시각을 기록한다. 체크인 사본은 별도로 보존한다.
- V3는 새로운 task_issues 테이블만 추가한다. V1/V2와 기존 A 테이블 구조는 변경하지 않는다.
