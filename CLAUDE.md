# FinReady — 프로젝트 컨텍스트

2026 금융 AI Challenge 출품작. 팀 '앞과뒤' 2인(백엔드 1, 프론트엔드 1).

ELS 상담에서 **어떤 Risk가 충분히 설명되지 않았는지**와 **고객이 어떤 Risk를 반대로
이해했는지**를 항목 단위로 드러내는 상담 보조 서비스. 법적 판정이 아니라 보조 수단이다.

## 마감

| 일정 | 내용 |
|---|---|
| 2026-09-07 10:00 | 기획서·기능명세서·배포 URL 제출 마감 |
| 2026-09-07 11:00 ~ 09-11 23:59 | 심사 URL 상시 가용 필요 |
| 2026-09-06 | **배포 동결.** 이후 긴급 수정 외 push 금지 |

## 저장소 구조

```
finready/
├── openapi.yaml              ← API 계약. 유일한 공유 파일
├── docs/                     ← PRD, TRD
├── finready-backend/         ← Spring Boot (이 문서의 주 대상)
└── finready-frontend/        ← Next.js (프론트 담당자 영역)
```

## 문서 우선순위

**PRD > TRD > 코드.** 충돌하면 상위 문서가 이긴다.

- `docs/` 의 PRD v1.3.1 — 제품 요구사항. DEV FREEZE 상태
- `docs/` 의 Backend TRD v1.2.3 — 기술 결정. 데이터 모델·상태머신·검증 절차
- `openapi.yaml` v1.4.2 — API 계약

**작업 전에 TRD의 해당 절을 먼저 읽을 것.** 특히 §4(데이터 모델), §6(Enum 계약),
§8(Evidence 검증)은 값 하나가 어긋나면 세 문서 대조에서 걸린다.

## 기술 스택

Java 25 / Spring Boot 4.0.7 / Gradle Kotlin DSL / Spring Data JPA /
Flyway / PostgreSQL(Supabase, 스키마 `finready`) / Render 배포(Singapore)

Boot 4는 Jackson 3(`tools.jackson`)를 쓴다. Boot 3 예제를 그대로 가져오면 안 된다.

## 절대 어기면 안 되는 규칙

1. **AI 원판정을 덮어쓰지 않는다.** `coverage_result.classifier_status`와
   `understanding_result.ai_status`는 어떤 경로로도 UPDATE되지 않는다.
   Override/Resolution은 별도 테이블 INSERT다. 리포지토리에 UPDATE 메서드를 만들지 말 것.

2. **합성 상태를 저장하지 않는다.** `effectiveStatus` 같은 필드를 만들지 않는다.
   `classifierStatus`(AI 원판정)와 `coverageStatus`(검증 후)를 별도 컬럼으로 둔다.

3. **EXPLAINED는 provenance + semantic을 모두 통과해야 성립한다.**
   DB check 제약으로도 강제돼 있다. 애플리케이션에서 우회하지 말 것.

4. **LLM이 반환한 offset을 쓰지 않는다.** 서버가 원문에서 재계산한다.
   응답 offset은 항상 원문 UTF-16 code unit 기준.

5. **스키마 변경은 Flyway로만.** `ddl-auto: validate` 고정. 엔티티를 고쳤으면
   마이그레이션 파일을 추가한다. 기존 마이그레이션을 수정하지 않는다.

6. **LLM 호출은 트랜잭션 밖에서.** 30초짜리 커넥션 점유를 만들지 않는다.
   DB role에 `idle_in_transaction_session_timeout=30s`가 걸려 있어 어기면 런타임에 터진다.

7. **상태 전이는 `common.StateMachine` 단일 지점을 통과한다.** 서비스 코드에
   상태 분기를 흩뿌리지 않는다. 미허용 전이는 `INVALID_STATE_TRANSITION`(409).

8. **프론트 분기는 서버가 결정한다.** 흐름을 진전시키는 응답은 `nextAction`을 싣는다.
   산출 규칙은 TRD §6.6.

9. **enum 문자열은 TRD §6이 전부다.** 목록에 없는 값을 만들지 않는다.
   LLM이 enum 밖의 값을 반환하면 파싱 실패로 처리한다. 임의 매핑 금지.

10. **API 키·DB 자격증명을 코드·응답·로그에 넣지 않는다.** 환경변수만 쓴다.

## 계약 파일 규칙

`openapi.yaml`은 **백엔드만 수정**한다. 바꿀 때는:
- `info.version`을 올린다
- `description`의 변경 이력 블록에 요약을 적는다
- 커밋 메시지 앞에 `contract:`를 붙인다

## 커밋 컨벤션

```
feat(be): F03 Coverage 4상태 분류 + Gate 판정
fix(be): F05 attempt 상한이 서버에서 안 걸리던 문제
contract: openapi v1.4.3 — recheckQuestion 추가
docs: TRD §4.6 session_question 신설
chore: .gitignore 패턴 기반으로 변경
```

범위는 `be` / `fe` / `contract` / `docs` / `chore`.
기능 작업은 PRD의 F01~F08, S01~S08 ID를 제목에 넣는다.

## 현재 진행 상황

### 완료
- Supabase `finready` 스키마 + `finready_backend` role + search_path/타임아웃/커넥션 한도
- 프로젝트 스캐폴딩, `gradlew build` 성공 (Java 25 / Boot 4.0.7 / Gradle 9.5.1)
- `application.yml`, `V1__init.sql`, `V2__audit_append_only.sql` 배치
- `seed/product_a_risk_schema.json` — PRD v1.3.1 정책표 반영본
- `static/documents/PROD_A/v1.0.pdf` — SHA-256 `5d355381abe028eb492f3c277236ee35a774150f4dbb24c289d2612ca8c5c47e`
- `src/test/resources/eval/demo_seed.json` — Gate 시나리오 6건 검증 완료
- 시드 sourceText 9건이 PDF 지정 페이지에 정확히 1회 존재함을 확인 (2026-08-12)

### 다음 순서
1. `application-local.yml` 작성 → `local` 프로파일로 기동 → Flyway가 테이블 14개 생성 확인
2. Render 배포 관통 (Root Directory `finready-backend`, Build Filter `finready-backend/**`)
3. **JPA 엔티티 14개** — V1 DDL과 컬럼명·제약이 정확히 일치해야 함 (`ddl-auto: validate`)
4. **시드 로더 + 검증기** — TRD §4.5. 검증 실패 시 기동 중단
5. `GET /api/products/demo` (F01)
6. 세션 / Revision / StateMachine (TRD §5.1)
7. Coverage 4상태 + Provenance + OffsetMapper + Verifier + Gate + Override (F03)
8. Understanding / 재설명 / Staff Resolution (F04~F07)
9. Report + Close + Audit (F08)
10. 오프라인 평가 모듈 + Rule baseline

### 데이터셋 현황 (별도 작업, 코드와 병행)
- 상담 시나리오 6 / 목표 60 — `CONS_A_002`~`006`은 본문 미작성
- 고객 답변 12 / 목표 180
- 라벨을 먼저 정하고 상담문을 생성하는 방식. 사후 라벨링 비용이 0이다

## 미결정

- LLM 모델·요금제 (심사 5일 quota 산정 필요)
- Guardrail 금칙어 최종 목록

## 작업할 때

- 빌드는 `finready-backend`에서 `.\gradlew.bat build` (Windows)
- 로컬 실행은 `local` 프로파일. 접속 정보는 `application-local.yml`(git 제외)
- 테스트에서 실제 LLM을 호출하지 않는다. 평가 모듈만 `@Tag("evaluation")`으로 분리
- 큰 변경 전에는 계획을 먼저 제시하고 승인을 받을 것
