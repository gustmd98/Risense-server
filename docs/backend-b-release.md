# 백엔드 B PR 및 검증 안내

## PR 순서

| 순서 | 브랜치 | 기능 |
| --- | --- | --- |
| 1 | feat/task-progress | 하위 작업 변경 시 자동 진행률·완료/재개 |
| 2 | feat/task-issues | 현재 이슈 조회·즉시 해결, V3 |
| 3 | feat/checkin-lifecycle | 일정 이력·회차·대상·A 연결·스케줄러, V4 |
| 4 | feat/checkin-api | 전체 제출·정시 수정·스냅샷·충돌·재요청 |
| 5 | feat/checkin-history | 내 기록·현황·누적 집계·프론트 계약 |

앞 브랜치 위에 다음 브랜치를 쌓았다. 순서대로 푸시·PR·수동 머지한다. GitHub에서 squash/rebase 머지한다면 다음 브랜치의 기준을 갱신한 후 PR 차이를 확인한다. main에는 직접 기능 코드를 올리지 않는다. 이전 정책용 feat/checkin-submission은 이번 PR 순서에 사용하지 않는다.

README는 사용자의 기존 요청대로 main에서 갱신한다. 위 기능들이 main에 머지된 뒤 체크인 구현 현황·V3/V4·이 문서 링크를 반영한다. 기능이 아직 없는 원격 main에 완료됐다고 기록하지 않는다.

## 로컬 검증

일반 테스트: `./gradlew test` (DB 통합 테스트는 기본 생략).

실제 PostgreSQL 통합 테스트는 버릴 수 있는 별도 DB에서만 실행한다. 실행 환경에 `CHECKIN_TEST_DB=disposable`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`을 제공하고 `./gradlew test --rerun-tasks`를 실행한다. 테스트는 고정 시계와 실제 A/B 서비스·트랜잭션을 사용하며 동시 재전송도 확인한다. 운영/Supabase 연결을 사용하지 않는다.

## 배포 후 확인

1. 원격 main의 마이그레이션 번호·체크섬 충돌 여부 확인.
2. PR 머지 후 이미지 빌드 성공, Oracle의 기존 배포 절차로 이미지 갱신.
3. Flyway V3/V4 성공과 health 확인.
4. Swagger /v3/api-docs에 새 체크인 5개 경로(7개 동작)와 이슈 2개 경로가 등록됐는지 확인.
5. 프론트가 API 계약의 version·revision·requestKey를 사용하도록 연결.

공개 Swagger는 실제 서버에 새 이미지가 배포된 뒤 새 API가 나타난다. 로컬 등록 성공과 공개 서버 배포 완료는 별개다. 프론트 저장소 변경·화면 검증은 이 백엔드 PR에 포함하지 않는다.

## DB 정책

첫 회차는 V4 적용 이후 첫 지정 요일부터다. 기존 legacy 체크인 데이터를 새 대상·누락으로 추정하지 않는다. 적용된 마이그레이션은 편집하지 않는다. 새 테이블을 사용한 뒤 이전 앱 이미지로 돌아가더라도 테이블을 삭제하는 롤백은 하지 않는다.
