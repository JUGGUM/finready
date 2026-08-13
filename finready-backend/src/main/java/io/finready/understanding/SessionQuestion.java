package io.finready.understanding;

import io.finready.common.GenerationSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * V1__init.sql / session_question — 발급된 질문의 단일 진실 (TRD §4.6).
 * 멱등 발급이라 새로고침·뒤로가기로 문구가 바뀌지 않으며, attempt 2 는 attempt 1 과 반드시 다르다.
 *
 * <p>attempt 1 = POST /questions, attempt 2 = /understanding(UNCERTAIN) 또는 /reexplain(MISUNDERSTOOD).
 * 상한 2 는 애플리케이션과 DB 양쪽에서 막는다 (ck_question_attempt).
 */
@Entity
@Table(name = "session_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionQuestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	/** DDL 이 smallint 다. Integer 로 두면 int4 ↔ int2 로 어긋난다 */
	@Column(name = "attempt", nullable = false)
	private short attempt;

	@Column(name = "question", nullable = false, columnDefinition = "text")
	private String question;

	@Enumerated(EnumType.STRING)
	@Column(name = "generation_source", length = 16, nullable = false)
	private GenerationSource generationSource;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public SessionQuestion(String sessionId,
	                       String riskId,
	                       short attempt,
	                       String question,
	                       GenerationSource generationSource) {
		this.sessionId = sessionId;
		this.riskId = riskId;
		this.attempt = attempt;
		this.question = question;
		this.generationSource = generationSource;
		this.createdAt = OffsetDateTime.now();
	}
}
