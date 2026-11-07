# CAST backend

Java 21 / Spring Boot 4.1.1 / PostgreSQL 17 / Maven Wrapper.

## 로컬 실행

1. Docker 엔진을 실행한다.
2. `.env.example`을 `.env`로 복사하고 관리자/앱 비밀번호를 채운다.
3. 저장소 루트에서 `docker compose up -d`로 PostgreSQL을 시작한다.
4. Spring 실행 환경에 `SPRING_PROFILES_ACTIVE=local`과 `.env`의 앱 비밀번호와 같은 `CAST_DB_PASSWORD`를 지정한다.
5. Windows에서는 `.\mvnw.cmd spring-boot:run`으로 실행한다.

PowerShell에서 프로필은 `$env:SPRING_PROFILES_ACTIVE = 'local'`로 지정할 수 있다.
비밀번호는 IDE 실행 구성의 환경변수나 현재 터미널의 환경변수로 설정한다.
Docker Compose는 `.env`를 읽지만 Spring Boot는 이 파일을 자동으로 읽지 않는다.
기본 프로필은 의도적으로 지정하지 않는다. 배포 환경은 프로필/데이터소스를 명시해야 한다.
로컬 기본 주소는 `jdbc:postgresql://127.0.0.1:5433/cast_dev`, 사용자는 `cast_app`이다.
주소/사용자는 `DB_URL`, `DB_USERNAME`으로 변경할 수 있다.

`open-in-view: false`, `ddl-auto: validate`는 공통 설정이다.
Flyway는 기본 경로 `classpath:db/migration`을 사용한다.
이미 적용된 V1 파일은 수정하지 않고 V2 이후의 순차 마이그레이션으로 변경한다.

## 인증 동작

폼 로그인, HTTP Basic, 세션 및 CSRF 보호를 유지한다. JWT는 아직 도입하지 않았다.
기존 Boot Actuator 정책과 같이 health는 공개하고, 그 외 요청에는 인증이 필요하다.
브라우저의 HTML 요청은 로그인 화면으로 이동한다. API의 미인증 요청과 잘못된 Basic/폼 로그인은
공통 401 본문을 사용하며, 인가/CSRF 실패는 공통 403 본문을 사용한다.
API 성공/오류 형식은 `result { code, success, message }`와 `data`다.
`code`는 HTTP 상태 번호이며, 내부 예외나 SQL 메시지는 외부 응답에 노출하지 않는다.
사용자 DB를 실제 로그인과 연결하는 인증 기능은 별도 구현 대상이다.

## 도메인과 입력 경계

- `user`: 사용자/동의
- `research`: 질문/조사 작업/답변
- `claim`: Claim/검증/근거
- `retrieval`: 검색 실행/검색 결과
- `document`: 원문/청크
- `source`: 저장 출처/폴더
- `evaluation`: 평가 정답
- `global`: 응답, 예외, 설정, 입력 검증

입력은 각 도메인의 `dto/request` 계약을 사용하고 서비스에서 `RequestValidator`로 검증한다.
HTTP 이외의 수집/배치 호출도 이 검증을 통과해야 한다. 엔티티 직접 생성/수정은 내부 구현과
DB 제약 테스트용이며 외부 입력의 진입점으로 사용하지 않는다. 상태 전이와 재검색 예산 등의
도메인 규칙은 엔티티에 유지한다.

쓰기 서비스는 트랜잭션 안에서 참조를 조회하고 값을 변경한다. 현재 서비스는 내부 애플리케이션
진입점이며 사용자 인증/소유권을 대신하지 않는다. 향후 API에서는 인증된 사용자의 접근 권한을
확인한 뒤 호출하고, 엔티티를 반환하지 말고 트랜잭션 안에서 응답 DTO로 변환해야 한다.
`PageResponse`는 외부 페이지 번호를 1부터 표현한다. 이번 변경에는 목록 API를 추가하지 않았다.

## 데이터 정책

### Claim / Verification

신규 Claim은 `ClaimRequest`의 유형/정규화 값과 함께 생성한다. 기존 행의 값은 추측해서 채우지 않는다.
내용/정규화 값 변경은 `ClaimService.revise`에서만 수행한다. 최초 검증이 시작되면 변경을 거절하고
새 Claim을 생성해야 한다. `CONFIRMED`는 검증 대상으로 확정했다는 뜻이며 근거의 지지를 의미하지 않는다.

Claim 수정과 `VerificationService.start`는 같은 Claim 행의 쓰기 잠금을 사용한다.
검증 시작은 잠금 안에서 다음 검증 시도 번호를 할당한다. 검색 재시도 번호와는 독립적이다.
검증의 검색 실행은 같은 조사 작업에 속해야 하며 Claim별 검색이면 같은 Claim이어야 한다.
규칙/LLM 중간 결과에는 `IN_PROGRESS`를 허용하지 않는다. 신뢰도는 null 또는 유한한 0~1이다.
RULE/LLM 방식의 최종 상태는 해당 결과와 같고, HYBRID는 두 결과를 보존한다.
HYBRID의 구체적인 최종 판정 알고리즘은 호출 측 검증 엔진의 책임이다.

### Document

`DocumentService.collect`는 원문 **바이트 전체**의 SHA-256으로 중복을 판단한다.
다른 인코딩이나 바이트가 다른 파일까지 의미적으로 합치지는 않는다.
같은 해시의 기존 문서는 ID, 제목, 수집 시각을 유지하고 재사용한다.
부분 유니크 인덱스와 `INSERT ... ON CONFLICT DO NOTHING`으로 동시 수집을 처리한다.
이 경로는 READ COMMITTED 트랜잭션을 사용한다. 더 강한 격리 수준의 외부 트랜잭션에 합류시키지 않는다.
네이티브 INSERT의 생성/수정 시각은 V1의 DB 기본값, 수집 시각은 서비스의 UTC Clock을 사용한다.
기존 문서는 원문을 확보해 중복 정리하기 전까지 해시가 null이며, 자동으로 병합하지 않는다.

유효기간의 null 경계는 제한 없음이고 시작일과 종료일을 포함한다.
`DocumentChunkRepository.findValidOn`으로 기준일에 유효한 문서의 청크를 조회한다.

### 평가 정답과 재청킹

V5는 기존 문자열 정답을 `{type: null, content: 기존 문자열, value: null}`로 변환하며 순서를 유지한다.
기존 정답의 유형/정규화 값은 임의로 추정하지 않는다.
신규 정답은 `EvaluationWriteService`에서 유형/값을 필수로 받아 생성한다.
구조화 평가 실행 전 `EvaluationReferenceService.validateForStructuredEvaluation`을 호출하여
정답의 구조화 여부와 청크 ID의 존재를 확인해야 한다.

청크 ID와 `(documentId, chunkIndex)` 모두 재청킹 후 같은 내용을 보장하지 않는다.
따라서 기존 `relevant_chunk_ids`를 유지하며 다음 정책을 적용한다.

1. 평가에 쓰인 청크의 본문/인덱스를 제자리에서 바꾸지 않는다. 평가용 데이터셋을 고정한다.
2. 재청킹은 별도 버전 데이터셋에서 수행하고, 기존 청크와 새 청크의 내용을 비교하여 명시적 매핑을 만든다.
3. 매핑이 없거나 내용이 달라졌으면 평가를 중단한다. 같은 인덱스라는 이유로 자동 치환하지 않는다.
4. 검토한 새 참조/정답으로 새 EvaluationCase를 생성하고 평가 실행 대상을 새 ID로 전환한다.
5. JSONB ID 배열에는 FK가 없으므로 기존 청크를 제거하기 전에 영향받는 평가 케이스를 확인한다.

### 시간/스키마

`BaseTimeEntity`는 UTC Instant 기반 JPA Auditing을 사용한다. 벌크/네이티브 UPDATE는
Auditing을 거치지 않으므로 `updated_at`을 직접 갱신해야 한다.
이벤트 시각(`ResearchTask`의 시작/종료, 동의 발생 시각)은 생성/수정 시각과 별도로 유지한다.
DDL은 Flyway가 관리하고 JPA는 검증한다. JSON과 명시적인 시간대 JDBC 매핑은 유지한다.

개념상의 User/Query는 각각 `app_user`/`research_query`에 대응한다.
V1의 실제 FK 정책은 대부분 RESTRICT이며 `document_chunk.document_id`도 RESTRICT다.
`SavedSource.origin_task_id`는 SET NULL, 소유 폴더 복합 FK는 `folder_id`만 SET NULL이다.
이번 변경은 기존 삭제 정책을 바꾸지 않는다.

## 테스트

테스트 실행은 사용자 승인을 받은 뒤에만 한다. 승인 후 현재 프로세스에서
`CAST_PERSISTENCE_TEST_APPROVED=YES`를 설정하고 `.\mvnw.cmd test`를 실행한다.
실행이 끝나면 해당 환경변수를 해제한다. 빌드/테스트에는 JDK 21과 실행 중인 Docker 엔진이 필요하다.
초기 실행에는 Maven 의존성 및 `postgres:17` 이미지 다운로드가 필요할 수 있다.

테스트는 Testcontainers가 만든 임시 DB를 사용한다. 개발 DB/고정 5436 포트/외부 비밀번호를 사용하지 않는다.
Spring 컨텍스트와 같은 생명주기의 컨테이너에 Flyway를 적용한 뒤 Hibernate 스키마 검증을 수행한다.
컨테이너는 종료 시 정리한다. DB에 쓰는 동시성 테스트도 이 임시 DB만 사용한다.

- 기존 영속성·FK·CHECK 검증 및 새 데이터 필드/서비스 정책
- 같은 문서 동시 수집과 동시 검증 시도 번호 할당
- V1 기존 데이터에서 V5까지 업그레이드 및 체크섬 검증
- API 성공, 검증 실패, 잘못된 JSON, 401/403/404/405/409/500 응답
- 폼 로그인 이동과 기존 health 공개 동작

기존 미추적 영속성 테스트의 검증 본문은 유지하면서 import, 수집 시각, 평가 정답 타입,
DB 연결과 실행 승인 검사 부분을 현재 구조에 맞췄다.

## PostgreSQL 초기화

`docker/postgres/init`은 볼륨을 처음 생성할 때만 실행된다. `.env`의 비밀번호만 바꾸어도
기존 DB 계정 비밀번호가 자동으로 변경되지는 않는다. 이번 변경은 초기화 스크립트와 계정 구성을 유지한다.
기존 볼륨을 지우는 명령은 실행하지 않는다. 운영의 마이그레이션/런타임 계정 분리는 별도 배포 설계 대상이다.

## 참고

- [Spring Security Java configuration](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
