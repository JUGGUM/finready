package io.finready.understanding;

import java.time.OffsetDateTime;

/**
 * openapi.yml v1.4.2 {@code StaffResolutionResponse}.
 *
 * <p>{@code aiStatus} 를 함께 싣는다. 직원이 해결했더라도 <b>AI 원판정은 그대로다</b>(규칙 1) —
 * 응답에서 빼면 프론트가 "덮어써졌다"고 오해하고 리포트와 값이 어긋난다.
 *
 * @param finalDisposition 직원 처리 후에는 항상 값이 있다. workflowStatus 는 COMPLETE 다
 */
public record StaffResolutionResponse(
		String riskId,
		StaffDisposition disposition,
		String reason,
		String actor,
		UnderstandingStatus aiStatus,
		WorkflowStatus workflowStatus,
		FinalDisposition finalDisposition,
		NextAction nextAction,
		OffsetDateTime createdAt
) {
}
