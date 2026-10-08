# 인증 API 프론트 전달

## Swagger

앱 실행 후 http://localhost:8080/swagger-ui/index.html 에서 전체 API를 확인한다.
OpenAPI JSON: http://localhost:8080/v3/api-docs
문서 조회는 토큰 없이 가능하다. /api/auth/me는 Bearer 토큰이 필요하다.

1. POST /api/auth/register로 가입한다. 가입 응답에는 토큰이 없다.
2. POST /api/auth/login으로 accessToken을 받는다.
3. Swagger의 Authorize에 accessToken 값만 입력한다(Bearer 문자열 제외).
4. GET /api/auth/me를 실행한다.

로그인 응답의 tokenType은 Bearer, expiresIn은 초 단위(기본 3600)다.
Refresh token과 서버 로그아웃 API는 없다. 로그아웃 시 클라이언트에서 토큰을 제거한다.
토큰 만료에 따른 401이면 다시 로그인하도록 안내한다.

## 배포 전 전달

Swagger 실행 확인 후 PowerShell에서 명세를 내려받는다:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/v3/api-docs' -OutFile 'auth-openapi.json'
```

이 파일은 프론트 담당자가 Swagger Editor 등에 불러와 명세를 확인하고 mock API를
구현하는 데 사용할 수 있다. 명세의 localhost 주소는 각자 컴퓨터를 가리키므로 원격
호출 주소가 아니다. 실제 연동은 접근 가능한 배포 API 주소를 전달한 뒤 진행한다.
DB 비밀번호/JWT_SECRET은 전달하지 않는다. Swagger에 실제 계정 비밀번호나 토큰을
공유 예제로 저장하지 않는다.

## 화면 처리

- 가입 201: 로그인 화면으로 이동(자동 로그인은 별도 login 호출 필요).
- 가입 409 / EMAIL_ALREADY_EXISTS: 이메일 중복 안내.
- 요청 400 / INVALID_REQUEST: fieldErrors에 있는 필드별 오류 표시.
- 요청 400 / INVALID_PASSWORD: 비밀번호 UTF-8 72바이트 제한 안내.
- 로그인 401 / INVALID_CREDENTIALS: 이메일 또는 비밀번호 확인 안내.
- 내 정보 401 / UNAUTHORIZED: 로그인 필요 안내.
- 서버 오류 5xx/네트워크 실패: 재시도 안내.

이메일은 공백 제거 및 소문자 정규화, 닉네임은 앞뒤 공백 제거 후 저장한다.
현재 UserResponse에는 프로젝트 역할이 없다. 프로젝트별 권한은 팀 기능에서 제공한다.
