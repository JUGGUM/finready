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
- 로컬 개발에 Docker는 필요 없다. DB가 원격 Supabase라 띄울 컨테이너가 없다.
  `Dockerfile`은 Render 배포 전용이다(아래 참조).
- 테스트에서 실제 LLM을 호출하지 않는다. 평가 모듈만 `@Tag("evaluation")`으로 분리
- 큰 변경 전에는 계획을 먼저 제시하고 승인을 받을 것

## Docker (배포 전용)

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

### 다음 순서
1. **Render 배포 관통** (Root Directory `finready-backend`, Build Filter `finready-backend/**`)
   → 환경변수 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `CORS_ALLOWED_ORIGINS`
   → `/actuator/health` 200 확인 → 프론트에 배포 URL 전달
2. **JPA 엔티티 14개** — V1 DDL과 컬럼명·제약이 정확히 일치해야 함 (`ddl-auto: validate`)
3. **시드 로더 + 검증기** — TRD §4.5. 검증 실패 시 기동 중단
   → `customerProfile`이 현재 `demo_seed.json`(테스트 리소스)에만 있다.
   프로덕션 시드로도 필요하므로 배치 방식을 먼저 결정할 것
4. `GET /api/products/demo` (F01)
5. 세션 / Revision / StateMachine (TRD §5.1)
6. Coverage 4상태 + Provenance + OffsetMapper + Verifier + Gate + Override (F03)
7. Understanding / 재설명 / Staff Resolution (F04~F07)
8. Report + Close + Audit (F08)
9. 오프라인 평가 모듈 + Rule baseline

> TRD §18 Step 1 DoD는 `연결 + Flyway + 시드 로더 + GET /products/demo` +
> §3.4 검증 5항목까지다. 연결·Flyway·검증은 끝났고, 시드 로더와 F01이 위 2~4번에 남아 있다.

### 데이터셋 현황 (별도 작업, 코드와 병행)
- 상담 시나리오 6 / 목표 60 — `CONS_A_002`~`006`은 본문 미작성
- 고객 답변 12 / 목표 180
- 라벨을 먼저 정하고 상담문을 생성하는 방식. 사후 라벨링 비용이 0이다

## 미결정

- LLM 모델·요금제 (심사 5일 quota 산정 필요) — TRD D-02, Step 5 이전 결정
- Guardrail 금칙어 최종 목록 — TRD D-04, Step 7 결정
- `customerProfile` 프로덕션 시드 배치 방식 (별도 파일 vs risk schema에 병합)

## 처리 대기 (문서 동기화)

- TRD §1 기술스택 표 정정 (Java 21/Boot 3.x → Java 25/Boot 4.0.7) → v1.2.4
- `finready-frontend/contracts/openapi.yml` v1.4.1 → v1.4.2 동기화
- PRD §12에 `POST /api/sessions/:id/recheck` 추가 (TRD §22-1)
- PRD §17-3 "Coverage Hold-out" → "Coverage dev set" 정정 (TRD §22-2)