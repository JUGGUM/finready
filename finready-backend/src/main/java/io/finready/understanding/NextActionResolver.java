package io.finready.understanding;

import org.springframework.stereotype.Component;

import java.util.List;

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

	/**
	 * 새로고침 복구용 — <b>저장된 상태만으로</b> 현재 분기값을 되살린다.
	 *
	 * <p>{@link #afterAnswer} 는 방금 일어난 판정을 알지만 여기는 모른다. 대신 위 표가 남긴
	 * 흔적을 읽는다 — 특히 <b>attempt 2 질문의 존재 여부가 REEXPLAIN 과 RECHECK 를 가른다.</b>
	 * UNCERTAIN 은 답변 즉시 후속 질문을 발급하고, MISUNDERSTOOD 는 재설명을 거쳐야 발급되기
	 * 때문이다(PRD §7.5). 그래서 "미답변 attempt 2 질문이 있다 = 재설명이 끝났다"가 성립한다.
	 *
	 * <p>이 산출도 여기 두는 이유는 {@code afterAnswer} 와 같다 — 프론트가 aiStatus 와 attempt 를
	 * 보고 자체 분기하면 두 경로가 어긋난다(규칙 8).
	 *
	 * @param states riskId 순서. 진행 중인 첫 Risk 가 화면을 정한다
	 */
	public NextAction resume(List<ResumeState> states) {
		if (states == null || states.isEmpty()) {
			return null;
		}

		ResumeState current = states.stream()
				.filter(state -> state.workflowStatus() != WorkflowStatus.COMPLETE)
				.findFirst()
				.orElse(null);

		if (current == null) {
			return NextAction.GO_TO_REPORT;
		}
		if (current.workflowStatus() == WorkflowStatus.MANUAL_REVIEW_REQUIRED) {
			return NextAction.STAFF_RESOLUTION_REQUIRED;
		}
		if (current.pendingAttempt() != null) {
			return current.pendingAttempt() >= UnderstandingPolicy.RECHECK_ATTEMPT
					? NextAction.RECHECK
					: NextAction.NEXT_RISK;
		}
		// 답변은 했는데 후속 질문이 없다 = 재설명을 아직 안 거쳤다
		return current.lastAiStatus() == UnderstandingStatus.MISUNDERSTOOD
				? NextAction.REEXPLAIN
				: NextAction.NEXT_RISK;
	}

	/**
	 * {@link #resume} 의 입력. 엔티티를 받지 않는 이유는 이 클래스가 계약 표만 알게 하기
	 * 위해서다 — 리포지토리나 JPA 를 알기 시작하면 표를 테스트하기 어려워진다.
	 *
	 * @param pendingAttempt 미답변 질문의 attempt. 없으면 null
	 * @param lastAiStatus   마지막 attempt 의 AI 판정. 답변 이력이 없으면 null
	 */
	public record ResumeState(WorkflowStatus workflowStatus,
	                          Integer pendingAttempt,
	                          UnderstandingStatus lastAiStatus) {
	}

	private NextAction afterRiskSettled(boolean hasRemaining) {
		return hasRemaining ? NextAction.NEXT_RISK : NextAction.GO_TO_REPORT;
	}
}
