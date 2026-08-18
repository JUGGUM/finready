package io.finready.understanding;

import io.finready.common.GenerationSource;

import java.util.List;

/**
 * openapi.yml v1.4.2 — {@code POST /sessions/{id}/questions} 의 200 본문.
 * required 는 [sessionId, totalRiskCount, questions] 다.
 */
public record QuestionsResponse(
		String sessionId,
		int totalRiskCount,
		List<QuestionView> questions
) {

	/**
	 * @param source     LLM 이 다듬었으면 LLM, 검수 원문 그대로면 FALLBACK
	 * @param attempt    이 엔드포인트가 반환하는 질문은 항상 1 이다
	 * @param orderIndex 1부터. UI 의 "Risk {orderIndex}/{totalRiskCount}"
	 */
	public record QuestionView(
			String riskId,
			String riskTitle,
			String question,
			GenerationSource source,
			int attempt,
			int orderIndex
	) {
	}
}
