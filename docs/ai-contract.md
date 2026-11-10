# AI 서버 연동 계약

## 소유권

AI 서버는 `cast_ai`로 `ai` 스키마만 읽고 쓴다. 백엔드의 JPA 엔티티를 복제하지 않는다.
회원·사용자 작업 상태·최종 결과는 API를 통해 백엔드가 저장한다. AI에는 사용자 비밀번호나 회원 테이블 접근이 필요하지 않다.

AI가 구현할 기능: 원문 수집/해시 중복 제거, 날짜 유효기간 필터, 청킹/검색/재검색, 규칙·LLM 검증, 평가 데이터 관리, 작업 중복 방지 및 결과 재전송.
현재 `ai.retrieval_run.task_id`는 요청받은 백엔드 taskId를 저장하는 외부 참조다.
`claim_id`는 기존 값은 보존하되 신규 AI 작업에서는 AI 내부에서 할당한 Claim 키로 사용한다. 백엔드의 claim_id로 조회하거나 조인하지 않는다.
기존 RETRY 1~3회 정책과 run_type/claim_id/회차 CHECK는 유지된다.
AI 서버에서 검색 실행과 Claim이 같은 작업에 속하는지 검증해야 한다.
평가 JSON 청크 ID는 FK가 아니므로 재청킹 시 명시적인 참조 검증/재매핑이 필요하다.

## 백엔드 → AI

`POST {AI_ORIGIN}/v1/research-jobs`

헤더:

```text
Content-Type: application/json
X-AI-Service-Token: <CAST_AI_API_TOKEN>
Idempotency-Key: <jobId>
```

본문:

```json
{
  "jobId": "d9428888-122b-4d4f-9fbd-3d3d3d3d3d3d",
  "taskId": 42,
  "question": "지원 대상과 신청 기간은?"
}
```

AI는 작업을 내구성 있게 접수한 뒤 2xx(예: 202)를 반환한다. 헤더와 본문의 jobId를 확인하며, 같은 jobId로 같은 작업이 들어오면 재실행하지 않고 기존 접수 결과를 반환한다.
네트워크 장애/응답 유실로 같은 요청이 여러 번 도착할 수 있다. 비정상 응답은 백엔드에서 재시도한다.
HTTP 연결 제한은 3초, 요청 제한은 30초다. AI는 전체 추론이 끝날 때까지 접수 응답을 보류하지 않는다.

## AI → 백엔드

`POST {BACKEND_ORIGIN}/internal/ai/research-tasks/{taskId}/result`

`X-AI-Service-Token`에는 **CAST_AI_CALLBACK_TOKEN**을 사용한다. 사용자 Basic 인증이나 세션으로 대체할 수 없다.
콜백 URL은 AI 서버 배포 설정에 지정한다. 요청 문장이나 사용자 입력에서 URL을 가져오지 않는다.

성공 예시:

```json
{
  "jobId": "d9428888-122b-4d4f-9fbd-3d3d3d3d3d3d",
  "outcome": "SUCCESS",
  "answer": "지원 금액은 30만 원입니다.",
  "summary": "월 지원 금액 안내",
  "errorMessage": null,
  "claims": [{
    "content": "지원 금액은 30만 원이다.",
    "type": "AMOUNT",
    "value": {"amount": 300000, "unit": "KRW"},
    "aiRunId": 120,
    "verification": {
      "status": "SUPPORTED",
      "reason": "공식 안내의 금액과 일치",
      "method": "RULE",
      "ruleResult": "SUPPORTED",
      "llmResult": null,
      "confidence": 0.95
    },
    "citations": [{
      "source": {
        "aiDocumentId": 10,
        "title": "지원사업 공고",
        "sourceName": "공식 기관",
        "sourceUrl": "https://example.invalid/notice",
        "publishedAt": "2026-11-01",
        "effectiveFrom": "2026-11-01",
        "effectiveTo": "2026-11-30",
        "version": "v1",
        "sourceType": "GOVERNMENT",
        "reliabilityLevel": "HIGH"
      },
      "aiChunkId": 15,
      "text": "월 30만 원을 지원합니다.",
      "relation": "SUPPORTS",
      "pageStart": 1,
      "pageEnd": 1,
      "sectionTitle": "지원 내용"
    }]
  }]
}
```

실패는 같은 jobId로 `outcome: FAILURE`, `answer/summary: null`, `claims: []`, 비어 있지 않은 `errorMessage`를 전달한다.
백엔드는 AI 내부 오류 메시지를 사용자에게 복사하지 않고 일반 실패 메시지를 저장한다.
진행 중 상태(IN_PROGRESS)는 최종 검증 결과에 사용할 수 없다. RULE/LLM은 해당 판정과 최종 상태가 같아야 하고, HYBRID는 두 판정을 모두 포함한다.
신뢰도는 null 또는 유한한 0~1이다. Claim은 최대 500개, Claim별 인용은 최대 100개다.

성공 응답(재전송 시에도 같은 데이터):

```json
{"result":{"code":200,"success":true,"message":"AI 결과가 반영되었습니다."},"data":{"taskId":42,"answerId":7,"status":"COMPLETED"}}
```

- 같은 jobId와 동일한 DTO 내용의 재전송은 결과를 다시 생성하지 않고 200을 반환한다.
- 정규화 값 Map의 키 순서는 중복 판단에 영향을 주지 않는다. Claim/인용 목록 순서와 값은 동일하게 재전송한다.
- 다른 작업의 jobId, 이미 접수된 결과의 내용 변경, 취소/종료 작업의 새 결과는 409다.
- 검증 실패는 400, 인증 실패는 401, 없는 작업은 404다. 서버 오류/네트워크 오류는 같은 결과로 재시도한다.
- 작업 결과 전체와 접수 해시는 한 트랜잭션으로 저장된다. 부분 저장 후 성공 응답을 반환하지 않는다.

`aiDocumentId`는 AI 원문의 불변 식별자로 사용한다. 원문 내용/버전 변경 시 새 ID를 사용한다.
백엔드는 같은 AI 문서 ID를 최초 수신한 메타데이터 스냅샷으로 재사용한다.
`aiChunkId`와 `aiRunId`는 선택적인 진단용 식별자이며 백엔드는 해당 AI 테이블을 읽지 않는다.
페이지/섹션/인용문은 결과에 포함해야 사용자 조회가 AI 데이터 변경에 영향을 받지 않는다.
