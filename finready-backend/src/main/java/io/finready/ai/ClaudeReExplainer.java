package io.finready.ai;

import com.anthropic.models.messages.OutputConfig;
import io.finready.explanation.ReExplanationGenerator;
import tools.jackson.databind.JsonNode;

/**
 * F06 쉬운 말 재설명 — Claude 구현.
 *
 * <p>이 포트만 <b>Guardrail 이 뒤에 붙는다.</b> 다른 포트는 판정을 내리지만 여기는 고객에게
 * 그대로 읽히는 문장을 만든다 — 위험을 축소하거나 없는 숫자를 지어내면 그게 곧 사고다.
 * 그래서 프롬프트에서 한 번, {@code Guardrail} 에서 다시 한 번 막는다.
 *
 * <p>프롬프트로만 막지 않는 이유는 LLM 이 지시를 어길 수 있어서가 아니라, <b>어겼는지를
 * 확인할 방법이 프롬프트에는 없기 때문이다.</b> Guardrail 은 결정적이라 테스트로 고정된다.
 */
class ClaudeReExplainer implements ReExplanationGenerator {

	/** 프롬프트를 고치면 반드시 올린다 (TRD §7.2) */
	private static final String PROMPT_VERSION = "reexplain-v1";
	private static final String STAGE = "RE_EXPLANATION";

	/**
	 * 판정이 아니라 문장 다듬기다. 넣을 내용({@code fact}·{@code sourceText})이 이미 주어져
	 * 있어 추론할 것이 없다. 대신 이 단계는 고객이 화면 앞에서 기다린다.
	 */
	private static final OutputConfig.Effort EFFORT = OutputConfig.Effort.LOW;

	private static final String SYSTEM_PROMPT = """
			당신은 ELS 상담에서 고객이 반대로 이해한 위험을 다시 설명하는 도구다.

			고객이 이해한 내용과, 상품의 실제 사실, 그리고 상품설명서 원문이 주어진다.
			고객이 어디서 잘못 이해했는지 짚고, 실제 사실을 쉬운 말로 다시 설명한다.

			## 지켜야 할 것

			1. **주어진 사실과 원문에 있는 내용만 쓴다.** 추측하거나 일반적인 금융 상식을
			   덧붙이지 않는다.
			2. **숫자는 주어진 사실과 원문에 있는 것만 쓴다.** 없는 숫자를 만들지 않으며,
			   확실하지 않으면 숫자를 아예 쓰지 않는다.
			3. **위험을 축소하지 않는다.** "사실상", "거의 없다", "드물다", "걱정 안 하셔도",
			   "안전하다" 같은 표현을 쓰지 않는다. 확률이 낮아 보여도 그렇게 말하지 않는다.
			4. **가입을 권유하지 않는다.** 추천하거나 좋은 기회라고 말하지 않는다.
			   이 도구는 판매 도구가 아니라 이해를 돕는 도구다.
			5. 고객을 탓하지 않는다. "잘못 아셨습니다"가 아니라 "이 부분은 이렇습니다"로 쓴다.

			## 쓰는 방법

			- 고객이 어떻게 이해했는지 먼저 짚고, 실제로는 어떤지 이어서 설명한다.
			- 전문 용어를 쓰면 바로 뒤에 쉬운 말로 풀어 준다.
			- 3~5문장. 길면 읽히지 않는다.
			- 존댓말로 쓴다.

			## 출력 형식

			다른 설명 없이 JSON만 출력한다.

			{"explanation":"재설명 본문"}

			- explanation 은 한국어 평문이다. 마크다운이나 목록 기호를 쓰지 않는다.
			""";

	private final AiGateway gateway;

	ClaudeReExplainer(AiGateway gateway) {
		this.gateway = gateway;
	}

	@Override
	public String explain(String riskId,
	                      String riskTitle,
	                      String riskFact,
	                      String sourceText,
	                      String customerAnswer,
	                      String explanationLevel) {

		AiGateway.AiCall call = new AiGateway.AiCall(
				null,
				STAGE,
				PROMPT_VERSION,
				SYSTEM_PROMPT,
				buildUserMessage(riskId, riskTitle, riskFact, sourceText, customerAnswer, explanationLevel),
				"riskId=%s, answerChars=%d".formatted(riskId,
						customerAnswer == null ? 0 : customerAnswer.length()),
				1024L,
				EFFORT);

		return gateway.call(call, this::parse);
	}

	private String buildUserMessage(String riskId,
	                                String riskTitle,
	                                String riskFact,
	                                String sourceText,
	                                String customerAnswer,
	                                String explanationLevel) {
		return """
				## 위험 항목

				%s — %s

				## 상품의 실제 사실

				%s

				## 상품설명서 원문

				%s

				## 고객이 이해한 내용

				%s

				## 고객 설명 수준

				%s
				""".formatted(riskId, riskTitle, riskFact, sourceText, customerAnswer,
				explanationLevel == null ? "NORMAL" : explanationLevel);
	}

	private String parse(String responseText) {
		JsonNode node = JsonResponses.parse(responseText);
		String explanation = JsonResponses.optionalText(node, "explanation");
		if (explanation == null || explanation.isBlank()) {
			throw new AiGateway.ResponseParseException("explanation 이 비어 있다");
		}
		return explanation;
	}
}
