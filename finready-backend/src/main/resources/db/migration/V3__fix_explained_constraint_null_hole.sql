-- FinReady V3__fix_explained_constraint_null_hole.sql
-- 기준: Backend TRD v1.2.3 §8.5 / CLAUDE.md 규칙 3
--
-- V1 의 ck_explained_requires_verification 이 semantic_relation IS NULL 인 EXPLAINED 를
-- 막지 못한다. Postgres 의 3값 논리 때문이다.
--
--   coverage_status <> 'EXPLAINED'                    -> false
--   provenance_valid = true and NULL = 'SUPPORTS'     -> true and NULL -> NULL
--   false or NULL                                     -> NULL
--
-- CHECK 제약은 결과가 FALSE 일 때만 거부하고 NULL 은 통과시킨다. 그래서
-- "Verifier 를 돌리지 않은 EXPLAINED" 가 조용히 INSERT 된다.
--
-- 애플리케이션(CoverageStatusResolver)은 이 조합을 INSUFFICIENT 로 접고 있어 실제
-- 사고는 없었지만, 규칙 3이 말하는 "DB 로도 강제된다"가 성립하지 않는 상태였다.
-- 이중 방어의 바깥층을 복구한다.
--
-- 안전성: coverage_result 는 F03 미배포 상태라 기존 행이 없다. 재작성 비용이 0이다.

alter table coverage_result
    drop constraint ck_explained_requires_verification;

-- is not distinct from 은 NULL 을 값으로 취급해 true/false 만 낸다.
-- semantic_relation 이 NULL 이면 false 가 되어 제약이 실제로 거부한다.
alter table coverage_result
    add constraint ck_explained_requires_verification check (
        coverage_status <> 'EXPLAINED'
            or (provenance_valid = true and semantic_relation is not distinct from 'SUPPORTS')
        );

comment on constraint ck_explained_requires_verification on coverage_result is
    'EXPLAINED는 provenance + semantic(SUPPORTS)을 모두 통과해야 성립한다. semantic_relation이 NULL이면 거부된다 (TRD §8.5)';
