package io.finready.session;

import io.finready.common.StateMachine;
import io.finready.understanding.FinalDisposition;
import io.finready.understanding.RiskUnderstandingState;
import io.finready.understanding.WorkflowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 종료 조건 판정. <b>리포트의 버튼 상태와 종료 요청 검증이 같은 값을 쓴다</b>는 것이
 * 이 클래스의 존재 이유라, 여기서 어긋나면 버튼은 눌리는데 400 이 나온다.
 */
class CloseEligibilityEvaluatorTest {

	private final CloseEligibilityEvaluator evaluator =
			new CloseEligibilityEvaluator(new StateMachine());

	private RiskUnderstandingState state(String riskId,
	                                     WorkflowStatus workflowStatus,
	                                     FinalDisposition disposition) {
		return new RiskUnderstandingState(
				riskId, riskId + " 제목", List.of(), null, workflowStatus, disposition, null);
	}

	@Nested
	@DisplayName("미해결 판정")
	class Unresolved {

		@Test
		@DisplayName("직원 처리 대기 중인 Risk 는 미해결이다")
		void manualReviewIsUnresolved() {
			List<String> unresolved = evaluator.unresolvedRiskIds(List.of(
					state("R01", WorkflowStatus.MANUAL_REVIEW_REQUIRED, null)));

			assertThat(unresolved).containsExactly("R01");
		}

		@Test
		@DisplayName("직원이 미해결로 마무리한 Risk 도 미해결이다")
		void staffMarkedUnresolvedIsUnresolved() {
			List<String> unresolved = evaluator.unresolvedRiskIds(List.of(
					state("R02", WorkflowStatus.COMPLETE, FinalDisposition.UNRESOLVED)));

			assertThat(unresolved).containsExactly("R02");
		}

		@Test
		@DisplayName("Override 로 제외된 Risk 는 미해결이 아니다 — 사유가 gate_override 에 남아 있다")
		void skippedByOverrideIsNotUnresolved() {
			List<String> unresolved = evaluator.unresolvedRiskIds(List.of(
					state("R03", WorkflowStatus.COMPLETE, FinalDisposition.SKIPPED_BY_OVERRIDE),
					state("R04", WorkflowStatus.COMPLETE, FinalDisposition.AUTO_RESOLVED),
					state("R05", WorkflowStatus.COMPLETE, FinalDisposition.RESOLVED_BY_STAFF)));

			assertThat(unresolved).isEmpty();
		}
	}

	@Nested
	@DisplayName("종료 가능 여부")
	class CanClose {

		@Test
		@DisplayName("이해 확인이 끝나기 전에는 종료할 수 없다")
		void beforeReviewCannotClose() {
			CloseEligibility eligibility = evaluator.evaluate(
					SessionStatus.UNDERSTANDING_IN_PROGRESS, List.of(), List.of());

			assertThat(eligibility.canClose()).isFalse();
		}

		@Test
		@DisplayName("직원 검토 단계면 종료할 수 있다")
		void awaitingStaffReviewCanClose() {
			CloseEligibility eligibility = evaluator.evaluate(
					SessionStatus.AWAITING_STAFF_REVIEW, List.of(), List.of());

			assertThat(eligibility.canClose()).isTrue();
			assertThat(eligibility.expectedCloseStatus())
					.isEqualTo(SessionStatus.SESSION_CLOSED_BY_STAFF);
			assertThat(eligibility.requiresUnresolvedReason()).isFalse();
		}

		@Test
		@DisplayName("미해결이 있으면 사유가 필요하고 WITH_UNRESOLVED 로 닫힌다")
		void unresolvedRequiresReason() {
			CloseEligibility eligibility = evaluator.evaluate(
					SessionStatus.AWAITING_STAFF_REVIEW, List.of(), List.of("R01"));

			assertThat(eligibility.canClose()).isTrue();
			assertThat(eligibility.requiresUnresolvedReason()).isTrue();
			assertThat(eligibility.expectedCloseStatus())
					.isEqualTo(SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED);
		}

		@Test
		@DisplayName("WARN_ONLY 미설명 항목이 확인 대상으로 그대로 실린다")
		void warningsAreCarriedThrough() {
			CloseEligibility eligibility = evaluator.evaluate(
					SessionStatus.AWAITING_STAFF_REVIEW, List.of("R05", "R07"), List.of());

			assertThat(eligibility.requiresWarningAcknowledgement()).containsExactly("R05", "R07");
		}

		/**
		 * 끝난 상담의 리포트에 "종료하면 이렇게 됩니다"라는 가정형 값이 찍히면 안 된다.
		 * 미해결이 없어도 WITH_UNRESOLVED 로 닫힌 세션은 그 상태 그대로여야 한다.
		 */
		@Test
		@DisplayName("이미 종료된 세션은 지금 상태가 곧 expectedCloseStatus 다")
		void closedSessionKeepsItsStatus() {
			CloseEligibility eligibility = evaluator.evaluate(
					SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED, List.of(), List.of());

			assertThat(eligibility.canClose()).isFalse();
			assertThat(eligibility.expectedCloseStatus())
					.isEqualTo(SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED);
		}
	}
}
