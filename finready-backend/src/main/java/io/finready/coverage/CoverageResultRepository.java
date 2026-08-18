package io.finready.coverage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * <b>UPDATE 메서드를 만들지 않는다</b> (CLAUDE.md 규칙 1). {@code classifier_status} 는
 * 어떤 경로로도 갱신되지 않으며, 정정은 {@code gate_override} INSERT 로 표현한다.
 */
public interface CoverageResultRepository extends JpaRepository<CoverageResult, Long> {

	/**
	 * 멱등 재사용 판정에 쓴다. {@code uq_coverage(revision_id, risk_id)} 라 revisionId 만으로
	 * 한 분석 회차가 특정된다 — 같은 revision 을 다시 분석하지 않고 이 결과를 그대로 돌려준다.
	 */
	List<CoverageResult> findByRevisionIdOrderByRiskIdAsc(Long revisionId);
}
