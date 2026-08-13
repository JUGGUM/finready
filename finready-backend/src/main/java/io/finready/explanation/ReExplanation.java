package io.finready.explanation;

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
 * V1__init.sql / re_explanation.
 * MISUNDERSTOOD 판정 뒤 쉬운 말 재설명을 남긴다 (F06).
 *
 * <p>source_page · source_text 는 product_risk 의 근거를 그대로 들고 온다.
 * 재설명이 상품설명서 밖의 내용을 만들어내지 않았음을 사후에 대조하기 위해서다.
 */
@Entity
@Table(name = "re_explanation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReExplanation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	@Column(name = "source_page", nullable = false)
	private int sourcePage;

	@Column(name = "source_text", nullable = false, columnDefinition = "text")
	private String sourceText;

	@Column(name = "explanation", nullable = false, columnDefinition = "text")
	private String explanation;

	@Enumerated(EnumType.STRING)
	@Column(name = "generation_source", length = 16, nullable = false)
	private GenerationSource generationSource;

	@Column(name = "guardrail_retried", nullable = false)
	private boolean guardrailRetried;

	@Column(name = "guardrail_violations", columnDefinition = "text")
	private String guardrailViolations;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public ReExplanation(String sessionId,
	                     String riskId,
	                     int sourcePage,
	                     String sourceText,
	                     String explanation,
	                     GenerationSource generationSource,
	                     boolean guardrailRetried,
	                     String guardrailViolations) {
		this.sessionId = sessionId;
		this.riskId = riskId;
		this.sourcePage = sourcePage;
		this.sourceText = sourceText;
		this.explanation = explanation;
		this.generationSource = generationSource;
		this.guardrailRetried = guardrailRetried;
		this.guardrailViolations = guardrailViolations;
		this.createdAt = OffsetDateTime.now();
	}
}
