package io.finready.explanation;

import io.finready.common.GenerationSource;
import io.finready.understanding.NextAction;

import java.util.List;

/**
 * openapi.yml v1.4.2 {@code ReExplanationResponse}.
 * required 는 [riskId, riskFact, sourcePage, sourceText, explanation, source, recheckQuestion] 다.
 *
 * <p>{@code customerAnswer} 와 {@code riskFact} 가 S06 의 좌·우다 — "고객이 이해한 것"과
 * "상품의 실제 조건"을 나란히 두는 화면이라 둘 다 응답에 있어야 한다.
 *
 * @param nextAction 재설명 후에는 <b>항상 RECHECK</b> 다. 프론트는 이 값으로 S07 로 간다(규칙 8)
 */
public record ReExplanationResponse(
		String riskId,
		String customerAnswer,
		String riskFact,
		int sourcePage,
		String sourceText,
		String documentUrl,
		String explanation,
		GenerationSource source,
		GuardrailView guardrail,
		String recheckQuestion,
		GenerationSource recheckQuestionSource,
		NextAction nextAction
) {

	/**
	 * @param retried    Guardrail 위반으로 재생성했는지
	 * @param violations 마지막 검사에서 걸린 항목. 통과했으면 빈 배열이다
	 */
	public record GuardrailView(boolean retried, List<GuardrailViolation> violations) {
	}
}
