package io.finready.explanation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@code re_explanation} 에는 unique 제약이 <b>없다</b> — TRD §4.2 의 append 정책상 의도된 것이다.
 * 그래서 "이미 있는지"는 애플리케이션이 조회로 판단한다.
 */
public interface ReExplanationRepository extends JpaRepository<ReExplanation, Long> {

	List<ReExplanation> findBySessionIdOrderByRiskIdAscIdAsc(String sessionId);

	/** 멱등 응답용 — 새로고침이 LLM 요금을 다시 물지 않게 한다 */
	Optional<ReExplanation> findFirstBySessionIdAndRiskIdOrderByIdDesc(String sessionId, String riskId);
}
