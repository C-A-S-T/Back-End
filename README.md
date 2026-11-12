# CAST backend

Java 21 / Spring Boot 4.1.1 / PostgreSQL 17. AI 서버와 백엔드는 별도 프로세스이며 PostgreSQL 데이터베이스 하나를 공유한다.

## 데이터 소유권

| 소유자 | 스키마 | 테이블/역할 |
| --- | --- | --- |
| 백엔드 | `backend` | 회원·동의, 질문·조사 작업, 최종 답변·Claim·검증 결과, 인용 근거, 문서 메타데이터 스냅샷, 저장 출처·폴더, AI 작업 전송/수신 기록 |
| AI 서버 | `ai` | 원문 `document`, 전체 `document_chunk`, `retrieval_run`, `retrieval_result`, `evaluation_case` |
| 마이그레이션 | `public` | Flyway 이력. 애플리케이션 업무 테이블은 두 소유 스키마로 이동 |

백엔드에는 AI 청크/검색/평가의 JPA 엔티티나 처리 서비스가 없다. AI 결과에 필요한 ID는 외부 식별자로만 저장하며 AI 테이블을 조회하지 않는다.
`backend.document`는 사용자에게 보여줄 출처 메타데이터이고 `ai.document`는 수집 원문 관리 데이터다. 둘은 같은 테이블이 아니다.
`Evidence`는 인용 문구·페이지·섹션을 보존하므로 AI 재청킹/데이터 정리가 기존 답변을 바꾸지 않는다.

`backend.ai_job`은 AI 모델용 엔티티가 아니라 백엔드의 전송 대기 및 결과 중복 방지 기록이다.
최종 `Claim`의 정규화 값, `Verification`의 규칙/LLM 판정은 AI가 전달한 결과 스냅샷이며 백엔드가 규칙 검증이나 LLM 추론을 실행한다는 뜻이 아니다.

## 계정과 마이그레이션

| 계정 | 권한 |
| --- | --- |
| `cast_app` | `backend` 스키마의 조회/추가/수정/삭제. AI 스키마와 DDL 접근 불가 |
| `cast_ai` | `ai` 스키마의 조회/추가/수정/삭제. 사용자/작업/결과 테이블과 DDL 접근 불가 |
| `cast_migrator` | DB 및 스키마 소유자. 양쪽 마이그레이션을 실행 |
| Docker 관리자 | 최초 계정 생성과 기존 소유권 이전용 |

런타임 계정은 테이블 소유자가 아니고 서로의 역할을 상속하지 않는다. 스키마 간 FK도 없다.
AI 서버는 자기 스키마 테이블을 위한 모델을 별도 구현한다. 이 저장소의 중앙 Flyway 경로가 양쪽 DDL의 단일 관리 경로이며 AI ORM의 자동 DDL 생성은 끈다.
새 테이블은 마이그레이션 역할로 생성해야 기본 권한 부여가 적용된다.

V1~V5는 변경하지 않았다. V6에서 기존 테이블을 이동하고 사용자 인용/출처를 복사하며, V7에서 AI 작업 전달 기록을 추가한다.
기존 ID와 데이터는 보존한다. `backend.document.document_id`와 `ai_document_id`는 이후 서로 다른 값일 수 있다.
스키마 변경 동안 구버전 프로세스의 쓰기를 중단해야 한다. 이번 작업은 개발 DB에 마이그레이션을 직접 실행하지 않는다.

## 로컬 실행: 새 DB

1. Docker 엔진을 실행한다.
2. `.env.example`을 `.env`로 복사하고 관리자/백엔드/AI/마이그레이션 비밀번호를 각각 설정한다.
3. `docker compose up -d`로 DB를 시작한다. 최초 초기화 스크립트가 역할과 권한을 준비한다.
4. Spring 실행 환경에 `SPRING_PROFILES_ACTIVE=local`, `CAST_DB_PASSWORD`, `CAST_MIGRATION_DB_PASSWORD`, `CAST_JWT_SECRET_BASE64`를 설정한다. JWT 키 생성 방법은 아래 인증 항목을 참고한다.
5. `.\mvnw.cmd spring-boot:run`으로 실행한다.

Compose는 `.env`를 읽지만 Spring은 자동으로 읽지 않는다. 앱 환경변수는 IDE나 실행 프로세스에 별도로 설정한다.
기본 주소는 `jdbc:postgresql://127.0.0.1:5433/cast_dev`이며 `DB_URL`로 변경할 수 있다.
기본 프로필은 없고, `open-in-view: false`, `ddl-auto: validate`를 유지한다.
Flyway 연결은 `cast_migrator`, JPA/Hikari 연결은 `cast_app`을 사용한다.
운영에서 마이그레이션을 별도 배포 작업으로 실행할 경우 앱에는 런타임 비밀번호만 제공하고 `spring.flyway.enabled=false`로 실행한다.

## 기존 로컬 볼륨 전환

초기화 스크립트는 기존 볼륨에서 자동 재실행되지 않는다. 데이터를 지우지 않고 다음 순서로 전환한다.

1. DB를 백업하고 백엔드/AI 쓰기 프로세스를 중단한다.
2. `.env`에 추가된 AI/마이그레이션 비밀번호를 설정한다.
3. `docker compose up -d postgres`로 컨테이너 환경변수를 갱신한다. 볼륨은 유지한다.
4. `docker compose exec postgres sh /docker-entrypoint-initdb.d/01-create-app-user.sh`로 역할을 준비한다.
5. 새 마이그레이션 자격 증명으로 백엔드를 시작하여 V6/V7을 적용한 뒤 AI를 시작한다.

스크립트는 지정한 CAST DB에서 알려진 CAST 테이블 중 `cast_app` 소유 테이블을 `cast_migrator`로 이전한다.
과거 런타임 소유권을 제거하지 않으면 GRANT/REVOKE만으로 격리할 수 없기 때문이다.
`docker compose down -v`는 필요하지 않으며 이번 작업에서도 실행하지 않는다.

## AI 작업 전달

백엔드 내부 애플리케이션 서비스에서 인증/소유권을 확인한 후 `AiJobService.enqueue(taskId)`를 호출한다.
신규 PENDING 작업에서만 등록하며, 동일 작업의 반복 호출은 같은 jobId를 반환한다.
이 경로가 답변을 생성하므로 별도로 DRAFT Answer를 먼저 생성하지 않는다.

1. 짧은 트랜잭션에서 작업을 RUNNING으로 전환하고 `backend.ai_job`에 전송 대기를 기록한다.
2. 스케줄러가 커밋된 작업 최대 20개를 읽고 트랜잭션 밖에서 AI HTTP API에 전달한다.
3. 2xx 응답 후 별도 트랜잭션으로 전송 완료를 기록한다. 실패한 전송은 다음 주기에 재시도한다.
4. AI가 결과 콜백을 보내면 작업 상태·jobId·입력값을 확인하고 결과 전체를 한 트랜잭션으로 저장한다.

전송 성공과 완료 기록 사이 장애가 나면 동일 jobId로 재전송될 수 있다. AI는 반드시 jobId로 중복 실행을 방지해야 한다.
전송 완료 후 AI의 결과 콜백 재시도는 AI 서버의 책임이다. 장기 실행 작업의 시간 초과/운영 재처리 정책은 아직 별도로 구현하지 않았다.

AI 통신은 기본적으로 꺼져 있다. 실제 AI 서버를 연결할 때 다음 환경변수를 지정한다.

| 변수 | 의미 |
| --- | --- |
| `CAST_AI_ENABLED=true` | 전송 스케줄러 활성화 |
| `CAST_AI_BASE_URL` | AI 서버의 origin URL. 요청 경로는 `/v1/research-jobs` |
| `CAST_AI_API_TOKEN` | 백엔드 → AI 인증 토큰, 32자 이상 |
| `CAST_AI_CALLBACK_TOKEN` | AI → 백엔드 인증 토큰, 32자 이상 |

두 토큰은 별도 생성한다. 콜백 토큰이 비어 있으면 내부 AI API는 모든 요청을 거절한다.
내부 통신은 배포 시 HTTPS 또는 신뢰된 사설망에서 제공한다. 토큰은 URL/로그에 기록하지 않는다.
사용자용 일반 API는 JWT Bearer 인증을 사용하며 `GET /actuator/health`는 공개한다.
`/internal/ai/**`만 별도 무상태 서비스 인증을 사용하며 사용자 로그인으로 접근할 수 없다.

상세 요청/응답 예시와 AI 구현 요구사항은 [AI 연동 계약](docs/ai-contract.md)을 참고한다.

## 이메일 회원가입과 로그인

| API | 입력 | 성공 응답 |
| --- | --- | --- |
| `POST /api/v1/auth/signup` | `email`, `password` | 201, 가입 직후 Access Token 발급 |
| `POST /api/v1/auth/login` | `email`, `password` | 200, Access Token 발급 |

두 API는 기존 `ApiResponse`를 사용한다. `data`에는 `userId`, `email`, `accessToken`,
`tokenType: "Bearer"`, `expiresIn: 3600`이 들어간다. 예시 요청은 다음과 같다.

```json
{"email":"user@example.com","password":"MySecurePassword123!"}
```

- 이메일 앞뒤 공백을 제거하고 `Locale.ROOT` 기준 소문자로 정규화한 뒤 검증/저장한다.
- 가입 비밀번호는 8자 이상이며 UTF-8 기준 72바이트 이하여야 한다. 공백만 있는 비밀번호는 허용하지 않는다.
  비밀번호 자체를 trim하거나 소문자로 바꾸지 않는다. BCrypt strength 10으로 해싱하며 로그인에는 `matches`를 사용한다.
- 이메일 중복은 409, 입력 오류는 400이다. 존재하지 않는 이메일과 잘못된 비밀번호는 동일한 401 메시지를 반환한다.
- 정지/탈퇴 계정은 비밀번호 검증 성공 이후 403을 반환한다. 토큰 발급 후 상태가 바뀌어도 다음 보호 요청부터 403이다.
- 기존 대소문자 혼합 이메일도 조회한다. 기존 데이터에 대소문자만 다른 계정이 여러 개 있으면 임의 선택 없이 로그인을 거부한다.
  이번 작업에서는 기존 데이터를 수정하지 않는다.
- 가입 API의 모든 쓰기는 정규화되므로 동시 가입도 기존 `app_user_email_key` UNIQUE 제약으로 최종 방어한다.
  이 DB 제약 자체는 대소문자를 구분한다. 별도 SQL/내부 서비스로 회원을 생성하는 경로까지 정규화가 강제되는 것은 아니다.

### JWT와 인증 사용자

HS256 서명, issuer `cast-api`, subject `userId`, `iat`/`exp`를 사용하며 유효 기간은 60분이다.
Spring Security OAuth2 Resource Server가 서명과 클레임을 검증하고 매 보호 요청마다 현재 DB 사용자의 존재/ACTIVE 상태를 확인한다.
일반 API는 `Authorization: Bearer <accessToken>`으로 호출한다. 세션, HTTP Basic, 폼 로그인은 사용하지 않는다.
쿠키 인증을 사용하지 않으므로 일반 API의 CSRF 검사를 끄며, JWT를 쿼리 파라미터에서 받지 않는다.
공개 경로는 두 인증 POST와 `GET /actuator/health`뿐이다. `/internal/ai/**`는 기존 별도 인증 체인을 유지한다.

이후 출처보관함 등 사용자별 API에서 다음처럼 검증된 사용자 ID를 받아 소유권을 검사한다.
요청 본문/쿼리의 `userId`를 로그인 사용자로 신뢰하지 않는다.

```java
public ResponseEntity<?> example(@AuthenticationPrincipal AuthenticatedUser principal) {
    Long currentUserId = principal.userId();
    // currentUserId와 리소스의 실제 소유자 ID를 비교한 뒤 조회/수정한다.
    // ...
}
```

`AuthenticatedUser`는 `com.example.cast.auth.security` 패키지다. 실제 출처보관함 API 구현은 이번 범위에 포함하지 않는다.
Refresh Token, 재발급/로그아웃 API는 제공하지 않는다. 만료 후 다시 로그인해야 하며 비밀번호 변경만으로 기존 토큰을 폐기하지 않는다.

### 실행 환경 설정

`CAST_JWT_SECRET_BASE64`는 디코딩 후 최소 32바이트의 랜덤 키여야 한다. 누락, 잘못된 Base64,
짧은 키는 앱 시작을 실패시킨다. 실제 키나 키의 기본값은 저장소에 넣지 않는다.
다음 PowerShell 코드는 안전한 랜덤 키를 **현재 프로세스 환경변수**에 설정하며 키를 출력하지 않는다.
이 명령은 앱/DB를 실행하지 않는다. 앱을 실행하는 터미널이나 IDE 실행 설정에 같은 키를 제공한다.

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRng.GetBytes($jwtKeyBytes)
    $env:CAST_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtKeyBytes)
} finally {
    $jwtRng.Dispose()
    [Array]::Clear($jwtKeyBytes, 0, $jwtKeyBytes.Length)
}
$env:CAST_CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
```

여러 백엔드 인스턴스에는 같은 키를 안전한 비밀 저장소로 제공한다. 키를 새로 생성하면 기존 키로 발급한 토큰은 검증되지 않는다.
`CAST_CORS_ALLOWED_ORIGINS`는 쉼표로 구분한 프론트엔드 Origin이며 기본값은 `http://localhost:5173`이다.
예: `https://cast.example.com,https://admin.cast.example.com`. 와일드카드, 경로, 마지막 `/`는 허용하지 않는다.
Spring 실행은 `.env`를 자동으로 읽지 않으므로 `.env.example`만 채워서는 적용되지 않는다.

## 테스트

테스트 실행 승인을 받은 환경에서 `CAST_PERSISTENCE_TEST_APPROVED=YES`를 설정하고 `.\mvnw.cmd test`를 실행한다.
테스트는 임시 PostgreSQL만 사용하고 기존 DB/볼륨에 연결하지 않는다.
공통 테스트의 `@DynamicPropertySource`는 환경변수보다 우선하여 AI 전송을 끄고 테스트 전용 콜백 토큰을 사용한다.
JWT 키와 CORS Origin도 테스트 전용 값으로 고정하며 실제 실행 환경의 비밀을 사용하지 않는다.
`test` 프로필의 YAML만으로는 환경변수를 덮어쓸 수 없으므로 이 설정을 함께 유지한다.
Flyway는 실제 `cast_migrator`, JPA는 `cast_app`, AI SQL 검증은 `cast_ai`로 실행한다.

검증 범위:

- 기존 사용자/동의/조사/답변/검증/저장 출처 제약
- V1 데이터에서 V7까지 이동, 인용 스냅샷·저장 출처·평가 정답 보존
- 두 런타임 계정의 상호 접근/DDL 거부 및 스키마 간 FK 부재
- 동일 결과의 반복/동시 콜백, 작업 ID 불일치, 취소 후 콜백, 실패 결과 처리
- 출처 동시 수신 중복 방지, 전체 결과의 원자적 저장
- 기존 볼륨 계정 전환 스크립트의 재실행과 다른 DB 소유권 보존
- 가짜 AI HTTP 서버를 통한 전송 계약/재시도와 트랜잭션 경계
- 기존 HTTP 응답 및 내부 콜백 인증/검증
- 회원가입/로그인 공통 응답, BCrypt 해싱, 이메일 정규화, 비밀번호 UTF-8 경계
- 동시 가입 충돌과 이메일 UNIQUE/기타 무결성 오류의 구분
- JWT 서명/만료/issuer/subject/필수 클레임, 계정 상태 변경, 존재하지 않는 사용자
- 테스트 전용 API의 인증 principal/소유권, CORS, 세션/쿼리 토큰 거부, 키 설정 오류의 시작 거부

실제 AI 서버 소스와 모델 실행은 이 저장소에 없으므로 그 서버와의 실환경 통합 테스트는 별도다.
