package io.finready.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code llm_call_log} 기록. 별도 빈이자 별도 트랜잭션이다.
 *
 * <p>LLM 호출은 트랜잭션 밖에서 일어나므로(규칙 6) 기록도 자기 트랜잭션을 열어야 한다.
 * {@code REQUIRES_NEW} 인 이유는 <b>호출부가 나중에 실패해도 기록은 남아야 하기</b> 때문이다 —
 * 파싱 실패로 롤백되면 정작 원인인 응답 원문이 사라져 재현할 수 없다.
 */
@Component
public class LlmCallRecorder {

	private static final Logger log = LoggerFactory.getLogger(LlmCallRecorder.class);

	private final LlmCallLogRepository repository;

	public LlmCallRecorder(LlmCallLogRepository repository) {
		this.repository = repository;
	}

	/**
	 * 기록 실패가 본 요청을 죽이지 않게 한다. 관측 때문에 기능이 멈추면 본말이 전도된다 —
	 * 대신 WARN 으로 남겨 관측 자체가 조용히 비는 일은 없게 한다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(LlmCallLog entry) {
		try {
			repository.save(entry);
		} catch (RuntimeException ex) {
			log.warn("LLM 호출 기록 실패 (stage={})", entry.getStage(), ex);
		}
	}
}
