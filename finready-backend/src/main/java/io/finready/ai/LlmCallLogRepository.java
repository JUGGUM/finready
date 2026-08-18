package io.finready.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 평가 재현과 성능 실측의 원천 (TRD §7.2). {@code prompt_version} 이 Hold-out 재현 조건이다.
 */
public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

	List<LlmCallLog> findBySessionIdOrderByIdAsc(String sessionId);
}
