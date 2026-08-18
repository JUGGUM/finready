# ck_explained_requires_verification 의 NULL 구멍

| | |
|---|---|
| **발견** | 2026-08-18, F03 Testcontainers 통합 테스트 작성 중 |
| **상태** | **수정 완료** — `V3__fix_explained_constraint_null_hole.sql` |
| **영향 범위** | `coverage_result` INSERT 경로 전체 |
| **실제 사고** | 없음 (애플리케이션이 별도로 막고 있었다) |

---

## 무엇이 문제였나

CLAUDE.md 규칙 3은 이렇게 적혀 있다.

> **EXPLAINED는 provenance + semantic을 모두 통과해야 성립한다.**
> DB check 제약으로도 강제돼 있다. 애플리케이션에서 우회하지 말 것.

그런데 **DB check 제약이 실제로는 강제하지 못하는 경우가 있었다.**

V1 의 제약은 이렇다.

```sql
constraint ck_explained_requires_verification check (
    coverage_status <> 'EXPLAINED'
        or (provenance_valid = true and semantic_relation = 'SUPPORTS')
)
```

`semantic_relation IS NULL` 인 EXPLAINED 행을 넣으면:

| 부분식 | 결과 |
|---|---|
| `coverage_status <> 'EXPLAINED'` | `false` |
| `NULL = 'SUPPORTS'` | `NULL` |
| `provenance_valid = true and NULL` | `NULL` |
| `false or NULL` | **`NULL`** |

**Postgres 의 CHECK 제약은 결과가 `FALSE` 일 때만 거부한다. `NULL` 은 통과다.**
따라서 "Verifier 를 돌리지 않은 EXPLAINED" 가 조용히 저장된다.

## 왜 실제 사고는 없었나

`CoverageStatusResolver` 가 같은 조합을 애플리케이션 층에서 이미 접고 있다.
`semanticRelation == null` 이면 계약 결정표상 "원판정 유지"지만, EXPLAINED 는
INSUFFICIENT 로 내려보낸다. 그래서 이 조합이 DB 까지 도달한 적이 없다.

문제는 **이중 방어의 바깥층이 비어 있었다**는 것이다. 규칙 3을 믿고 DB 를 최후의
방어선으로 취급하는 코드가 나중에 생기면 그때 뚫린다.

## 왜 계약 결정표만으로는 이 조합이 생길 수 있었나

openapi `POST /sessions/{id}/coverage` 의 결정표 마지막 행은 이렇다.

| provenanceValid | semanticRelation | coverageStatus |
|---|---|---|
| — | Verifier 미실행 (WARN_ONLY 비-CONTRADICTED) | classifierStatus 유지 |

"원판정 유지"를 문자 그대로 구현하면 `classifierStatus = EXPLAINED` 이고
Verifier 를 안 돌린 WARN_ONLY Risk 에서 **EXPLAINED + semantic NULL** 이 나온다.
계약과 DB 제약이 이 지점에서 어긋난다.

**결정: DB(규칙 3)를 따른다.** `CoverageStatusResolver.resolve` 가 마지막에 한 번 더
접고, 그래도 빠져나온 값은 `CoverageResultFactory` 가 `IllegalStateException` 으로 막는다.

### 해소됨 — (1) Verifier 대상 확대 (2026-08-18)

**실측으로 결론이 났다.** `CONS_A_003` 실행에서 예상했던 문제가 그대로 나타났다:

```
R06  classifierStatus EXPLAINED    ← 분류기는 정확했다 (라벨과 일치)
     semanticRelation null         ← WARN_ONLY 라 Verifier 대상에서 빠짐
     coverageStatus   INSUFFICIENT ← CoverageStatusResolver 가 접었다
     downgraded       true
```

분류기가 맞게 판정한 것을 **우리 코드가 뒤집었다.** 잘 설명한 항목이 경고로 둔갑하며,
심사에서 "설명했는데 왜 경고냐"는 질문을 받을 수 있는 지점이다.

| 안 | 실측 근거 |
|---|---|
| **(1) Verifier 대상 확대 ← 채택** | 대상이 3개 → 4개. 세션당 비용 증가 미미(웜 캐시 $0.030 기준 한 자릿수 %) |
| (2) 현행 유지 | 잘 설명한 WARN_ONLY 가 계속 경고로 뜬다 |

착수 전에는 "토큰이 늘어난다"가 (1)의 비용이었는데, **실측 비용이 사전 추정의 1/5~1/8**로
나와($5로 약 100세션) 이 비용이 판단을 좌우할 만큼 크지 않다는 것이 확인됐다.

구현: `CoverageAnalysisService.selectVerifierTargets` 에 `EXPLAINED` 조건 추가.
`CoverageAnalysisServiceTest` 가 이 동작을 고정한다 — **계약 문구("GATE_REQUIRED +
CONTRADICTED")만 보고 되돌리면 테스트가 깨진다.**

## 어떻게 고쳤나

`is not distinct from` 은 NULL 을 값으로 취급해 항상 `true`/`false` 만 낸다.

```sql
alter table coverage_result
    add constraint ck_explained_requires_verification check (
        coverage_status <> 'EXPLAINED'
            or (provenance_valid = true and semantic_relation is not distinct from 'SUPPORTS')
        );
```

`coverage_result` 는 F03 미배포라 기존 행이 없어 재작성 비용이 0이었다.

## 어떻게 발견했나

`CoverageConstraintIntegrationTest` 의 "팩토리를 우회하면 DB 가 막는다" 케이스를
쓰다가, **막힐 줄 알았던 INSERT 가 성공해서** 드러났다.

이 결함은 **단위 테스트로는 절대 드러나지 않는다.** 실제 Postgres 의 3값 논리가
필요하다. Testcontainers 를 붙인 첫날에 바로 나왔다는 점이 도입 근거가 된다.

## 관련

- `V1__init.sql` — 원래 제약
- `V3__fix_explained_constraint_null_hole.sql` — 수정
- `CoverageStatusResolver` — 애플리케이션 층 방어
- `CoverageResultFactory` — 마지막 방어
- `CoverageConstraintIntegrationTest` — 회귀 고정
- CLAUDE.md 규칙 3
