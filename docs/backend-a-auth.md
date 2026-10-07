# 백엔드 A — 회원가입·로그인·토큰 인증

구현 API는 회원가입, 로그인, 현재 사용자 조회다.
인증 프레임워크는 Spring Security의 JWT Resource Server를 사용한다.
비밀번호는 BCrypt(cost 12)로 해시하며 원문을 저장하거나 응답하지 않는다.

## 실행 설정

기존 DB_URL/DB_USERNAME/DB_PASSWORD와 Java 21 설정을 유지하고
IntelliJ Run → Edit Configurations → 실행 중인 RiskRadarApplication 설정에
JWT_SECRET 환경변수를 추가한다. JWT_SECRET은 32바이트 이상 난수를 Base64로 인코딩한 값이다.
Supabase 비밀번호나 Supabase의 JWT 키를 이 값에 재사용하지 않는다.

IntelliJ Terminal에서 다음 명령으로 직접 생성할 수 있다.

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

출력값은 실행 설정에만 입력하고 Git/채팅에 올리지 않는다.
실행 설정의 Store as project file은 체크하지 않는다.
배포 환경도 별도 secret으로 주입하며 서명 키는 앱 재시작 시 유지한다.

이번 변경의 V2는 이메일에 lower(btrim(email)) unique index를 추가한다.
이미 Supabase에 적용된 V1은 변경하지 않는다.
서버 시작 시 Flyway가 V2를 적용한 뒤 JPA validate를 실행한다.
기존 이메일 중 대소문자/주변 공백만 다른 중복 데이터가 있다면 V2 적용이 실패한다.
그때는 데이터를 확인해 정리해야 하며 Flyway repair/clean으로 우회하지 않는다.

## API

| 메서드 | URL | 인증 | 성공 |
|---|---|---|---|
| POST | /api/auth/register | 없음 | 201, 사용자 정보 |
| POST | /api/auth/login | 없음 | 200, Bearer 토큰·사용자 정보 |
| GET | /api/auth/me | Bearer 토큰 | 200, 사용자 정보 |
| GET | /api/health | 없음 | 200, UP |

나머지 URL은 인증을 요구한다. 프로젝트 역할/권한 검증은 다음 구현 단계에서 추가한다.

회원가입 요청:

```json
{"email":"test@example.com","password":"test-password-123","nickname":"테스터"}
```

이메일은 앞뒤 공백을 제거하고 소문자로 정규화한다. 최대 255자다.
닉네임은 앞뒤 공백을 제거하고 1~50자다.
가입 비밀번호는 8자 이상이고 UTF-8 기준 72바이트 이하다.
멀티바이트 비밀번호를 BCrypt 한계에 맞춰 조용히 잘라내지 않고 400으로 거부한다.
동시 가입의 이메일 중복도 DB unique 제약을 통해 409로 처리한다.

사용자 응답:

```json
{"id":1,"email":"test@example.com","nickname":"테스터","createdAt":"2026-10-07T08:00:00Z"}
```

로그인 요청:

```json
{"email":"test@example.com","password":"test-password-123"}
```

로그인 응답:

```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":3600,"user":{"id":1,"email":"test@example.com","nickname":"테스터","createdAt":"2026-10-07T08:00:00Z"}}
```

현재 사용자 조회 헤더:

```text
Authorization: Bearer <로그인 응답의 accessToken>
```

서명 알고리즘은 HS256이며 발급자·만료·사용자 ID·access 토큰 종류를 검증한다.
토큰에는 사용자 ID만 식별 정보로 포함하며 비밀번호나 프로젝트 역할은 넣지 않는다.
수명은 1시간이고 만료 후 다시 로그인한다. refresh token 및 서버측 토큰 폐기는 이번 단계 범위가 아니다.
현재 사용자가 삭제된 경우 /me는 401을 반환한다.

## 에러

```json
{"code":"EMAIL_ALREADY_EXISTS","message":"이미 사용 중인 이메일입니다.","fieldErrors":{}}
```

| HTTP | code | 의미 |
|---|---|---|
| 400 | INVALID_REQUEST | 요청 JSON 또는 이메일·비밀번호·닉네임 검증 실패 |
| 400 | INVALID_PASSWORD | BCrypt의 72바이트 한계 초과 |
| 409 | EMAIL_ALREADY_EXISTS | 이메일 중복 |
| 401 | INVALID_CREDENTIALS | 이메일 없음 또는 비밀번호 불일치 (동일 응답) |
| 401 | UNAUTHORIZED | 토큰 없음·변조·만료·잘못된 claim 또는 삭제된 사용자 |
| 403 | FORBIDDEN | 접근 권한 없음 |

## 테스트 및 확인 순서

```powershell
.\gradlew.bat clean build
```

AuthHttpTest는 실제 MVC 검증과 BCrypt, JWT 발급·검증 및 SecurityFilterChain을 사용한다.
DB 호출만 mock으로 대체해 Supabase 변경 없이 회원가입·중복·입력 오류·로그인 실패·/me·
토큰 변조/만료/발급자/사용자 ID·삭제 사용자·CORS preflight·JSON 오류를 검증한다.
AuthRaceTest는 동시 중복 및 다른 DB 오류 구분을, JwtConfigTest는 서명 키 형식을 검증한다.
이 테스트는 실제 PostgreSQL에 V2를 적용하는 테스트는 아니다.

빌드 후 JWT_SECRET을 추가하고 서버를 재시작한다.
V2 적용과 Started RiskRadarApplication 로그를 확인한 다음 아래 PowerShell 예제로 테스트한다.

```powershell
$body = @{ email='test@example.com'; password='test-password-123'; nickname='테스터' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/register' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$body = @{ email='test@example.com'; password='test-password-123' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/me' -Headers @{ Authorization="Bearer $($login.accessToken)" }
```

이 예제는 실행한 DB에 테스트 사용자 1명을 만든다. 같은 이메일의 재가입은 409가 정상이다.
커밋·스테이징·푸시는 사용자가 직접 진행한다.
