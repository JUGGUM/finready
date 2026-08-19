package io.finready.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import io.finready.common.StateMachine;
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

	/**
	 * 상태를 바꾸는 유일한 경로. StateMachine 을 인자로 받는 이유는 규칙 7 때문이다 —
	 * 이 메서드를 부르려면 StateMachine 을 손에 쥐어야 하고, StateMachine 은 반드시 전이표를 본다.
	 * setter 를 열어두면 서비스가 전이표를 건너뛰고 상태를 바꾸는 경로가 생긴다.
	 *
	 * @throws io.finready.common.ApiException 허용되지 않은 전이면 INVALID_STATE_TRANSITION(409)
	 */
	public void transitionTo(SessionStatus to, StateMachine stateMachine) {
		stateMachine.assertCanTransition(this.status, to);
		this.status = to;
	}

	/**
	 * F08 종료. 상태 전이와 종료 흔적({@code closedBy}·{@code closedAt}·{@code unresolvedReason})을
	 * <b>한 메서드로 묶는다.</b>
	 *
	 * <p>{@code transitionTo} 를 부르고 서비스가 setter 로 나머지를 채우게 두면, 전이는 됐는데
	 * 종료자가 비어 있는 행이 만들어지는 경로가 열린다. 그런 행은 나중에 "누가 닫았는지 모르는
	 * 종료된 상담"으로 남고, 감사 관점에서 되짚을 방법이 없다.
	 *
	 * @param unresolvedReason 미해결 사유. 없으면 null — 필수 여부는
	 *                         {@link CloseEligibilityEvaluator} 가 이미 판정했다
	 */
	public void close(SessionStatus to,
	                  String actor,
	                  String unresolvedReason,
	                  StateMachine stateMachine) {
		transitionTo(to, stateMachine);
		this.closedBy = actor;
		this.closedAt = OffsetDateTime.now();
		this.unresolvedReason = unresolvedReason;
	}
}
