# FinReady 백엔드 작업 노트

2026-08-13 ~ 08-14 작업분. 엔티티 14개 + 시드 로더까지.

무엇을 만들었는지보다 **왜 그렇게 했는지**를 남기는 문서다.
코드만 봐서는 안 보이는 판단 근거가 여기 있다.

관련 커밋: `6be0b65` (엔티티), `2b5fc1a` (문서)
시드 로더는 아직 커밋 전이다.

---

# 1. 파일 목록과 역할

## 1.1 엔티티 14개 — DB 테이블 1:1 대응

| 패키지 / 파일 | 역할 |
|---|---|
| `product/Product` | 상품. id가 시드 지정 문자열이라 생성 전략 없음 |
| `product/ProductRisk` | 검수된 Risk 9건. 런타임에 수정되지 않는 시드 데이터 |
| `product/CustomerProfile` | 데모용 고객 preset. `created_at` 컬럼이 없다 |
| `session/ConsultationSession` | 상담 세션. `@Version` 낙관적 락이 붙은 유일한 엔티티 |
| `session/ConsultationRevision` | 상담 원문 스냅샷. `@Immutable` |
| `coverage/CoverageResult` | Risk별 커버리지 판정. AI 원판정과 검증 후 값을 별도 컬럼으로 보관 |
| `coverage/GateOverride` | 직원의 Gate 예외 처리. 원판정을 고치지 않고 새 행으로 남긴다 |
| `understanding/SessionQuestion` | 발급된 질문의 단일 진실. 멱등 발급 |
| `understanding/UnderstandingResult` | 고객 답변에 대한 AI 이해 판정 |
| `understanding/RiskWorkflowState` | Risk별 workflow 상태. `updated_at`만 있고 `created_at`이 없다 |
| `understanding/StaffResolution` | 직원 최종 판단. AI 판정을 덮지 않고 새 행 |
| `explanation/ReExplanation` | 쉬운 말 재설명 + guardrail 위반 기록 |
| `audit/AuditEvent` | 감사 로그. `@Immutable` + V2 트리거로 이중 차단 |
| `ai/LlmCallLog` | LLM 호출 기록. `session_id`가 nullable(세션 밖 평가 호출 허용) |

## 1.2 enum 16개 — DDL `check` 제약을 Java 타입으로

| 패키지 | enum |
|---|---|
| `product/` | `CoveragePolicy`, `InvestmentExperience`, `FinancialLiteracy`, `ExplanationLevel` |
| `session/` | `SessionStatus` (7값) |
| `coverage/` | `CoverageStatus`, `ProvenanceFailureReason`, `SemanticRelation`, `OverrideCategory` |
| `understanding/` | `UnderstandingStatus`, `AnswerSource`, `WorkflowStatus`, `FinalDisposition`, `StaffDisposition` |
| `common/` | `GenerationSource` (understanding·explanation 공용) |
| `audit/` | `ActorRole` |

`event_type`, `stage`, `product_risk.category`, `age_group`은 **DDL에 check 제약이 없어 String**이다.
enum으로 올리면 코드만 값을 막고 DB는 아무 문자열이나 받아 — 다른 enum과 강제 수준이 어긋난다.

## 1.3 시드 로더 11개 + 리소스

| 파일 | 역할 |
|---|---|
| `product/ProductRepository` | `JpaRepository<Product, String>` |
| `product/ProductRiskRepository` | 조회 + `deleteByProductId` (재시드용) |
| `product/CustomerProfileRepository` | `JpaRepository<CustomerProfile, String>` |
| `product/ProductSeedDocument` | 시드 JSON 루트 (record) |
| `product/ProductSeedData` | `product` 블록 |
| `product/RiskSeedData` | `risks[]` 항목 |
| `product/CustomerProfileSeedDocument` | 고객 시드 루트 |
| `product/CustomerProfileSeedData` | 고객 preset 한 건 |
| `product/SeedValidator` | TRD §4.5 검증 4항목 + 추가 2항목 |
| `product/SeedLoader` | `CommandLineRunner`. 검증 → upsert |
| `product/SeedValidationException` | 검증 실패. fail-fast면 기동 중단 |

**리소스**
- `seed/customer_profiles.json` (신규) — CUST_A/B/C 3건
- `application.yaml` — `finready.seed.customer-path` 추가
- `static/documents/PROD_A/v1.0` → **`v1.0.pdf`로 이름 변경**

패키지 배치는 TRD §2.1 그대로다. `SeedLoader`·`SeedValidator`가 `product/`에 있는 것도 TRD 명시다.

---

# 2. TRD/PRD에 없어서 판단한 것

문서에 답이 없어 내가 고른 것들이다. **다르게 갈 수 있었던 지점이므로 이견이 있으면 지금 바꾸는 게 싸다.**

## 2.1 연관관계를 객체가 아니라 스칼라 FK로

**선택지**
- (A) `@ManyToOne(LAZY) Product product` — JPA 교과서 방식
- (B) `String productId` — 선택함

**이유**: `open-in-view: false`이고 LLM 호출이 트랜잭션 밖에서 일어나야 하므로(규칙 6),
지연 프록시가 트랜잭션 밖으로 새면 `LazyInitializationException`이 난다.
이 프로젝트는 그 상황이 **예외가 아니라 기본 구조**다. 상세는 5장.

**되돌리는 비용**: (B)→(A)는 컬럼이 이미 있어 마이그레이션 불필요. (A)→(B)는 호출부 전수 수정.
싼 쪽을 기본값으로 잡았다.

## 2.2 상태 전이 메서드를 만들지 않음

**선택지**
- (A) `session.close()` 같은 도메인 메서드를 지금 추가
- (B) 아무것도 안 넣고 StateMachine 작업까지 미룸 — 선택함

**이유**: 규칙 7이 "전이는 `common.StateMachine` 단일 지점을 통과"인데 StateMachine이 아직 없다.
먼저 만들면 우회 경로가 자리잡고, 나중에 "이미 있는 걸 왜 안 쓰냐"가 된다.

**대상**: `ConsultationSession`, `RiskWorkflowState`

## 2.3 `StaffDisposition`을 `FinalDisposition`과 별도 enum으로

**선택지**
- (A) `FinalDisposition` 재사용 (2값은 4값의 부분집합)
- (B) 2값짜리 별도 enum — 선택함

**이유**: (A)면 `AUTO_RESOLVED`를 넣는 코드가 **컴파일에 통과하고 INSERT에서야** 터진다.
(B)면 타입 시스템이 `ck_staff_disposition`을 대신 지킨다.

## 2.4 `GenerationSource`를 `common/`에 배치

TRD §2.1의 `common/` 목록에는 enum이 없다. 하지만 `understanding`과 `explanation`이 같은 값을 쓴다.
패키지마다 복제하면 TRD §6 값이 갈라질 위험이 있어 한 곳에 뒀다.

## 2.5 `CustomerProfile`을 `product/`에 배치

TRD §2.1 목록에 `CustomerProfile`이 아예 없다. 시드 데이터라는 성격을 따라
`SeedLoader` 옆(`product/`)에 뒀다. `session/`도 후보였다(세션이 참조하므로).

## 2.6 고객 시드를 별도 파일로

**선택지**
- (A) `product_a_risk_schema.json`에 `customerProfiles` 추가 — `$schema` v2→v3 필요
- (B) `seed/customer_profiles.json` 별도 파일 — 선택함

**이유**: 고객 preset은 product risk schema가 아니다. 파일이 분리되면 스키마 버전을 안 건드리고,
상품 시드와 고객 시드의 생명주기가 묶이지 않는다.

**부작용**: `eval/demo_seed.json`에도 같은 3건이 있다. **두 파일이 갈라질 수 있다.** (7장 참조)

## 2.7 PDF 파일명을 `v1.0` → `v1.0.pdf`로 변경

원래 파일에 확장자가 없었고 시드의 `documentUrl`은 `/documents/PROD_A/v1.0.pdf`였다.

**선택지**
- (A) 로더에서 `.pdf`를 떼는 규칙 추가
- (B) 파일 이름 변경 — 선택함

**이유**: 해시 검증만의 문제가 아니다. **프론트가 `documentUrl`을 그대로 HTTP로 요청**한다.
(A)면 `GET /documents/PROD_A/v1.0.pdf`가 404라 근거 페이지 표시(S03)가 통째로 안 된다.

내용이 안 바뀌므로 SHA-256은 동일하다(확인함).

## 2.8 Risk를 개별 갱신이 아니라 통째로 교체

TRD는 "upsert"라고만 한다. 구현은 `deleteByProductId` → `flush()` → 9건 INSERT다.

**이유**: 시드에서 Risk가 **빠진** 경우까지 한 방식으로 처리된다.
`product_risk.id`를 참조하는 테이블이 없어 안전하다(다른 테이블은 `risk_id` 문자열만 들고 있다).

**함정**: `flush()`가 필수다. Hibernate는 기본적으로 INSERT를 DELETE보다 먼저 내보내서
`uq_product_risk`에 걸린다.

## 2.9 검증 항목 2개 추가

TRD §4.5는 4항목만 요구한다. 여기에 2개를 더 넣었다.

- `riskId` 중복 (`uq_product_risk` 선반영)
- `fallbackRecheckQuestion != fallbackQuestion` (`ck_recheck_question_differs` 선반영)

**이유**: 둘 다 DDL이 막지만, INSERT에서 터지면 **어느 Risk가 문제인지 안 나온다.**
검증 단계에서 잡으면 riskId까지 찍힌다.

## 2.10 검증 실패를 모아서 보고

하나 발견하고 바로 던지지 않고 전부 수집해 한 번에 보고한다.
시드를 고칠 때 "고치고 재기동"을 줄이려는 목적이다.

## 2.11 시드 DTO에서 enum을 String으로 받음

`coveragePolicy`를 enum으로 받으면 잘못된 값이 **Jackson 파싱 단계**에서 터져
"어느 Risk의 어느 필드"인지 못 알려준다. String으로 받고 `SeedValidator`가 판정한다.

---

# 3. 지금 이해 못 하면 나중에 곤란한 것 3가지

## 3.1 `ddl-auto: validate` — 기동이 곧 검증이다

`gradlew build`는 **자바 문법만** 본다. Java는 DB를 모른다.
엔티티가 DB와 맞는지는 **기동해봐야만** 안다.

실제로 이렇게 걸렸다:

```
Schema validation: wrong column type encountered in column [document_sha256] in table [product];
found [bpchar (Types#CHAR)], but expecting [char(64) (Types#VARCHAR)]
```

컴파일은 멀쩡했다. 원인은 `columnDefinition`이 **DDL 생성용이라 JDBC 타입 코드를 못 바꾼다**는 것.
`@JdbcTypeCode(SqlTypes.CHAR)`로 해결했다.

**따라서**
- 엔티티를 건드렸으면 반드시 기동해본다
- Hibernate는 불일치를 **한 번에 하나만** 보여준다. 여러 개 고칠 땐 반복이 필요하다
- 스키마를 바꾸려면 Flyway 마이그레이션을 추가한다. 엔티티를 고쳐서 DB를 맞추는 방향은 없다

## 3.2 트랜잭션 경계와 프록시

**세 가지가 얽혀 있다.**

**(1) `open-in-view: false`** — 영속성 컨텍스트가 `@Transactional` 메서드 끝나는 순간 닫힌다.
그 뒤에 지연 프록시를 건드리면 `LazyInitializationException`.
(Spring 기본값 `true`를 끈 이유: 커넥션 풀이 5개뿐인데 HTTP 응답 끝까지 커넥션을 붙든다)

**(2) 규칙 6 — LLM 호출은 트랜잭션 밖**
DB role에 `idle_in_transaction_session_timeout=30s`가 걸려 있다.
6~12초짜리 LLM 호출을 트랜잭션 안에서 하면 DB가 세션을 끊는다.
그래서 "트랜잭션 밖에서 엔티티를 만지는" 코드가 구조적으로 계속 나온다.

**(3) 자기 호출은 `@Transactional`이 안 먹는다**
실제로 이 세션에서 낸 버그다.

```java
public void run(...) {
    apply(...);          // 같은 클래스 메서드 직접 호출
}

@Transactional           // ← 프록시를 안 거치므로 무효
private void apply(...) { ... }
```

Spring은 **프록시 객체**를 통해 들어올 때만 트랜잭션을 건다.
`run()`으로 어노테이션을 옮겨 고쳤다. **컴파일로는 절대 안 잡힌다.**

리포지토리를 붙이는 다음 작업부터 이 세 가지에 계속 부딪힌다.

## 3.3 AI 원판정은 고치지 않는다 (규칙 1·2)

이게 이 프로젝트의 **제품 주장 그 자체**다. "AI가 이렇게 판정했고, 사람이 이렇게 뒤집었다"를
둘 다 남겨야 상담 기록으로서 의미가 있다. 덮어쓰면 남는 게 없다.

**코드에 어떻게 박혀 있나**

```java
@Column(name = "classifier_status", ..., updatable = false)   // CoverageResult
@Column(name = "ai_status", ..., updatable = false)           // UnderstandingResult
```

`updatable = false`면 Hibernate가 **UPDATE 문에서 그 컬럼을 아예 뺀다.**
setter가 없는 것만으로는 부족하다 — 나중에 누가 필드를 어떻게 건드리든 DB에 안 나간다.

**뒤집는 방법은 새 행 INSERT다**
- Coverage 판정 뒤집기 → `gate_override` INSERT
- 이해 판정 뒤집기 → `staff_resolution` INSERT

그리고 규칙 2: `effectiveStatus` 같은 **합성 상태를 저장하지 않는다.**
`classifierStatus`(원판정)와 `coverageStatus`(검증 후)를 별도 컬럼으로 둔다.
"지금 상태"가 필요하면 읽는 쪽에서 계산한다.

---

# 4. 안 끝난 / 불확실한 부분

## 4.1 시드 로더 기동 검증 — **아직 안 됨**

컴파일만 통과했다. 실제로 기동해서 아래를 확인해야 한다.

```
시드 적재 완료 — product=PROD_A (A-2026-08-12-01), risk 9건, customerProfile 3건
```

```sql
select count(*) from finready.product_risk;        -- 9
select count(*) from finready.customer_profile;    -- 3
```

## 4.2 미결정

| 항목 | 상태 |
|---|---|
| `CoverageResult`·`SessionQuestion`에 `@Immutable`을 붙일지 | TRD §4.2/§4.6은 행 전체 append-only로 읽히지만, 규칙 1은 두 컬럼만 명시. 지금은 `updatable=false`까지만 |
| LLM 모델·요금제 | TRD D-02. Coverage 작업 전 결정 필요 |
| Guardrail 금칙어 목록 | TRD D-04 |

## 4.3 의도적으로 미룬 것

- **리포지토리 11개** — 해당 기능 작업에서 만든다. 지금 만들면 어떤 쿼리가 필요한지 모른 채 껍데기만 생기고, 규칙 1의 "UPDATE 메서드 금지"를 어디에 적용할지도 그때 판단해야 한다
- **상태 전이 메서드** — StateMachine과 함께 (2.2)
- **`CoverageResult` 팩토리** — 현재 생성자는 `ck_provenance_consistency`와
  `ck_explained_requires_verification` 조합을 강제하지 않는다. 어긋난 조합은 INSERT에서 터진다.
  Verifier 작업에서 팩토리로 막아야 한다

## 4.4 확인이 필요한 것

- **`@Profile("!test")`의 실효성** — `@SpringBootTest`에서 `CommandLineRunner`가 실행되는지는
  Boot 버전마다 다르다. 테스트가 아직 0개라 검증 못 했다. 첫 테스트 작성 시 확인할 것
- **고객 시드 이중 관리** — `seed/customer_profiles.json`과 `eval/demo_seed.json`에
  같은 3건이 있다. 한쪽만 고치면 갈라진다. 평가 모듈 작업 때 한쪽을 참조하게 정리할 것

## 4.5 문서 빚 (CLAUDE.md "처리 대기")

- TRD §1 기술스택 표 정정 (Java 21/Boot 3.x → Java 25/Boot 4.0.7) → v1.2.4
- `finready-frontend/contracts/openapi.yml` v1.4.1 → v1.4.2 동기화
- PRD §12에 `POST /api/sessions/:id/recheck` 추가
- PRD §17-3 "Coverage Hold-out" → "Coverage dev set" 정정

## 4.6 일정

배포 동결이 **2026-09-06**이다. 남은 작업은 F01부터 리포트·감사·평가모듈까지 애플리케이션 계층 전부다.
심사에 필요한 최소 경로(F01 → 세션 → F03 Coverage/Gate → F08 리포트)를 먼저 관통시키고
평가모듈을 뒤로 미루는 판단이 필요해 보인다.

---

# 5. 부록 — `@ManyToOne`을 안 쓴 이유 상세

2.1의 배경. JPA에서 가장 많이 사고 나는 지점이라 따로 둔다.

## 5.1 프록시가 뭔가

`fetch = LAZY`는 `ProductRisk`를 조회할 때 **Product를 안 가져온다.**
대신 그 자리에 가짜 객체(프록시)를 넣는다.

```java
ProductRisk risk = repo.findById(1L);
// product 필드에는 껍데기만 있음. DB에 product 조회 안 함

risk.getProduct().getName();
// 이 시점에 SELECT * FROM product WHERE id=? 를 날림
```

**"나중에 DB에 물어본다"가 성립하려면 그때까지 DB 연결이 살아 있어야 한다.**

## 5.2 언제 죽나

| `open-in-view` | 프록시가 살 수 있는 기간 |
|---|---|
| `true` (Spring 기본값) | HTTP 요청 전체 |
| `false` (우리 설정) | `@Transactional` 메서드가 끝나는 순간까지 |

`false`에서 트랜잭션 밖의 프록시를 건드리면:

```
org.hibernate.LazyInitializationException: could not initialize proxy - no Session
```

## 5.3 이 프로젝트에서 왜 기본값이 되나

```java
@Transactional
public ConsultationSession loadSession(String id) {
    return sessionRepo.findById(id);
}   // ← 영속성 컨텍스트 닫힘

public void analyze(String sessionId) {
    var session = loadSession(sessionId);
    String name = session.getProduct().getName();   // LazyInitializationException
    llmClient.classify(name, ...);                  // 6~12초
}
```

보통의 CRUD 앱이면 트랜잭션 안에서 다 끝나서 이런 구조가 잘 안 나온다.
우리는 LLM 호출을 트랜잭션 밖으로 빼는 게 **강제**라 계속 나온다.

빠져나갈 구멍도 막혀 있다 — 트랜잭션을 길게 유지하면
`idle_in_transaction_session_timeout=30s`가 세션을 끊는다.

## 5.4 N+1 비용이 유난히 크다

```yaml
# 앱(Singapore) ↔ DB(Seoul) 왕복이 70~90ms다 (TRD §14.1)
default_batch_fetch_size: 100
```

Risk 9건을 순회하며 `getProduct()`를 부르면 SELECT 9번 = 약 720ms.
**로컬에서는 DB가 가까워 안 보이다가 Render(싱가포르)에 올리면 드러난다.**

## 5.5 잃지 않은 것

DDL의 외래키 제약은 그대로다.

```sql
product_id varchar(32) not null references product(id)
```

없는 `product_id`를 넣으면 DB가 거부한다. **참조 무결성은 안 잃었다.**
잃은 건 자바 객체에서 점 찍고 따라가는 편의뿐이다.

## 5.6 나중에 되돌리려면

```java
@Column(name = "product_id", insertable = false, updatable = false)
private String productId;              // 읽기 전용으로 남김

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_id")
private Product product;               // 추가
```

컬럼이 이미 있으니 마이그레이션 불필요. 기존 `getProductId()` 호출부도 그대로 돈다.

## 5.7 한 줄 요약

> 지연 로딩은 "나중에 DB에 물어보겠다"는 약속인데, 이 프로젝트는 LLM 호출 때문에
> 트랜잭션을 일찍 닫아야 해서 그 약속을 지킬 수 없는 순간이 구조적으로 계속 생긴다.
> 그래서 약속 자체를 안 한다.

---

# 6. 부록 — 코드 읽는 순서

1. `product/Product.java` — 가장 단순. 어노테이션 5개가 전부
2. `V1__init.sql`의 `create table product`와 나란히 대조 (필드 순서를 DDL 순서에 맞춰뒀다)
3. `product/CoveragePolicy.java` + DDL의 `ck_coverage_policy` — enum이 왜 있는지
4. `coverage/CoverageResult.java` — `updatable = false`. 규칙 1의 실체
5. `product/SeedValidator.java` — TRD §4.5가 코드로 어떻게 옮겨졌는지
6. `product/SeedLoader.java` — 트랜잭션 경계와 upsert

**DDL ↔ 엔티티 매핑 규칙**

| DDL | 엔티티 |
|---|---|
| `varchar(n)` | `@Column(length = n) String` |
| `text` | `@Column(columnDefinition = "text") String` |
| `int` not null / nullable | `int` / `Integer` |
| `smallint` | `short` |
| `bigserial` | `@GeneratedValue(IDENTITY) Long` |
| `timestamptz` | `OffsetDateTime` |
| `char(64)` | `@JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) String` |
| `check (x in (...))` | enum + `@Enumerated(EnumType.STRING)` |

**확인 명령**

```bash
grep -rn "void set" finready-backend/src/main/java     # setter 없음 확인 (결과 없어야 정상)
grep -rl "@Entity" finready-backend/src/main/java | wc -l   # 14
```
