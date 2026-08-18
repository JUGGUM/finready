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
 * V1__init.sql / risk_workflow_state.
 * 갱신은 understanding 모듈에서 일원화한다 (TRD §4.2).
 *
 * <p>created_at 이 없고 updated_at 만 있다 — DDL 그대로다.
 * 전이 메서드는 두지 않았다. ConsultationSession 과 같은 이유로, 상태를 바꾸는 경로가
 * 서비스보다 먼저 생기면 일원화가 깨진다. F04~F07 작업에서 함께 설계한다.
 */
@Entity
@Table(name = "risk_workflow_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskWorkflowState {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "session_id", length = 40, nullable = false)
	private String sessionId;

	@Column(name = "risk_id", length = 8, nullable = false)
	private String riskId;

	@Enumerated(EnumType.STRING)
	@Column(name = "workflow_status", length = 32, nullable = false)
	private WorkflowStatus workflowStatus;

	/** COMPLETE 일 때만 non-null — ck_disposition_only_when_complete (TRD §6.3) */
	@Enumerated(EnumType.STRING)
	@Column(name = "final_disposition", length = 32)
	private FinalDisposition finalDisposition;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	public RiskWorkflowState(String sessionId, String riskId) {
		this.sessionId = sessionId;
		this.riskId = riskId;
		this.workflowStatus = WorkflowStatus.NOT_STARTED;
		this.finalDisposition = null;
		this.updatedAt = OffsetDateTime.now();
	}

	/**
	 * 상태를 바꾸는 유일한 경로. {@link WorkflowStateMachine} 을 인자로 받는 이유는
	 * {@code ConsultationSession.transitionTo} 와 같다 — 이 메서드를 부르려면 상태머신을
	 * 손에 쥐어야 하고, 상태머신은 반드시 전이표를 본다. setter 를 열면 서비스가 전이표를
	 * 건너뛰는 경로가 생기고, TRD §4.2 가 요구한 "갱신 일원화"가 깨진다.
	 *
	 * @param disposition COMPLETE 일 때만 non-null. 그 외에는 null 이어야 한다
	 */
	public void transitionTo(WorkflowStatus to,
	                         FinalDisposition disposition,
	                         WorkflowStateMachine stateMachine) {
		stateMachine.assertCanTransition(this.workflowStatus, to, this.riskId);
		stateMachine.assertDispositionConsistent(to, disposition);
		this.workflowStatus = to;
		this.finalDisposition = disposition;
		this.updatedAt = OffsetDateTime.now();
	}
}
