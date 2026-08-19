package io.finready.understanding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 직원 처리는 Risk 당 1행이다 — {@code uq_staff_resolution (session_id, risk_id)} 가 DB 에서 막는다.
 *
 * <p>이 테이블이 존재하는 이유가 규칙 1이다. {@code understanding_result.ai_status} 를
 * 덮어쓰는 대신 별도 행으로 남겨 리포트에 둘 다 표시한다.
 */
public interface StaffResolutionRepository extends JpaRepository<StaffResolution, Long> {

	List<StaffResolution> findBySessionIdOrderByRiskIdAsc(String sessionId);

	Optional<StaffResolution> findBySessionIdAndRiskId(String sessionId, String riskId);
}
