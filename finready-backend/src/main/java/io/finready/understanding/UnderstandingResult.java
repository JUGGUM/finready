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
 * V1__init.sql / understanding_result.
 *
 * <p>ai_status 는 AI 최초 판정이며 Staff Resolution 이 덮어쓰지 않는다(CLAUDE.md 규칙 1).
 * classifier_status 와 같은 이유로 {@code updatable = false} 를 건다.
 * 직원 판단은 이 값을 고치는 게 아니라 staff_resolution 에 INSERT 한다.
 */
@Entity
@Table(name = "understanding_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnderstandingResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	@Column(name = "attempt", nullable = false)
	private short attempt;

	/** session_question 의 복사본. 감사 목적이며 원천은 session_question 이다 */
	@Column(name = "question", nullable = false, columnDefinition = "text")
	private String question;

	@Enumerated(EnumType.STRING)
	@Column(name = "generation_source", length = 16, nullable = false)
	private GenerationSource generationSource;

	@Column(name = "answer", nullable = false, columnDefinition = "text")
	private String answer;

	@Enumerated(EnumType.STRING)
	@Column(name = "answer_source", length = 32, nullable = false)
	private AnswerSource answerSource;

	/** AI 원판정. updatable = false 로 UPDATE 경로를 원천 차단한다 (TRD §4.2) */
	@Enumerated(EnumType.STRING)
	@Column(name = "ai_status", length = 20, nullable = false, updatable = false)
	private UnderstandingStatus aiStatus;

	/** MISUNDERSTOOD / UNCERTAIN 은 사유가 필수다 — ck_reason_required (PRD §9 F05) */
	@Column(name = "reason", columnDefinition = "text")
	private String reason;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public UnderstandingResult(String sessionId,
	                           String riskId,
	                           short attempt,
	                           String question,
	                           GenerationSource generationSource,
	                           String answer,
	                           AnswerSource answerSource,
	                           UnderstandingStatus aiStatus,
	                           String reason) {
		this.sessionId = sessionId;
		this.riskId = riskId;
		this.attempt = attempt;
		this.question = question;
		this.generationSource = generationSource;
		this.answer = answer;
		this.answerSource = answerSource;
		this.aiStatus = aiStatus;
		this.reason = reason;
		this.createdAt = OffsetDateTime.now();
	}
}
