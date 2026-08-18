package io.finready.understanding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 발급된 질문의 단일 진실 (TRD §4.6). <b>멱등 발급</b>이라 새로고침·뒤로가기로 문구가 바뀌지 않는다 —
 * 그래서 "이미 있으면 그대로 쓴다"를 판정할 조회가 필요하다.
 */
public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, Long> {

	List<SessionQuestion> findBySessionIdOrderByRiskIdAscAttemptAsc(String sessionId);

	Optional<SessionQuestion> findBySessionIdAndRiskIdAndAttempt(String sessionId,
	                                                            String riskId,
	                                                            short attempt);
}
