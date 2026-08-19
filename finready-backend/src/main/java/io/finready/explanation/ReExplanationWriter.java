package io.finready.explanation;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재설명 쓰기의 트랜잭션 경계. {@code CoverageWriter}·{@code UnderstandingWriter} 와 같은
 * 이유로 별도 빈이다 — LLM 호출이 트랜잭션 안에 있으면 안 되고(규칙 6), 같은 클래스의
 * {@code @Transactional} 메서드를 자기 호출하면 프록시를 타지 않아 트랜잭션이 아예 안 걸린다.
 */
@Component
class ReExplanationWriter {

	private final ReExplanationRepository repository;

	ReExplanationWriter(ReExplanationRepository repository) {
		this.repository = repository;
	}

	@Transactional
	ReExplanation save(ReExplanation reExplanation) {
		return repository.save(reExplanation);
	}
}
