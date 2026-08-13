package io.finready.product;

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
 * V1__init.sql / product_risk — Verified Risk Schema.
 * 사람이 검수한 값만 들어오며 런타임에 수정되지 않는다.
 *
 * <p>productId 는 스칼라 FK 로 둔다. open-in-view=false 이고 LLM 호출이 트랜잭션
 * 밖에서 일어나므로(CLAUDE.md 규칙 6) 지연 프록시가 트랜잭션 밖으로 새는 경로를
 * 애초에 만들지 않는다.
 */
@Entity
@Table(name = "product_risk")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRisk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "product_id", length = 32, nullable = false)
	private String productId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	/** product_risk 쪽에는 check 제약이 없다. gate_override.category 와 달리 자유 문자열이다 */
	@Column(name = "category", length = 48, nullable = false)
	private String category;

	@Column(name = "title", length = 200, nullable = false)
	private String title;

	@Column(name = "fact", nullable = false, columnDefinition = "text")
	private String fact;

	@Enumerated(EnumType.STRING)
	@Column(name = "coverage_policy", length = 24, nullable = false)
	private CoveragePolicy coveragePolicy;

	@Column(name = "understanding_check", nullable = false)
	private boolean understandingCheck;

	@Column(name = "source_page", nullable = false)
	private int sourcePage;

	@Column(name = "source_text", nullable = false, columnDefinition = "text")
	private String sourceText;

	@Column(name = "fallback_question", nullable = false, columnDefinition = "text")
	private String fallbackQuestion;

	/** 최초 질문과 같으면 "동일 질문 반복 금지"를 못 지킨다. DDL 이 ck_recheck_question_differs 로도 막는다 */
	@Column(name = "fallback_recheck_question", nullable = false, columnDefinition = "text")
	private String fallbackRecheckQuestion;

	@Column(name = "fallback_plain_explanation", nullable = false, columnDefinition = "text")
	private String fallbackPlainExplanation;

	@Column(name = "verified_at", nullable = false)
	private OffsetDateTime verifiedAt;

	@Column(name = "verified_by", length = 64, nullable = false)
	private String verifiedBy;

	public ProductRisk(String productId,
	                   String riskId,
	                   String category,
	                   String title,
	                   String fact,
	                   CoveragePolicy coveragePolicy,
	                   boolean understandingCheck,
	                   int sourcePage,
	                   String sourceText,
	                   String fallbackQuestion,
	                   String fallbackRecheckQuestion,
	                   String fallbackPlainExplanation,
	                   OffsetDateTime verifiedAt,
	                   String verifiedBy) {
		this.productId = productId;
		this.riskId = riskId;
		this.category = category;
		this.title = title;
		this.fact = fact;
		this.coveragePolicy = coveragePolicy;
		this.understandingCheck = understandingCheck;
		this.sourcePage = sourcePage;
		this.sourceText = sourceText;
		this.fallbackQuestion = fallbackQuestion;
		this.fallbackRecheckQuestion = fallbackRecheckQuestion;
		this.fallbackPlainExplanation = fallbackPlainExplanation;
		this.verifiedAt = verifiedAt;
		this.verifiedBy = verifiedBy;
	}
}
