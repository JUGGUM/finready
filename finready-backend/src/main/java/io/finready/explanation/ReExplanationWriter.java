package io.finready.explanation;

import io.finready.audit.AuditEntry;
import io.finready.audit.AuditRecorder;
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
	private final AuditRecorder auditRecorder;

	ReExplanationWriter(ReExplanationRepository repository, AuditRecorder auditRecorder) {
		this.repository = repository;
		this.auditRecorder = auditRecorder;
	}

	/**
	 * 재설명 저장 + 감사 기록.
	 *
	 * <p>본문은 남기지 않는다. {@code re_explanation} 에 이미 있고, 감사 로그가 볼 것은
	 * <b>LLM 이 만든 문장인지 검수 문장인지</b>와 <b>Guardrail 에 걸렸는지</b>다.
	 */
	@Transactional
	ReExplanation save(ReExplanation reExplanation, AuditEntry audit) {
		ReExplanation saved = repository.save(reExplanation);
		auditRecorder.record(reExplanation.getSessionId(), audit);
		return saved;
	}
}
