package io.finready.understanding;

import io.finready.common.GenerationSource;

/**
 * openapi.yml v1.4.2 UnderstandingResponse — {@code /understanding} 과 {@code /recheck} 공용.
 * required 는 [riskId, attempt, remainingAttempts, aiStatus, workflowStatus, nextAction] 다.
 *
 * <p>{@code aiStatus} 와 {@code finalDisposition} 을 <b>둘 다</b> 싣는다. 직원이 해결해도
 * AI 판정은 원래 값을 유지하며 리포트에 함께 표시된다(규칙 1).
 *
 * @param remainingAttempts 서버가 강제한다. 0 이면 이후 요청은 409
 * @param reason            MISUNDERSTOOD/UNCERTAIN 일 때 필수
 * @param recheckQuestion   <b>{@code nextAction=RECHECK} 일 때만</b> 값이 있다.
 *                          UNCERTAIN 은 재설명을 거치지 않으므로 이 응답이 후속 질문을
 *                          제공하는 유일한 지점이다 (PRD §7.5)
 */
public record UnderstandingResponse(
		String riskId,
		String question,
		String answer,
		AnswerSource answerSource,
		int attempt,
		int remainingAttempts,
		UnderstandingStatus aiStatus,
		String reason,
		WorkflowStatus workflowStatus,
		FinalDisposition finalDisposition,
		NextAction nextAction,
		String recheckQuestion,
		GenerationSource recheckQuestionSource
) {
}
