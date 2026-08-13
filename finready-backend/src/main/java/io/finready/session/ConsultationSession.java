package io.finready.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * V1__init.sql / consultation_session.
 *
 * <p>상태 전이 메서드를 여기에 두지 않는다. 전이는 common.StateMachine 단일 지점을
 * 통과해야 하므로(CLAUDE.md 규칙 7) StateMachine 을 만들 때 함께 설계한다.
 * 지금 setter 를 열어두면 서비스 코드가 상태를 직접 바꾸는 경로가 먼저 생긴다.
 */
@Entity
@Table(name = "consultation_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationSession {

	@Id
	@Column(name = "id", length = 40, nullable = false)
	private String id;

	@Column(name = "product_id", length = 32, nullable = false)
	private String productId;

	@Column(name = "customer_id", length = 32, nullable = false)
	private String customerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 40, nullable = false)
	private SessionStatus status;

	/** 생성 시점 snapshot. 시드가 바뀌어도 진행 중 세션의 판정 기준은 고정된다 */
	@Column(name = "product_risk_version", length = 64, nullable = false)
	private String productRiskVersion;

	/** 낙관적 락. 더블 클릭으로 attempt 가 2씩 오르는 사고를 막는다 (TRD §5.3) */
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "unresolved_reason", length = 500)
	private String unresolvedReason;

	@Column(name = "closed_by", length = 64)
	private String closedBy;

	@Column(name = "closed_at")
	private OffsetDateTime closedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public ConsultationSession(String id,
	                           String productId,
	                           String customerId,
	                           String productRiskVersion) {
		this.id = id;
		this.productId = productId;
		this.customerId = customerId;
		this.productRiskVersion = productRiskVersion;
		this.status = SessionStatus.DRAFT;
		this.createdAt = OffsetDateTime.now();
	}
}
