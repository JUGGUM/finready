package io.finready.understanding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskWorkflowStateRepository extends JpaRepository<RiskWorkflowState, Long> {

	List<RiskWorkflowState> findBySessionIdOrderByRiskIdAsc(String sessionId);

	Optional<RiskWorkflowState> findBySessionIdAndRiskId(String sessionId, String riskId);
}
