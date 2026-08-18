package io.finready.coverage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GateOverrideRepository extends JpaRepository<GateOverride, Long> {

	/** Gate 판정과 응답의 override 필드 양쪽에서 쓴다. {@code uq_gate_override} 로 risk 당 1행이다 */
	List<GateOverride> findBySessionIdOrderByRiskIdAsc(String sessionId);
}
