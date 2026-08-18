package io.finready.understanding;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Risk 단위 workflow 전이표 (TRD §4.2, §6.3).
 *
 * <p>세션의 {@code common.StateMachine} 과 같은 이유로 존재한다 — TRD §4.2 가 "갱신은
 * understanding 모듈에서 일원화하라"고 명시했고, 서비스마다 상태를 직접 바꾸면
 * "언제 COMPLETE 가 되는가"가 코드 여러 곳에 흩어진다.
 *
 * <p>세션 상태머신과 <b>합치지 않는다.</b> 축이 다르다 — 세션은 상담 전체의 단계,
 * 여기는 Risk 하나의 진행이다. 한 세션 안에서 Risk 마다 다른 값을 갖는다.
 *
 * <p>{@code MANUAL_REVIEW_REQUIRED} 는 이 enum 에만 있고 {@code FinalDisposition} 에는
 * 없다. "직원이 봐야 한다"는 진행 상태지 처분이 아니다.
 */
@Component
public class WorkflowStateMachine {

	private static final Map<WorkflowStatus, Set<WorkflowStatus>> ALLOWED =
			new EnumMap<>(WorkflowStatus.class);

	static {
		// 질문이 발급되면 시작된다. Override 로 건너뛰면 곧장 COMPLETE(SKIPPED_BY_OVERRIDE)
		ALLOWED.put(WorkflowStatus.NOT_STARTED,
				EnumSet.of(WorkflowStatus.IN_PROGRESS, WorkflowStatus.COMPLETE));
		// attempt 2 까지 갔는데도 안 풀리면 직원에게 넘어간다
		ALLOWED.put(WorkflowStatus.IN_PROGRESS,
				EnumSet.of(WorkflowStatus.MANUAL_REVIEW_REQUIRED, WorkflowStatus.COMPLETE));
		ALLOWED.put(WorkflowStatus.MANUAL_REVIEW_REQUIRED,
				EnumSet.of(WorkflowStatus.COMPLETE));
		// COMPLETE 는 종착이다. 되살리려면 새 세션을 만든다
		ALLOWED.put(WorkflowStatus.COMPLETE, EnumSet.noneOf(WorkflowStatus.class));
	}

	/**
	 * @throws ApiException 미허용 전이면 {@code INVALID_STATE_TRANSITION}(409),
	 *                      이미 종결된 Risk 면 {@code RISK_ALREADY_FINALIZED}(409)
	 */
	public void assertCanTransition(WorkflowStatus from, WorkflowStatus to, String riskId) {
		if (from == WorkflowStatus.COMPLETE) {
			// 계약에 전용 코드가 있다. 일반 전이 오류로 뭉뚱그리면 프론트가 "이미 끝난 항목"을
			// 안내하지 못하고 "새로고침하세요"만 띄우게 된다
			throw new ApiException(ErrorCode.RISK_ALREADY_FINALIZED,
					"이미 처리가 끝난 항목입니다.", riskId);
		}
		if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					"현재 단계에서는 진행할 수 없습니다. 화면을 새로고침해 주세요.", riskId);
		}
	}

	public boolean isFinal(WorkflowStatus status) {
		return status == WorkflowStatus.COMPLETE;
	}

	/**
	 * {@code ck_disposition_only_when_complete} 를 코드에서도 성립시킨다.
	 * DB 가 막아주지만 INSERT 시점까지 가면 어느 Risk 때문인지 추적이 번거롭다.
	 */
	public void assertDispositionConsistent(WorkflowStatus status, FinalDisposition disposition) {
		boolean complete = status == WorkflowStatus.COMPLETE;
		if (complete == (disposition == null)) {
			throw new IllegalStateException(
					"finalDisposition 은 COMPLETE 일 때만 존재해야 한다 (status=%s, disposition=%s)"
							.formatted(status, disposition));
		}
	}
}
