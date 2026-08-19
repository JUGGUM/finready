package io.finready.explanation;

/**
 * openapi {@code ReExplanationResponse.guardrail.violations} 의 값.
 *
 * <p>계약이 예시로 두 값만 든다. 늘리려면 계약을 먼저 고친다(규칙 9와 같은 이유다 —
 * 프론트가 이 문자열을 그대로 표시한다).
 */
public enum GuardrailViolation {

	/** 재설명에 등장한 숫자가 검수된 근거(fact·sourceText)에 없다 */
	UNSUPPORTED_NUMBER,

	/** 위험을 축소하는 표현이거나 가입을 권유하는 표현이다 */
	MITIGATING_EXPRESSION
}
