package io.finready.coverage;

import java.util.List;

/**
 * openapi {@code POST /sessions/{sessionId}/gate-override} 의 requestBody.
 *
 * <p>AI 원판정과 evidence 는 이 요청으로 바뀌지 않는다 — {@code gate_override} 에 행이 하나
 * 추가될 뿐이다(규칙 1).
 *
 * @param staffExplanationConfirmed {@code understandingCheck=true} Risk 를 override 할 때 필수.
 *                                  true 면 그 Risk 는 이해확인 질문 대상에 남고, false 면
 *                                  질문·재설명에서 빠지며 SKIPPED_BY_OVERRIDE 로 기록된다
 */
public record GateOverrideRequest(
		List<String> riskIds,
		OverrideCategory category,
		String reason,
		Boolean staffExplanationConfirmed,
		String actor
) {
}
