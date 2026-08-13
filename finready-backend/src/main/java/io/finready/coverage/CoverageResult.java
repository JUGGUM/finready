package io.finready.coverage;

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
 * V1__init.sql / coverage_result.
 *
 * <p>classifier_status 는 AI 원판정이며 어떤 경로로도 UPDATE 되지 않는다(CLAUDE.md 규칙 1).
 * setter 가 없는 것만으로는 부족해서 {@code updatable = false} 로 Hibernate 가 UPDATE 문에서
 * 아예 제외하도록 둔다. Override 는 이 값을 고치는 게 아니라 gate_override 에 INSERT 한다.
 */
@Entity
@Table(name = "coverage_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "revision_id", nullable = false)
	private Long revisionId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	/** AI 원판정. updatable = false 로 UPDATE 경로를 원천 차단한다 (TRD §4.2) */
	@Enumerated(EnumType.STRING)
	@Column(name = "classifier_status", length = 20, nullable = false, updatable = false)
	private CoverageStatus classifierStatus;

	/** provenance + semantic 검증 후 확정값 (TRD §8.5) */
	@Enumerated(EnumType.STRING)
	@Column(name = "coverage_status", length = 20, nullable = false)
	private CoverageStatus coverageStatus;

	@Column(name = "classifier_reason", columnDefinition = "text")
	private String classifierReason;

	@Column(name = "verification_reason", columnDefinition = "text")
	private String verificationReason;

	@Column(name = "evidence_text", columnDefinition = "text")
	private String evidenceText;

	/** 서버가 원문에서 재계산한 UTF-16 code unit offset. LLM 이 준 값을 쓰지 않는다 (규칙 4) */
	@Column(name = "evidence_start")
	private Integer evidenceStart;

	@Column(name = "evidence_end")
	private Integer evidenceEnd;

	@Column(name = "provenance_valid", nullable = false)
	private boolean provenanceValid;

	@Enumerated(EnumType.STRING)
	@Column(name = "provenance_failure_reason", length = 20)
	private ProvenanceFailureReason provenanceFailureReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "semantic_relation", length = 20)
	private SemanticRelation semanticRelation;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	/**
	 * DDL 이 ck_provenance_consistency 와 ck_explained_requires_verification 으로
	 * 조합을 강제한다. 어긋난 조합은 INSERT 시점에 터진다.
	 * 조합을 안전하게 만드는 팩토리는 Verifier(다음 순서 5번)에서 함께 설계한다.
	 */
	public CoverageResult(String sessionId,
	                      Long revisionId,
	                      String riskId,
	                      CoverageStatus classifierStatus,
	                      CoverageStatus coverageStatus,
	                      String classifierReason,
	                      String verificationReason,
	                      String evidenceText,
	                      Integer evidenceStart,
	                      Integer evidenceEnd,
	                      boolean provenanceValid,
	                      ProvenanceFailureReason provenanceFailureReason,
	                      SemanticRelation semanticRelation) {
		this.sessionId = sessionId;
		this.revisionId = revisionId;
		this.riskId = riskId;
		this.classifierStatus = classifierStatus;
		this.coverageStatus = coverageStatus;
		this.classifierReason = classifierReason;
		this.verificationReason = verificationReason;
		this.evidenceText = evidenceText;
		this.evidenceStart = evidenceStart;
		this.evidenceEnd = evidenceEnd;
		this.provenanceValid = provenanceValid;
		this.provenanceFailureReason = provenanceFailureReason;
		this.semanticRelation = semanticRelation;
		this.createdAt = OffsetDateTime.now();
	}
}
