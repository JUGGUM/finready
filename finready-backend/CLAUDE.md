# FinReady Backend

Spring Boot 백엔드. 저장소 공통 규칙(마감·문서 우선순위·계약 파일·커밋 컨벤션)은
루트 `CLAUDE.md`에 있다. 이 파일은 백엔드 작업 규칙만 다룬다.

## 기술 스택

Java 25 / Spring Boot 4.0.7 / Gradle Kotlin DSL / Spring Data JPA /
Flyway / PostgreSQL(Supabase, 스키마 `finready`) / Render 배포(Singapore)

Boot 4는 Jackson 3(`tools.jackson`)를 쓴다. Boot 3 예제를 그대로 가져오면 안 된다.
springdoc은 3.x 라인이다. 2.x는 Boot 3 전용이다.

> **Boot 4 모듈화 주의.** Boot 4는 자동설정을 기술별 모듈로 쪼갰다.
> 라이브러리(`flyway-core` 등)만 넣으면 자동설정이 **조용히 안 걸린다.**
> 반드시 `spring-boot-starter-*` 형태로 넣을 것. 앞으로 Redis·Kafka 등을
> 추가할 때도 동일하다.
>
> 판별법: 기동 시 `debug: true`로 CONDITIONS EVALUATION REPORT를 찍었을 때
> 해당 기능 이름이 **Positive에도 Negative에도 없으면 = 전용 스타터 누락**이다.
> Negative에 있으면 조건 문제다.

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

- **저장소를 한글·공백 경로에 두지 말 것.** Windows에서 `gradlew test`가 통째로 깨진다.
  Gradle이 테스트 워커 클래스패스를 `@argfile`로 넘기는데, Gradle은 UTF-8로 쓰고
  JVM 런처는 네이티브 인코딩(cp949)으로 읽어서 경로가 깨진다. 증상은
  모든 테스트 클래스에 `ClassNotFoundException`이며, **컴파일은 멀쩡히 통과한다.**
  워커 명령줄의 `-Dfile.encoding=UTF-8`은 argfile을 읽은 뒤 적용돼 소용없다.
  이 문제로 `D:\공부\finready` → `D:\dev\finready`로 옮겼다 (2026-08-14).
  F03의 Testcontainers도 Docker 볼륨 마운트에서 같은 계열 문제를 겪는다.
- **로컬 실행 전 JAVA_HOME을 JDK 25로 잡을 것.** 셸 기본값이 존재하지 않는
  openjdk@17 경로라 gradlew가 즉시 죽는다.
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS
  ./gradlew build
  ```
  Windows는 `.\gradlew.bat build`.
- 로컬 실행은 `local` 프로파일. 접속 정보는 IntelliJ Run Configuration
  환경변수로 관리한다. 참고용 템플릿은 `application-local.yaml.example`.
  Run Configuration에 `SPRING_PROFILES_ACTIVE=local` + `DB_URL` /
  `DB_USERNAME` / `DB_PASSWORD`를 한 번 넣어두면 이후 초록 버튼으로 그냥 실행된다.
- **앱을 띄우는 데는 Docker가 필요 없다.** DB가 원격 Supabase라 띄울 컨테이너가 없다.
  `Dockerfile`은 Render 배포 전용이다(아래 참조).
  다만 **F03부터는 통합 테스트에 Docker가 필요하다** — DB 제약(`ck_*`)·append-only 트리거·
  `updatable=false`·`@Version` 락은 실제 Postgres 없이는 검증되지 않는다.
  Testcontainers를 그때 붙인다. Render 빌드는 `Dockerfile`이 `-x test`라 영향 없다.
- 테스트에서 실제 LLM을 호출하지 않는다. 평가 모듈만 `@Tag("evaluation")`으로 분리
- 큰 변경 전에는 계획을 먼저 제시하고 승인을 받을 것
- **`columnDefinition`은 `ddl-auto: validate`에 영향을 주지 않는다.** DDL 생성용이라
  JDBC 타입 코드를 바꾸지 못한다. DB 타입 코드가 Java 기본 매핑과 다른 컬럼은
  `@JdbcTypeCode`로 지정해야 한다. 실제로 `product.document_sha256`의 `char(64)`에서
  `found [bpchar (Types#CHAR)], but expecting [char(64) (Types#VARCHAR)]`로 걸렸고
  `@JdbcTypeCode(SqlTypes.CHAR)`로 해결했다 (2026-08-13)
- **엔티티 검증은 기동으로만 된다.** `gradlew build`는 컴파일만 확인한다.
  Hibernate 검증기는 불일치를 만나면 예외를 던져 **한 번에 하나만** 보여주므로,
  엔티티를 몰아서 쓰면 고치고 재기동을 반복하게 된다. DDL 섹션 단위로 나눠 진행할 것
- **Boot 4는 테스트 슬라이스도 모듈로 쪼갰다.** `@WebMvcTest`가
  `spring-boot-starter-test`에 없다. `spring-boot-starter-webmvc-test`를 따로 넣어야 한다.
  패키지도 `org.springframework.boot.webmvc.test.autoconfigure`로 옮겼다(Boot 3과 다름).
  `@MockBean`은 없어졌고 `@MockitoBean`
  (`org.springframework.test.context.bean.override.mockito`)을 쓴다.

## 테스트 전략

**하이브리드.** 순수 단위/`@WebMvcTest`는 기본 `test` 태스크, 실 Postgres가 필요한 검증은
`integrationTest` 태스크로 분리했다(둘 다 2026-08-14 기준 도입 완료, F03 기능 코드는 아직).

| 단계 | 방식 | 검증 범위 |
|---|---|---|
| `./gradlew test` (기본) | 순수 단위 + `@WebMvcTest` | 로직·계약 JSON 필드명·오류 코드 매핑 |
| `./gradlew integrationTest` (Docker 필요) | Testcontainers PostgreSQL | `ck_*` 제약, append-only 트리거, `updatable=false`, `@Immutable`, `@Version` 락, `ddl-auto: validate` 회귀, Flyway 맨바닥 실행 |

지금 순수 단위 방식으로는 **DB에 걸린 규칙을 검증할 수 없다.** 특히 규칙 1의 `updatable=false`는
Hibernate가 실제 SQL을 만들어야 확인되므로, `@WebMvcTest` 쪽은 "코드에 그렇게 적어놨다"까지만
검증한다. F03에서 규칙 3(`ck_explained_requires_verification`)이 실제로 쓰이기 시작하면
`integrationTest` 쪽에 케이스를 추가한다.

- `test`·`integrationTest` 둘 다 `test` 프로파일로 돈다. `SeedLoader`는 `@Profile("!test")`라
  안 돈다
- `src/test/resources/application-test.yaml`이 가짜 datasource를 박아둔다.
  `integrationTest`는 `io.finready.integration.AbstractPostgresIntegrationTest`가
  `@DynamicPropertySource`로 이 값을 컨테이너 실접속 정보로 덮어쓴다 — 별도 프로파일 파일 없이
  같은 `test` 프로파일 안에서 통합 테스트만 실 DB를 쓴다. 이 장치 덕분에 실수로
  `@SpringBootTest`를 붙여도 운영 Supabase에는 붙지 않는다
- Testcontainers 쪽 datasource URL엔 `currentSchema=finready`가 필요하다.
  `application.yaml`의 `flyway.schemas: finready` 때문에 테이블이 `public`이 아니라
  `finready` 스키마에 생기는데, 컨테이너 JDBC URL은 기본이 `public`이라 안 붙이면
  방금 만든 테이블이 안 보인다
- 신규 통합 테스트는 `@Tag("integration")` — `AbstractPostgresIntegrationTest`가 클래스에
  붙여두므로 상속만 하면 자동으로 붙는다. `test` 태스크가 이 태그를 제외한다
- 평가 모듈만 `@Tag("evaluation")`으로 분리해 `./gradlew evaluate`로 돌린다

## Docker

용도가 둘이다. **배포용 `Dockerfile`과 테스트용 Testcontainers는 별개다.**

- `Dockerfile` — Render 빌드 전용. 로컬에서 이걸 직접 실행할 일은 없다
- Testcontainers — `integrationTest` 태스크가 PostgreSQL 컨테이너를 띄운다. Docker Desktop이
  떠 있어야 한다. 아티팩트명이 Testcontainers 2.x부터 `testcontainers-junit-jupiter` /
  `testcontainers-postgresql`로 바뀌었다(단독 `junit-jupiter`/`postgresql` 아님) — Boot
  4.0.7 BOM이 관리하는 `testcontainers-bom` 버전이 2.0.5라 구버전 아티팩트명 예제를
  그대로 쓰면 `Could not find` 로 걸린다 (2026-08-14)

### 배포용 Dockerfile

`finready-backend/Dockerfile`이 Render 빌드에 쓰인다. 로컬에서는 실행할 일이 없다.

- 빌드 스테이지: `eclipse-temurin:25-jdk` + **Gradle Wrapper**
  (gradle 공식 이미지를 안 쓴 이유: 래퍼가 Gradle 버전을 저장소에 고정하므로
  로컬과 컨테이너 빌드가 항상 일치한다)
- 런타임 스테이지: `eclipse-temurin:25-jre`, 비-root 사용자
- Render가 주입하는 `PORT` 환경변수를 사용
- **모노레포 주의**: Render 서비스 설정에서 Root Directory를 `finready-backend`로
  지정해야 `COPY` 경로가 맞는다

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
- **로컬 DB 연결 성공** — Supavisor Session Pooler,
  user는 `finready_backend.{project-ref}` 형식 (접두사만 전용 role로 교체) (2026-08-12)
- **Flyway 마이그레이션 v1·v2 적용 완료** — 테이블 14개 + `flyway_schema_history` 생성 (2026-08-12)
- **IntelliJ 2026.2.1 Community로 교체** — 이전 2023.2.5의 번들 Kotlin 1.9.24가
  Gradle 9.5.1의 Kotlin 2.3.20 메타데이터를 못 읽어 `build.gradle.kts` 전체가
  빨간줄이었다. Boot 4가 요구하는 최소 Gradle(8.14)조차 Kotlin 2.0.21이라
  다운그레이드로는 해결 불가였다 (2026-08-12)
- **Render 배포 완료** — https://finready-backend.onrender.com
  (Singapore / Docker / Starter, Root Directory `finready-backend`).
  `/actuator/health` 200 확인. 배포 로그에서 Flyway `Current version: 2, up to date` —
  로컬에서 적용한 v1·v2를 그대로 인식했다는 뜻이라 스키마가 하나임이 확인됐다.
  `SPRING_PROFILES_ACTIVE`를 넣지 않아 **default 프로파일로 뜬다(의도한 동작)**.
  `application.yaml`의 `local` 문서가 안 걸리므로 로깅은 INFO/WARN이다 (2026-08-13)
- **JPA 엔티티 14개 + enum 16개** — 패키지 구조는 TRD §2.1
  (`product`·`session`·`coverage`·`understanding`·`explanation`·`audit`·`ai`·`common`).
  `local` 기동으로 `ddl-auto: validate` 통과 확인 = V1 DDL과 컬럼·타입·nullable 일치.
  `customer_profile`은 TRD §2.1 목록에 없어 시드라는 성격을 따라 `product/`에 뒀다 (2026-08-13)
- **시드 로더 + 검증기** (TRD §4.5) — `CommandLineRunner`가 검증 후 upsert.
  기동 로그 `시드 적재 완료 — product=PROD_A (A-2026-08-12-01), risk 9건, customerProfile 3건`.
  고객 preset은 `seed/customer_profiles.json` 별도 파일로 뒀다(`$schema`가 다르므로).
  `static/documents/PROD_A/v1.0` → **`v1.0.pdf`로 이름 변경** — `documentUrl`이
  `/documents/PROD_A/v1.0.pdf`라 그대로 두면 프론트의 PDF 요청이 404다 (2026-08-14)
- **F01 `GET /api/products/demo` + `common/` 오류 규약** — `ErrorCode`(계약 18값,
  HTTP 상태·`recoverable`을 코드마다 보유) / `ErrorResponse` / `ApiException` /
  `GlobalExceptionHandler` / `RequestIdFilter` / `WebConfig`(CORS).
  응답은 엔티티가 아니라 `DemoProductResponse`로 변환한다 — `document_sha256` 같은
  계약 밖 컬럼이 새지 않게 (2026-08-14)
- **세션 / Revision / StateMachine** (TRD §5.1~5.3) — `common/StateMachine`에 전이표 전체.
  `POST /api/sessions`, `POST /api/sessions/{id}/revisions`(F02), `GET /api/sessions/{id}`.
  상태 변경은 `session.transitionTo(to, stateMachine)` 하나뿐이다 — StateMachine을 인자로
  받게 해서 전이표를 건너뛸 수 없게 만들었다(규칙 7).
  `GET`의 `coverage`·`nextAction`·`understanding`은 계약이 null/빈 배열을 허용해
  지금은 그대로 내보낸다. F03·F04에서 채운다 (2026-08-14)
- **테스트 3종 도입** — `StateMachineTest`(전이표 52+15조합 전수),
  `SessionServiceTest`(revision 채번·중복·검증 순서), `SessionControllerTest`(계약 필드명·오류 스키마).
  `D:\dev\finready`에서 `gradlew test` 재실행 — 6개 스위트 101건 전수 통과,
  argfile 인코딩 문제 재발 없음 확인 (2026-08-14)
- **Testcontainers 스캐폴딩** — `./gradlew integrationTest` 신설 태스크(기본 `test`와 분리,
  `@Tag("integration")` excludeTags로 격리). `AbstractPostgresIntegrationTest` 베이스 +
  `SchemaConstraintIntegrationTest` 스모크 2건: ① Flyway V1+V2가 실 Postgres에 적용된
  스키마로 `ddl-auto: validate` 통과, ② `audit_event` INSERT 성공 / UPDATE는
  `[23001]`로 트리거가 차단 — 둘 다 지금까지 로컬 기동으로 수기 확인했던 것을
  CI 회귀로 전환. F03 리포지토리·서비스 코드는 아직 없음, 이건 인프라만 (2026-08-14)
- **`revisionNo` 경쟁 상태 — (1)안 적용** (`docs/decisions/2026-08-14-...`). 경쟁 자체는
  그대로 두고 실패의 모양만 고쳤다: `uq_revision` 위반이 500 `INTERNAL_ERROR`(재시도 불가)로
  나가던 것을 409 `CONCURRENT_SESSION_UPDATE`(recoverable)로. **제약 위반을 뭉뚱그리지
  않는다** — `ck_*` 위반은 애플리케이션이 DB 규칙을 우회했다는 뜻이라 500으로 남긴다.
  제약 이름 추출은 `common/ConstraintNames`(JPA는 Hibernate 예외, JdbcTemplate은 메시지 폴백).
  `RevisionConcurrencyIntegrationTest`가 실 Postgres로 재현 (2026-08-18)
- **F03 Coverage 코어 (LLM 비의존 부분)** — 모델 선정(D-02)을 기다리지 않아도 되는 것만 먼저.
  `OffsetMapper`(정규화↔원문 UTF-16 인덱스 맵, 규칙 4의 재계산 지점) /
  `ProvenanceVerifier`(EMPTY·TOO_SHORT·TOO_LONG·NOT_FOUND·AMBIGUOUS) /
  `CoverageStatusResolver`(openapi 결정표) / `CoverageResultFactory`(엔티티 생성자를
  직접 부르지 않게 하는 유일한 경로) / `GateEvaluator`+`GateStatus` /
  `CoverageClassifier`(포트만, 구현체 없음) (2026-08-18)
- **`ck_explained_requires_verification`의 NULL 구멍 수정 (V3)** —
  `semantic_relation IS NULL`인 EXPLAINED를 막지 못하고 있었다. Postgres CHECK는 결과가
  FALSE일 때만 거부하는데 `NULL = 'SUPPORTS'`가 NULL이라 제약 전체가 NULL로 평가됐다.
  **규칙 3의 "DB check 제약으로도 강제돼 있다"가 이 경우 사실이 아니었다.**
  `is not distinct from`으로 교체. 경위는 `docs/decisions/2026-08-18-explained-constraint-null-hole.md`
  (2026-08-18)
- **평가 데이터셋 `CONS_A_002`~`006` 본문 작성** — 라벨(`coverageGroundTruth`)에 맞춰
  9개 Risk의 `fact` 요소를 넣고 뺐다. `DemoSeedGateConsistencyTest`가 라벨 ↔
  `expectedGateResult`를 `GateEvaluator`로 재계산해 대조한다 — 시나리오가 60건까지
  늘어날 때 사람 눈으로는 유지되지 않는 검증이다 (2026-08-18)
- **F03 파이프라인 (LLM 무관 부분 전체)** — `CoverageAnalysisService` /
  `CoverageWriter` / `CoverageController`(`POST /sessions/{id}/coverage`,
  `POST /sessions/{id}/gate-override`) / `CoverageResponse` /
  `CoverageResultRepository`·`GateOverrideRepository` / `SemanticVerifier` 포트.
  **규칙 6 때문에 서비스에 `@Transactional`이 없다** — 읽기 → LLM 호출(트랜잭션 밖) → 쓰기이고,
  쓰기만 `CoverageWriter`가 묶는다. 같은 클래스 안에서 `@Transactional` 메서드를 자기 호출하면
  프록시를 안 타 트랜잭션이 아예 안 걸리므로 별도 빈으로 뺐다.
  멱등: 같은 revision에 결과가 있으면 LLM을 다시 부르지 않는다(새로고침이 요금을 다시 물지 않게).
  `ai/AiPortConfig`가 `@ConditionalOnMissingBean` 스텁을 등록해 **LLM 없이도 기동**하되,
  호출되면 설정 누락을 명시적으로 알린다 — 빈 결과를 돌려주면 전 Risk가 "설명 안 됨"으로
  읽혀 Gate가 잠긴다 (2026-08-18)
- **F04 질문 발급 + F05 답변 판정(attempt 1)** — `POST /sessions/{id}/questions`,
  `POST /sessions/{id}/understanding`. F03과 같은 트랜잭션 구조(`UnderstandingWriter`).
  · `understanding/WorkflowStateMachine` — Risk 단위 전이표. TRD §4.2 "갱신 일원화"를
  `ConsultationSession.transitionTo`와 같은 패턴으로 강제한다(상태머신을 인자로 받게 해
  전이표를 건너뛸 수 없게). 세션 상태머신과 **합치지 않았다** — 축이 다르다
  · `understanding/NextActionResolver` — 계약 표(TRD §6.6)를 그대로. 프론트가 이 값만 보고
  분기하므로(규칙 8) 한 곳에만 둔다. **UNCERTAIN은 REEXPLAIN으로 가지 않는다**(PRD §7.5) —
  헷갈리기 쉬워 테스트로 고정
  · attempt는 **경로가 정한다**(`/understanding`=1, `/recheck`=2). 클라이언트가 보내지
  않으므로 2를 1로 바꿔 재시도할 수 없다
  · 질문 생성 실패는 **정상 경로**다 — 검수 `fallbackQuestion`으로 대체 + `source: FALLBACK`.
  그래서 `QuestionGenerator` 스텁만 예외적으로 빈 결과를 돌려준다(던지면 F04를 못 돌려본다)
  · Override로 제외된 Risk도 `COMPLETE/SKIPPED_BY_OVERRIDE`로 기록한다 — 리포트에서
  "왜 안 물었나"가 보여야 한다 (2026-08-18)

### 검증한 것 (2026-08-12)
- V1 테이블 14개가 TRD §4.1 목록과 이름 일치
- 시드 risk 9건 정책이 PRD §5 정책표와 일치
  (R01–R03 GATE_REQUIRED+understandingCheck, R04·R08 GATE_REQUIRED, R05–R07·R09 WARN_ONLY)
- PDF SHA-256이 기재값과 일치
- V2 트리거가 `before update or delete`만 잡고 INSERT를 넣지 않음 (TRD §4.4가 경고한 사고 회피됨)
- JDK 25로 `gradlew build` BUILD SUCCESSFUL
- **append-only 트리거 실검증**: `audit_event` INSERT 성공 /
  UPDATE는 `[23001] append-only table: audit_event`로 차단됨 (TRD §4.4 충족)
- **스키마 격리**: 마이그레이션·`flyway_schema_history` 모두 `finready` 스키마에 생성.
  `public`의 기존 앱 테이블은 건드리지 않음

### 검증한 것 (2026-08-14)
- **F01 응답 ↔ openapi v1.4.2 대조**: `product` 7필드 일치,
  `understandingCheckRiskIds`=`["R01","R02","R03"]`, `customers` 3건,
  risks 9건의 정책 분포가 PRD §5 정책표와 일치
- **계약 밖 컬럼 미노출 확인**: 응답에 `documentSha256`·`isLiveDemo`가 없다.
  엔티티 직렬화였으면 그대로 샜다
- `X-Request-Id` 헤더 존재(RequestIdFilter 작동), `Vary: Origin`(CORS 활성)
- **PDF 서빙**: `/documents/PROD_A/v1.0.pdf` 200 + `Content-Type: application/pdf`.
  확장자 없는 `v1.0`이었으면 `application/octet-stream`으로 나가 브라우저가
  다운로드로 처리했을 것
- 미검증 경로: 시드에 `NOT_APPLICABLE` Risk가 없어 `ProductQueryService`의
  해당 필터가 실제로 걸러낸 적이 없다

### 실측한 것 (2026-08-18, 실 LLM 호출)

상세는 `docs/decisions/2026-08-18-coverage-prompt-tuning.md`. **다시 헤매지 않으려면
프롬프트를 손대기 전에 그 문서부터 볼 것.**

- **비용**: Coverage 2호출 웜 캐시 **$0.019** / 콜드 $0.035. 세션 전체 추정 ~$0.047 →
  **$5 크레딧으로 약 100세션.** 착수 전 추정($0.2~0.4)이 자릿수로 틀렸다 — 한국어 토큰을
  과대평가했다(실측 대략 1글자=1토큰). **실험을 아낄 이유가 없다**
- **캐시 작동 확인**: `cacheWrite=2969 → cacheRead=2969` 완전 적중. 44% 절감.
  Risk 카탈로그를 정렬 순서로 system에 둔 설계가 유효하다.
  ⚠️ **기본 TTL 5분** — 심사처럼 띄엄띄엄 오면 매번 쓰기만 물 수 있다(배포 전 결정)
- **웜 상태 비용의 70%가 출력 토큰**이다. 입력은 캐시로 거의 사라졌고, 줄일 곳은 evidence 인용문이다
- **레이턴시**: 웜에서 classifier 10.8s(TRD §14 예산 12s 충족), 합계 15.5s.
  단 **실행 간 편차가 프롬프트 효과보다 크다**(같은 프롬프트로 verifier 5.0~12.0s).
  **n=1로 레이턴시를 판단하지 말 것** — 이 함정에 한 번 빠져 잘못된 원인 진단을 했다
- **판정 안정성**: `temperature: 0`인데도 **경계 케이스는 실행마다 바뀐다**(R02·R03).
  명확한 판정(R01·R04·R05·R07~R09)은 3회 안정. 무작위가 아니라 경계에 집중된 흔들림이다
- **Gate 결과는 3회 모두 정확**했다. 개별 Risk가 어긋나도 제품 판단은 맞았다 —
  **평가 지표를 개별 Risk 정확도만으로 잡으면 이 사실이 안 보인다**
- **`CONS_A_003` 함정 검출 확인** — 오도 설명("노낙인이라 사실상 원금은 지켜진다")에서
  R01을 `CONTRADICTED`로 판정하고 해당 문장을 정확히 인용했다. **2회 실행 결과 9개 Risk 전부 동일.**
  라벨(`INSUFFICIENT`)보다 정확해서 **라벨을 `CONTRADICTED`로 정정**했다
- **불안정성은 모델이 아니라 입력이 모호할 때 나타난다.** `CONS_A_003`은 완전히 안정적이고
  `CONS_A_001`만 흔들렸다 — 후자는 "언급이 아예 없는" 항목이 많아 모델이 매번
  "이 애매한 문장을 근거로 볼 것인가"를 다시 판단해야 했다
- **레이턴시·비용은 출력 토큰에 비례한다.** EXPLAINED가 많을수록 인용문이 길어진다.
  `CONS_A_001` classifier 10.8s / $0.019 vs `CONS_A_003` 14.3s / $0.030 —
  **TRD §14 예산(12초) 충족 여부가 상담문 내용에 달렸다**

### 다음 순서
1. ~~F03~~ **완료** (2026-08-18). 파이프라인 + 구현체 4개 + 프롬프트 튜닝 + 실 LLM 검증(`CONS_A_001`·`CONS_A_003`)
   → **모델 결정됨: `claude-sonnet-4-6`** (2026-08-18, TRD D-02 해소). SDK는
   `com.anthropic:anthropic-java`, 설정은 `ai/AiProperties`(`finready.ai.*`)
   → **Sonnet 4.6은 structured outputs를 지원하지 않는다** (지원: Fable 5 / Opus 5 /
   Opus 4.8 / Sonnet 5 / Haiku 4.5). `output_config.format`으로 enum을 API 계층에서
   강제할 수 없으므로 **프롬프트로 JSON을 유도하고 직접 파싱 + 방어적 검증**해야 한다.
   규칙 9의 보장이 API가 아니라 우리 코드에 있다 — `indexExactly`·`validate`가 이미
   `AI_PARSING_FAILED`를 던지므로 구조는 그대로 쓴다
   → `temperature: 0`은 4.6에서 유효하다. **Opus 4.7+ / Sonnet 5로 올리면 400이므로
   그때 지울 것**
   → prompt caching·effort 모두 적용 완료. 비용·레이턴시 실측은 위 "실측한 것" 참조
   → **Verifier 대상은 계약 문구("GATE_REQUIRED + CONTRADICTED")보다 넓다** —
   `EXPLAINED` 후보도 돌린다. 규칙 3 때문에 EXPLAINED는 `semantic = SUPPORTS` 없이
   성립하지 않아서, 안 돌리면 잘 설명한 WARN_ONLY Risk가 INSUFFICIENT로 접혀 경고로 둔갑한다
   (`CONS_A_003`의 R06에서 실측). `CoverageAnalysisServiceTest`가 이 동작을 고정하므로
   **계약 문구만 보고 되돌리면 테스트가 깨진다.** 경위는
   `docs/decisions/2026-08-18-explained-constraint-null-hole.md` "해소됨"
2. **F06 재설명 + F07 recheck·직원 처리**
   → F04/F05는 완료. `WorkflowStateMachine`·`NextActionResolver`·`UnderstandingWriter`가
   이미 있으니 그 위에 얹는다
   → `/recheck`는 `UnderstandingService.judge(..., RECHECK_ATTEMPT)`를 그대로 재사용하면
   된다 — attempt만 다르다
   → `/reexplain`은 응답에 **후속 질문(attempt 2)을 함께 싣고 영속화**해야 한다.
   그래야 새로고침 후 `pendingQuestion`으로 복구된다
   → Guardrail 금칙어 목록이 미결정이다 (TRD D-04)
3. Report + Close + Audit (F08)
   → `ConsultationSession.closedAt`·`closedBy`·`unresolvedReason`을 채우는 경로가 아직 없다
4. 오프라인 평가 모듈 + Rule baseline

리포지토리는 `product`·`product_risk`·`customer_profile`·`consultation_session`·
`consultation_revision`·`coverage_result`·`gate_override`·`session_question`·
`understanding_result`·`risk_workflow_state` 10개가 있다.
나머지(`staff_resolution`·`re_explanation`·`audit_event`·`llm_call_log`)는 해당 기능 작업에서 만든다.

> **병행(배포 연동)**: F01이 생겨 프론트가 붙을 수 있다. 프론트에 배포 URL 전달 →
> 프론트 `NEXT_PUBLIC_API_BASE_URL=https://finready-backend.onrender.com/api`,
> 백엔드 `CORS_ALLOWED_ORIGINS`에 프론트 배포 도메인 추가.
> CORS는 이제 `common/WebConfig`가 이 설정을 실제로 읽는다. 기본값이
> `http://localhost:3000`이라 배포 도메인을 안 넣으면 배포 프론트에서 막힌다.

> **TRD §18 Step 1 DoD 충족.** `연결 + Flyway + 시드 로더 + GET /products/demo` +
> §3.4 검증 5항목이 모두 끝났다. 다음 순서 1번부터는 Step 2다.

### 데이터셋 현황 (별도 작업, 코드와 병행)
- 상담 시나리오 6 / 목표 60 — **6건 모두 본문 작성 완료** (2026-08-18).
  `DemoSeedGateConsistencyTest`가 라벨↔기대 Gate 정합성을 자동 검증하므로
  시나리오를 추가할 때 라벨만 맞으면 어긋남이 바로 걸린다
- 고객 답변 12 / 목표 180
- 라벨을 먼저 정하고 상담문을 생성하는 방식. 사후 라벨링 비용이 0이다

## 미결정

- ~~LLM 모델·요금제~~ — **`claude-sonnet-4-6` 결정** (2026-08-18).
  실측 결과 $5로 약 100세션 — **quota는 빠듯하지 않다**(위 "실측한 것")
- **캐시 TTL을 5분에서 1시간으로 올릴지** — 심사처럼 세션이 띄엄띄엄 오면 5분 TTL은
  매번 쓰기(1.25배)만 물고 읽기 혜택이 없다. 1시간은 쓰기 2배지만 유지된다.
  배포 전 결정 (`docs/decisions/2026-08-18-coverage-prompt-tuning.md`)
- ~~`CONS_A_001`의 R06 라벨~~ — **라벨이 아니라 코드 문제였다** (2026-08-18).
  Verifier를 안 돌린 EXPLAINED가 규칙 3 때문에 접히던 것 → Verifier 대상에 EXPLAINED 추가로 해소
- **`CONS_A_001`의 R02·R03 불안정** — 실행마다 뒤집힌다. 입력이 모호한 케이스의 한계로 보이며
  프롬프트로 더 잡을 수 있을지 불확실하다. **Gate 결과는 3회 모두 정확했으므로 우선순위가 낮다**
- Guardrail 금칙어 최종 목록 — TRD D-04, Step 7 결정
- `customerProfile` 프로덕션 시드 배치 방식 (별도 파일 vs risk schema에 병합)
- **`resumePoint` 매핑을 프론트 화면 정의와 대조할 것.** TRD에 규정이 없다 —
  §6.6은 Understanding 단계의 `nextAction` → 화면만 정한다.
  현재 매핑(DRAFT→S02 / COVERAGE_ANALYZED·GATE_BLOCKED→S03 / UNDERSTANDING_IN_PROGRESS→S04 /
  AWAITING_STAFF_REVIEW→S07 / CLOSED_*→S08)은 `SessionService.resumePointOf`에 있고
  `SessionServiceTest`가 고정해뒀다. 프론트가 API 연결을 시작하기 전에 맞출 것
- `CoverageResult`·`SessionQuestion`에 `@Immutable`을 붙일지.
  TRD §4.2("정정 시 이전 행을 지우지 않는다")와 §4.6("멱등 발급")을 그대로 읽으면
  행 전체가 append-only다. 다만 규칙 1은 `classifier_status`·`ai_status` 두 컬럼만
  명시하므로 지금은 `updatable = false`까지만 걸어뒀다.
  `ConsultationRevision`·`AuditEvent`는 근거가 명확해 이미 `@Immutable`이다

## 알려진 문제

- **`revisionNo` 채번 경쟁 상태** — `docs/decisions/2026-08-14-revision-no-race-condition.md`.
  **경쟁 자체는 여전히 남아 있다**(의도적 보류. P0가 단일 사용자 데모라 실질 위험이 낮다).
  다만 (1)안을 적용해 실패가 409 `CONCURRENT_SESSION_UPDATE`(recoverable)로 나가므로
  프론트가 재시도할 수 있다. 근본 해결(재시도 루프·행 락·DB 채번)은 결정 문서의
  "위험이 현실화되는 조건"에 해당할 때 재검토
- **Verifier 대상 범위 미정** — WARN_ONLY Risk가 잘 설명됐어도 semantic 검증을 안 돌리면
  규칙 3 때문에 INSUFFICIENT로 접혀 불필요한 경고가 뜬다. 계약 결정표와 DB 제약이
  어긋나는 지점이며 LLM 모델 결정과 함께 판단해야 한다.
  `docs/decisions/2026-08-18-explained-constraint-null-hole.md` "남은 질문"

## 처리 대기 (문서 동기화)

- TRD §1 기술스택 표 정정 (Java 21/Boot 3.x → Java 25/Boot 4.0.7) → v1.2.4 — PDF 원본,
  코드로 처리 불가. 다음에 TRD 직접 열 때 반영할 것
- ~~`finready-frontend/contracts/openapi.yml` v1.4.1 → v1.4.2 동기화~~ — 완료 (2026-08-14).
  `docs/openapi.yml`을 그대로 덮어썼다. 두 파일 diff 없음 확인
- PRD §12에 `POST /api/sessions/:id/recheck` 추가 (TRD §22-1) — PDF 원본, 코드로 처리 불가
- PRD §17-3 "Coverage Hold-out" → "Coverage dev set" 정정 (TRD §22-2) — PDF 원본, 코드로 처리 불가