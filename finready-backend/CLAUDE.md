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

### 다음 순서
1. **F03 마무리 — LLM 구현체 2개뿐**
   → 파이프라인은 **완료**됐다. `CoverageClassifier`·`SemanticVerifier` 구현체를
   `@Component`로 추가하면 `AiPortConfig`의 스텁이 자동으로 물러난다. 그 외에 고칠 코드는 없다
   → **LLM 모델·요금제 결정이 선행** (미결정, TRD D-02).
   잠정 후보는 Claude Sonnet 5 — structured outputs(`output_config.format` + `json_schema`의
   `enum`)로 "enum 밖 값 = 파싱 실패"(규칙 9)를 API 계층에서 강제할 수 있다.
   **`application.yaml`의 `finready.ai.temperature`는 그때 제거할 것** — Sonnet 5는
   비기본값 sampling 파라미터를 400으로 거부한다
   → 착수 시 결정할 것: **Verifier를 어떤 Risk까지 돌릴지.** 지금은 계약대로
   "GATE_REQUIRED + CONTRADICTED 후보 + provenance 통과"만 돌린다
   (`CoverageAnalysisService.selectVerifierTargets`, 이 메서드 하나만 고치면 된다).
   그러면 WARN_ONLY EXPLAINED가 semantic 없이 남아 규칙 3 때문에 INSUFFICIENT로 접힌다
   (= 불필요한 경고). 대안은 EXPLAINED 후보 전체를 돌리는 것이고 토큰이 늘어난다. 상세는
   `docs/decisions/2026-08-18-explained-constraint-null-hole.md` "남은 질문"
2. Understanding / 재설명 / Staff Resolution (F04~F07)
   → `RiskWorkflowState`에 상태 전이 메서드가 아직 없다. 갱신은 understanding 모듈에서
   일원화하라는 TRD §4.2대로 그때 설계할 것
3. Report + Close + Audit (F08)
   → `ConsultationSession.closedAt`·`closedBy`·`unresolvedReason`을 채우는 경로가 아직 없다
4. 오프라인 평가 모듈 + Rule baseline

리포지토리는 `product`·`product_risk`·`customer_profile`·`consultation_session`·
`consultation_revision`·`coverage_result`·`gate_override` 7개가 있다.
나머지(understanding·explanation·audit·ai 계열)는 해당 기능 작업에서 만든다.

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

- LLM 모델·요금제 (심사 5일 quota 산정 필요) — TRD D-02, Step 5 이전 결정
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