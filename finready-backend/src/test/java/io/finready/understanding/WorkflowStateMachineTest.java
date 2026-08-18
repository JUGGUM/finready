package io.finready.understanding;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Risk 단위 workflow 전이표 (TRD §4.2, §6.3). 세션 상태머신과 같은 이유로 전수로 덮는다 —
 * 어느 전이가 빠졌는지는 표를 눈으로 봐서는 안 보인다.
 */
@DisplayName("WorkflowStateMachine — Risk 전이표")
class WorkflowStateMachineTest {

	private static final String RISK_ID = "R01";

	private final WorkflowStateMachine stateMachine = new WorkflowStateMachine();

	@Test
	@DisplayName("NOT_STARTED 에서 갈 수 있는 곳은 IN_PROGRESS 와 COMPLETE 뿐")
	void fromNotStarted() {
		assertAllowed(WorkflowStatus.NOT_STARTED, WorkflowStatus.IN_PROGRESS);
		assertAllowed(WorkflowStatus.NOT_STARTED, WorkflowStatus.COMPLETE);
		assertRejected(WorkflowStatus.NOT_STARTED, WorkflowStatus.MANUAL_REVIEW_REQUIRED);
		assertRejected(WorkflowStatus.NOT_STARTED, WorkflowStatus.NOT_STARTED);
	}

	@Test
	@DisplayName("IN_PROGRESS 에서는 직원 처리로 넘기거나 끝낼 수 있다")
	void fromInProgress() {
		assertAllowed(WorkflowStatus.IN_PROGRESS, WorkflowStatus.MANUAL_REVIEW_REQUIRED);
		assertAllowed(WorkflowStatus.IN_PROGRESS, WorkflowStatus.COMPLETE);
		assertRejected(WorkflowStatus.IN_PROGRESS, WorkflowStatus.NOT_STARTED);
		assertRejected(WorkflowStatus.IN_PROGRESS, WorkflowStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("MANUAL_REVIEW_REQUIRED 에서는 COMPLETE 로만 간다")
	void fromManualReview() {
		assertAllowed(WorkflowStatus.MANUAL_REVIEW_REQUIRED, WorkflowStatus.COMPLETE);
		assertRejected(WorkflowStatus.MANUAL_REVIEW_REQUIRED, WorkflowStatus.IN_PROGRESS);
		assertRejected(WorkflowStatus.MANUAL_REVIEW_REQUIRED, WorkflowStatus.NOT_STARTED);
	}

	@ParameterizedTest
	@EnumSource(WorkflowStatus.class)
	@DisplayName("COMPLETE 에서 나가는 전이는 없고, 전용 오류 코드로 거절한다")
	void completeIsTerminal(WorkflowStatus target) {
		ApiException thrown = catchThrowableOfType(
				() -> stateMachine.assertCanTransition(WorkflowStatus.COMPLETE, target, RISK_ID),
				ApiException.class);

		// 일반 전이 오류로 뭉뚱그리면 프론트가 "이미 끝난 항목"을 안내하지 못한다
		assertThat(thrown.code()).isEqualTo(ErrorCode.RISK_ALREADY_FINALIZED);
		assertThat(thrown.riskId()).isEqualTo(RISK_ID);
	}

	@Test
	@DisplayName("finalDisposition 은 COMPLETE 일 때만 존재한다 (ck_disposition_only_when_complete)")
	void dispositionOnlyWhenComplete() {
		assertThatCode(() -> stateMachine.assertDispositionConsistent(
				WorkflowStatus.COMPLETE, FinalDisposition.AUTO_RESOLVED)).doesNotThrowAnyException();
		assertThatCode(() -> stateMachine.assertDispositionConsistent(
				WorkflowStatus.IN_PROGRESS, null)).doesNotThrowAnyException();

		assertThat(catchThrowableOfType(() -> stateMachine.assertDispositionConsistent(
				WorkflowStatus.COMPLETE, null), IllegalStateException.class)).isNotNull();
		assertThat(catchThrowableOfType(() -> stateMachine.assertDispositionConsistent(
				WorkflowStatus.MANUAL_REVIEW_REQUIRED, FinalDisposition.RESOLVED_BY_STAFF),
				IllegalStateException.class)).isNotNull();
	}

	@Test
	@DisplayName("엔티티는 상태머신을 거치지 않고는 상태를 바꿀 수 없다")
	void entityTransitionGoesThroughStateMachine() {
		RiskWorkflowState state = new RiskWorkflowState("S-1", RISK_ID);
		assertThat(state.getWorkflowStatus()).isEqualTo(WorkflowStatus.NOT_STARTED);

		state.transitionTo(WorkflowStatus.IN_PROGRESS, null, stateMachine);
		assertThat(state.getWorkflowStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);
		assertThat(state.getFinalDisposition()).isNull();

		state.transitionTo(WorkflowStatus.COMPLETE, FinalDisposition.AUTO_RESOLVED, stateMachine);
		assertThat(state.getFinalDisposition()).isEqualTo(FinalDisposition.AUTO_RESOLVED);

		// 종결 후에는 어떤 전이도 통과하지 못한다
		assertThat(catchThrowableOfType(() -> state.transitionTo(
				WorkflowStatus.IN_PROGRESS, null, stateMachine), ApiException.class).code())
				.isEqualTo(ErrorCode.RISK_ALREADY_FINALIZED);
	}

	private void assertAllowed(WorkflowStatus from, WorkflowStatus to) {
		assertThatCode(() -> stateMachine.assertCanTransition(from, to, RISK_ID))
				.as("%s → %s 는 허용돼야 한다", from, to)
				.doesNotThrowAnyException();
	}

	private void assertRejected(WorkflowStatus from, WorkflowStatus to) {
		assertThat(catchThrowableOfType(
				() -> stateMachine.assertCanTransition(from, to, RISK_ID), ApiException.class))
				.as("%s → %s 는 거절돼야 한다", from, to)
				.isNotNull();
	}
}
