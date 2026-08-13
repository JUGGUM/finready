package io.finready.coverage;

/**
 * coverage_result 의 classifier_status / coverage_status 공통 4상태 — TRD §6.
 * ck_classifier_status · ck_coverage_status 와 값이 정확히 일치해야 한다.
 *
 * <p>두 컬럼은 같은 값 집합을 쓰지만 의미가 다르다 (TRD §4.3).
 * classifier_status 는 AI 원판정, coverage_status 는 provenance + semantic 검증 후 확정값이다.
 * 둘을 합친 effectiveStatus 같은 합성 상태는 만들지 않는다(CLAUDE.md 규칙 2).
 */
public enum CoverageStatus {
	EXPLAINED,
	INSUFFICIENT,
	NOT_FOUND,
	CONTRADICTED
}
