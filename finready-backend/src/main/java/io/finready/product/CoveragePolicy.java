package io.finready.product;

/**
 * product_risk.coverage_policy — TRD §6 Canonical Enum Contract.
 * V1__init.sql 의 ck_coverage_policy 와 값이 정확히 일치해야 한다.
 */
public enum CoveragePolicy {
	GATE_REQUIRED,
	WARN_ONLY,
	NOT_APPLICABLE
}
