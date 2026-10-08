# 팀 및 초대 API

Swagger의 team 그룹, JSON `/v3/api-docs/team`에서 확인한다.
초대 링크 검증 GET 한 개를 제외하면 모든 API는 Bearer JWT가 필요하다.
프로젝트 ID와 멤버 ID는 다르다. 역할 변경·승인·내보내기에는 MemberResponse.id를 사용한다.

## API

| 메서드 | 경로 | 권한/동작 |
| --- | --- | --- |
| POST | /api/projects/{projectId}/invite-links | 팀장·공동 팀장, 201 |
| GET | /api/projects/{projectId}/invite-links/current | 팀장·공동 팀장, 현재 링크 |
| DELETE | /api/projects/{projectId}/invite-links/{inviteId} | 팀장·공동 팀장, 비활성화 204 |
| GET | /api/invites/{token} | 공개, 유효성·프로젝트 이름·만료 시간 |
| POST | /api/invites/{token}/join | 로그인 사용자, 가입 요청 200 |
| GET | /api/projects/{projectId}/membership | 본인 가입 상태(승인 전에도 가능) |
| GET | /api/projects/{projectId}/members | 승인된 팀원, 승인된 멤버 목록 |
| GET | /api/projects/{projectId}/join-requests | 팀장·공동 팀장, PENDING 목록 |
| POST | /api/projects/{projectId}/members/{memberId}/approve | 팀장·공동 팀장, 승인 |
| POST | /api/projects/{projectId}/members/{memberId}/reject | 팀장·공동 팀장, 거절 |
| PATCH | /api/projects/{projectId}/members/{memberId}/role | 팀장만 역할 변경 |
| DELETE | /api/projects/{projectId}/members/{memberId} | 팀장 또는 공동 팀장, 내보내기 |

링크 발급 요청: `{"expiresInHours":168}`. 생략 또는 빈 객체는 168시간,
범위는 1~720시간이다. 응답 token으로 프론트의 초대 화면 URL을 구성한다.
프론트 초대 라우트는 여기서 임의로 확정하지 않는다.
토큰은 32바이트 난수를 64자리 hex로 인코딩하며 예측 불가능하다.
재발급하면 기존 활성 링크를 비활성화한다. 만료 시각과 현재 시각이 같아도 만료다.
링크 발급자의 내보내기 시 해당 발급자의 활성 링크도 비활성화한다.

역할 변경 요청: `{"role":"CO_LEADER"}`. 값은 LEADER/CO_LEADER/MEMBER.
공동 팀장은 가입 승인·거절·초대 관리 및 일반 팀원 내보내기를 할 수 있으나,
역할 변경이나 팀장·공동 팀장 내보내기는 팀장만 할 수 있다.

## 상태와 보호 규칙

- 신규 가입은 MEMBER/PENDING. 승인 전에는 팀원 목록·프로젝트 상세에 접근하지 못한다.
- PENDING/APPROVED의 반복 가입 요청은 기존 상태·역할·시각을 유지한다.
- REJECTED는 다시 신청하면 PENDING으로 전환한다. REMOVED는 재신청 불가다.
- 승인·거절은 PENDING에서만 가능하다. 같은 결과 재요청은 멱등 처리한다.
- 역할 변경·내보내기는 APPROVED 대상으로만 가능하다.
- 마지막 승인된 LEADER는 강등·내보내기 불가다. 본인을 대상으로 해도 동일하다.
- 내보내기는 행 삭제 없이 REMOVED 및 removedAt 기록. joinedAt과 외래 키 참조를 보존한다.
- DONE/CLOSED에서 초대 발급·취소·가입·승인·거절·역할 변경·내보내기를 차단한다.
- 모든 쓰기는 프로젝트 행 잠금 후 처리한다. 마지막 팀장 수 검사와 상태 변경을
  같은 트랜잭션에 두어 동시 요청으로 최소 팀장 규칙이 깨지지 않도록 한다.
- 가입 요청은 토큰으로 프로젝트 ID만 조회한 뒤 프로젝트 잠금을 잡고 토큰 유효성을
  다시 읽는다. 링크 재발급·비활성화·종료와의 동시 처리에서도 같은 잠금을 사용한다.

## 오류

400 INVALID_REQUEST / INVALID_INVITE_EXPIRY

401 UNAUTHORIZED

403 PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED / LEADER_REQUIRED / MEMBER_REMOVED

404 PROJECT_NOT_FOUND / MEMBER_NOT_FOUND / INVITE_NOT_FOUND

409 PROJECT_NOT_WRITABLE / INVALID_MEMBER_STATUS / LAST_LEADER

410 INVITE_UNAVAILABLE

새 DB 마이그레이션은 없다. 기존 invite_links/project_members를 사용한다.
팀원 응답은 이메일·비밀번호를 포함하지 않는다. 초대 토큰을 공용 로그·스크린샷에 남기지 않는다.
회원 계정 생성·JWT 발급과 프로젝트 API는 변경하지 않는다.
팀원 내보내기 시 현재 작업 배정을 자동 해제하며, 내보내기 실행자를 변경자로
UNASSIGN 이력을 기록한다. 멤버·작업 자체와 기존 이력은 보존한다.

## 로컬 확인

1. 진행 중 프로젝트를 생성한 계정으로 링크 발급. 이전 테스트 프로젝트가 CLOSED이면 새로 생성한다.
2. 별도 계정으로 링크 검증 → 가입 요청 → membership의 PENDING 확인.
3. 팀장으로 join-requests → 해당 MemberResponse.id로 승인.
4. 새 계정으로 프로젝트 상세 및 팀원 목록 조회.
5. 팀장으로 역할 변경, 마지막 팀장 강등 409 확인.
6. 팀원을 내보낸 후 재가입 403 확인.
7. 링크 재발급 후 이전 토큰 검증 410 확인.

자동 테스트는 권한·상태·만료 경계·마지막 팀장·HTTP 인증·입력 검증을 확인한다.
실제 Supabase 저장과 프로젝트 잠금 실행은 로컬 앱 실행으로 확인한다.
