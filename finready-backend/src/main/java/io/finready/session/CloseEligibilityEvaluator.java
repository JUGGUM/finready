package io.finready.session;

import io.finready.common.StateMachine;
import io.finready.understanding.FinalDisposition;
import io.finready.understanding.RiskUnderstandingState;
import io.finready.understanding.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 종료 가능 여부 판정 (F08).
 *
 * <p><b>리포트와 종료가 같은 판정을 쓰게 하려고 뺐다.</b> {@code GET /report} 는 이 값을 실어
 * 버튼 상태를 정하고, {@code POST /close} 는 같은 값으로 요청을 검증한다. 둘이 갈라지면
 * 버튼은 활성화됐는데 눌러보면 400 이 나온다 — 심사 중에 나오면 설명할 방법이 없다.
 *
 * <p>상태를 읽기만 한다. 저장소도 트랜잭션도 없고, 인자로 받은 것만으로 결정한다.
 */
@Component
public class CloseEligibilityEvaluator {

	private final StateMachine stateMachine;

	public CloseEligibilityEvaluator(StateMachine stateMachine) {
		this.stateMachine = stateMachine;
	}

	/**
	 * 이해가 끝내 확인되지 않은 Risk.
	 *
	 * <p>둘을 센다 — <b>직원 처리 대기 중</b>({@code MANUAL_REVIEW_REQUIRED})과
	 * <b>직원이 미해결로 마무리한 것</b>({@code UNRESOLVED}). 앞의 것을 빼면 처리하지 않은
	 * 항목이 남은 채로 "정상 종료"가 되고, 뒤의 것을 빼면 직원이 스스로 미해결이라고 적은
	 * 판단이 리포트에서 사라진다.
	 *
	 * <p>{@code SKIPPED_BY_OVERRIDE} 는 미해결이 아니다 — 직원이 사유를 적고 제외한
	 * 항목이라 {@code gate_override} 에 근거가 남아 있다.
	 */
	public List<String> unresolvedRiskIds(List<RiskUnderstandingState> understanding) {
		return understanding.stream()
				.filter(state -> state.workflowStatus() == WorkflowStatus.MANUAL_REVIEW_REQUIRED
						|| state.finalDisposition() == FinalDisposition.UNRESOLVED)
				.map(RiskUnderstandingState::riskId)
				.toList();
	}

	/**
	 * @param warningRiskIds Coverage 가 낸 WARN_ONLY 미설명 목록
	 *                       ({@code GateEvaluator.GateVerdict.warningRiskIds}). Gate 를 막지
	 *                       않으므로 여기서 처음으로 직원 확인을 요구하는 지점이다
	 */
	public CloseEligibility evaluate(SessionStatus current,
	                                 List<String> warningRiskIds,
	                                 List<String> unresolvedRiskIds) {

		SessionStatus expected = expectedCloseStatus(current, unresolvedRiskIds);

		// 종료 가능 여부를 여기서 따로 정의하지 않는다. 전이표가 이미 답을 갖고 있고,
		// 조건을 복제하면 전이표를 고쳐도 이쪽이 안 따라온다 (규칙 7)
		boolean canClose = stateMachine.canTransition(current, expected);

		return new CloseEligibility(
				canClose,
				!unresolvedRiskIds.isEmpty(),
				warningRiskIds == null ? List.of() : warningRiskIds,
				expected);
	}

	/**
	 * 이미 종료된 세션은 <b>지금 상태가 곧 결과</b>다. 여기서 다시 계산하면 끝난 상담의
	 * 리포트에 "종료하면 이렇게 됩니다"라는 가정형 값이 찍힌다.
	 */
	private SessionStatus expectedCloseStatus(SessionStatus current, List<String> unresolvedRiskIds) {
		if (stateMachine.isClosed(current)) {
			return current;
		}
		return unresolvedRiskIds.isEmpty()
				? SessionStatus.SESSION_CLOSED_BY_STAFF
				: SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED;
	}
}
