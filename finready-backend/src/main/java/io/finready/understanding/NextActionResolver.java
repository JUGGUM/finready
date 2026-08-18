package io.finready.understanding;

import org.springframework.stereotype.Component;

/**
 * {@code nextAction} 산출 단일 지점 (TRD §6.6, 규칙 8).
 *
 * <p>계약의 표를 그대로 옮긴다:
 * <pre>
 * 조건                                   nextAction                 이동
 * UNDERSTOOD, 남은 Risk 있음              NEXT_RISK                  S04
 * UNDERSTOOD, 마지막 Risk                 GO_TO_REPORT               S08
 * MISUNDERSTOOD, attempt=1               REEXPLAIN                  S06
 * UNCERTAIN, attempt=1                   RECHECK                    S07
 * attempt=2 후 MISUNDERSTOOD/UNCERTAIN    STAFF_RESOLUTION_REQUIRED  S07
 * Staff Resolution 완료, 남은 Risk 있음    NEXT_RISK                  S04
 * Staff Resolution 완료, 마지막 Risk       GO_TO_REPORT               S08
 * </pre>
 *
 * <p><b>UNCERTAIN 은 REEXPLAIN 으로 가지 않는다.</b> PRD §7.5 가 경로를 갈라 놨다 —
 * {@code MISUNDERSTOOD → F06 재설명 → F07}, {@code UNCERTAIN → F07}. 헷갈리기 쉬운
 * 지점이라 여기 한 곳에만 둔다.
 */
@Component
public class NextActionResolver {

	/**
	 * 답변 판정 직후의 다음 행동.
	 *
	 * @param aiStatus      AI 판정. 이 값은 어떤 경로로도 덮어쓰이지 않는다(규칙 1) — 읽기만 한다
	 * @param attempt       방금 처리한 시도 번호 (1 또는 2)
	 * @param hasRemaining  이 Risk 를 끝냈을 때 아직 남은 Risk 가 있는지
	 */
	public NextAction afterAnswer(UnderstandingStatus aiStatus, int attempt, boolean hasRemaining) {
		if (aiStatus == UnderstandingStatus.UNDERSTOOD) {
			return afterRiskSettled(hasRemaining);
		}
		if (attempt >= UnderstandingPolicy.MAX_ATTEMPTS) {
			return NextAction.STAFF_RESOLUTION_REQUIRED;
		}
		// attempt 1 에서 갈린다 — 재설명이 도움이 되는 건 "반대로 이해한" 경우뿐이고,
		// "잘 모르겠다"는 설명을 다시 해도 같은 답이 돌아온다 (PRD §7.5)
		return aiStatus == UnderstandingStatus.MISUNDERSTOOD
				? NextAction.REEXPLAIN
				: NextAction.RECHECK;
	}

	/** 직원 처리가 끝난 뒤. 처분이 UNRESOLVED 여도 다음 Risk 로 진행한다 (PRD §7.5) */
	public NextAction afterStaffResolution(boolean hasRemaining) {
		return afterRiskSettled(hasRemaining);
	}

	private NextAction afterRiskSettled(boolean hasRemaining) {
		return hasRemaining ? NextAction.NEXT_RISK : NextAction.GO_TO_REPORT;
	}
}
