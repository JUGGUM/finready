# FinReady Backend

Spring Boot 백엔드. 저장소 공통 규칙(마감·문서 우선순위·계약 파일·커밋 컨벤션)은
루트 `CLAUDE.md`에 있다. 이 파일은 백엔드 작업 규칙만 다룬다.

## 기술 스택

Java 25 / Spring Boot 4.0.7 / Gradle Kotlin DSL / Spring Data JPA /
Flyway / PostgreSQL(Supabase, 스키마 `finready`) / Render 배포(Singapore)

Boot 4는 Jackson 3(`tools.jackson`)를 쓴다. Boot 3 예제를 그대로 가져오면 안 된다.
springdoc은 3.x 라인이다. 2.x는 Boot 3 전용이다.

> **TRD §1 기술스택 표는 `Java 21 (LTS) / Spring Boot 3.x`로 적혀 있다.**
> 코드·Dockerfile·이 문서가 Java 25 / Boot 4.0.7이므로 **TRD 쪽이 낡았다.**
> 결정: 코드를 유지하고 TRD §1을 정정한다(→ v1.2.4). 아직 반영 전이므로
> 세 문서 대조 전에 처리할 것.

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

## 작업할 때

- **로컬 실행 전 JAVA_HOME을 JDK 25로 잡을 것.** 셸 기본값이 존재하지 않는
  openjdk@17 경로라 gradlew가 즉시 죽는다.
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS
  ./gradlew build
  ```
  Windows는 `.\gradlew.bat build`.
- 로컬 실행은 `local` 프로파일. 접속 정보는 `application-local.yaml`(git 제외).
  템플릿은 `application-local.yaml.example`.
- 테스트에서 실제 LLM을 호출하지 않는다. 평가 모듈만 `@Tag("evaluation")`으로 분리
- 큰 변경 전에는 계획을 먼저 제시하고 승인을 받을 것

## 현재 진행 상황

### 완료
- Supabase `finready` 스키마 + `finready_backend` role + search_path/타임아웃/커넥션 한도
- 프로젝트 스캐폴딩, `gradlew build` 성공 (Java 25 / Boot 4.0.7 / Gradle 9.5.1)
- `application.yaml`, `V1__init.sql`, `V2__audit_append_only.sql` 배치
- `seed/product_a_risk_schema.json` — PRD v1.3.1 정책표 반영본
- `static/documents/PROD_A/v1.0` — SHA-256 `5d355381abe028eb492f3c277236ee35a774150f4dbb24c289d2612ca8c5c47e`
  (파일명에 `.pdf` 확장자가 없다. 시드의 `documentFileName`과 로더에서 맞출 것)
- `src/test/resources/eval/demo_seed.json` — Gate 시나리오 6건 검증 완료
- 시드 sourceText 9건이 PDF 지정 페이지에 정확히 1회 존재함을 확인 (2026-08-12)

### 검증한 것 (2026-08-12)
- V1 테이블 14개가 TRD §4.1 목록과 이름 일치
- 시드 risk 9건 정책이 PRD §5 정책표와 일치
  (R01–R03 GATE_REQUIRED+understandingCheck, R04·R08 GATE_REQUIRED, R05–R07·R09 WARN_ONLY)
- PDF SHA-256이 기재값과 일치
- V2 트리거가 `before update or delete`만 잡고 INSERT를 넣지 않음 (TRD §4.4가 경고한 사고 회피됨)
- JDK 25로 `gradlew build` BUILD SUCCESSFUL

### 다음 순서
1. `application-local.yaml`에 **실제** Supabase Session Pooler 접속 정보 채우기
   → `local` 프로파일 기동 → Flyway가 테이블 14개 생성 확인
   → TRD §3.4 연결 검증 5항목 실행
2. Render 배포 관통 (Root Directory `finready-backend`, Build Filter `finready-backend/**`)
3. **JPA 엔티티 14개** — V1 DDL과 컬럼명·제약이 정확히 일치해야 함 (`ddl-auto: validate`)
4. **시드 로더 + 검증기** — TRD §4.5. 검증 실패 시 기동 중단
5. `GET /api/products/demo` (F01)
6. 세션 / Revision / StateMachine (TRD §5.1)
7. Coverage 4상태 + Provenance + OffsetMapper + Verifier + Gate + Override (F03)
8. Understanding / 재설명 / Staff Resolution (F04~F07)
9. Report + Close + Audit (F08)
10. 오프라인 평가 모듈 + Rule baseline

> CLAUDE.md의 1번은 TRD §18 Step 1보다 좁다. TRD Step 1 DoD는
> `연결 + Flyway + 시드 로더 + GET /products/demo` + §3.4 검증 5항목까지다.
> 나머지는 여기 4·5번에 있다.

### 데이터셋 현황 (별도 작업, 코드와 병행)
- 상담 시나리오 6 / 목표 60 — `CONS_A_002`~`006`은 본문 미작성
- 고객 답변 12 / 목표 180
- 라벨을 먼저 정하고 상담문을 생성하는 방식. 사후 라벨링 비용이 0이다

## 미결정

- LLM 모델·요금제 (심사 5일 quota 산정 필요) — TRD D-02, Step 5 이전 결정
- Guardrail 금칙어 최종 목록 — TRD D-04, Step 7 결정
- TRD §1 기술스택 표 정정 (Java 21/Boot 3.x → Java 25/Boot 4.0.7)
- `finready-frontend/contracts/openapi.yml` v1.4.1 → v1.4.2 동기화
