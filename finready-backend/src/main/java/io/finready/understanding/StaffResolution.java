package io.finready.understanding;

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
 * V1__init.sql / staff_resolution.
 * understanding_result.ai_status 를 고치는 대신 별도 행으로 남긴다(CLAUDE.md 규칙 1).
 * risk_id 별 1행이며 reason 은 ck_resolution_reason_len 으로 5자 이상이 강제된다.
 */
@Entity
@Table(name = "staff_resolution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffResolution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	@Enumerated(EnumType.STRING)
	@Column(name = "disposition", length = 32, nullable = false)
	private StaffDisposition disposition;

	@Column(name = "reason", length = 500, nullable = false)
	private String reason;

	@Column(name = "actor", length = 64, nullable = false)
	private String actor;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public StaffResolution(String sessionId,
	                       String riskId,
	                       StaffDisposition disposition,
	                       String reason,
	                       String actor) {
		this.sessionId = sessionId;
		this.riskId = riskId;
		this.disposition = disposition;
		this.reason = reason;
		this.actor = actor;
		this.createdAt = OffsetDateTime.now();
	}
}
