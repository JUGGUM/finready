package io.finready.understanding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * <b>UPDATE 메서드를 만들지 않는다</b> (규칙 1). {@code ai_status} 는 어떤 경로로도 갱신되지
 * 않으며, 직원 처리는 {@code staff_resolution} INSERT 로 표현한다.
 */
public interface UnderstandingResultRepository extends JpaRepository<UnderstandingResult, Long> {

	List<UnderstandingResult> findBySessionIdOrderByRiskIdAscAttemptAsc(String sessionId);

	/** attempt 상한 강제에 쓴다 — 이미 있으면 같은 attempt 를 다시 받지 않는다 */
	Optional<UnderstandingResult> findBySessionIdAndRiskIdAndAttempt(String sessionId,
	                                                                String riskId,
	                                                                short attempt);

	List<UnderstandingResult> findBySessionIdAndRiskIdOrderByAttemptAsc(String sessionId,
	                                                                   String riskId);
}
