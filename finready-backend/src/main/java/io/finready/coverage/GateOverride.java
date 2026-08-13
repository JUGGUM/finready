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
 * V1__init.sql / gate_override.
 * AI 원판정을 고치는 대신 별도 행으로 남긴다(CLAUDE.md 규칙 1). risk_id 별 1행이다.
 *
 * <p>reason 은 ck_override_reason_len 으로 5자 이상이 강제된다.
 */
@Entity
@Table(name = "gate_override")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GateOverride {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", length = 40, nullable = false)
	private OverrideCategory category;

	@Column(name = "reason", length = 500, nullable = false)
	private String reason;

	@Column(name = "staff_explanation_confirmed")
	private Boolean staffExplanationConfirmed;

	@Column(name = "actor", length = 64, nullable = false)
	private String actor;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public GateOverride(String sessionId,
	                    String riskId,
	                    OverrideCategory category,
	                    String reason,
	                    Boolean staffExplanationConfirmed,
	                    String actor) {
		this.sessionId = sessionId;
		this.riskId = riskId;
		this.category = category;
		this.reason = reason;
		this.staffExplanationConfirmed = staffExplanationConfirmed;
		this.actor = actor;
		this.createdAt = OffsetDateTime.now();
	}
}
