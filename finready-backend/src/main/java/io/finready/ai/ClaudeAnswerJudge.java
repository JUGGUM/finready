package io.finready.ai;

import io.finready.understanding.AnswerJudge;
import io.finready.understanding.UnderstandingStatus;
import tools.jackson.databind.JsonNode;

/**
 * F05/F07 고객 답변 3상태 판정 — Claude 구현.
 *
 * <p>다른 포트와 달리 <b>배치가 아니다.</b> 고객이 답변을 하나 낼 때마다 한 번 부르며,
 * 그 사이 화면이 응답을 기다린다. 묶을 대상이 애초에 없다.
 *
 * <p>가장 조심할 판정은 UNCERTAIN 이다. "잘 모르겠다"와 "반대로 이해했다"를 섞으면
 * 경로가 갈린다 — MISUNDERSTOOD 는 재설명으로, UNCERTAIN 은 바로 후속 확인으로 간다(PRD §7.5).
 */
class ClaudeAnswerJudge implements AnswerJudge {

	/** 프롬프트를 고치면 반드시 올린다 (TRD §7.2) */
	private static final String PROMPT_VERSION = "judge-v1";
	private static final String STAGE = "ANSWER_JUDGE";

	private static final String SYSTEM_PROMPT = """
			당신은 ELS 상담에서 고객이 특정 위험을 이해했는지 판정하는 도구다.

			질문과 고객의 답변, 그리고 확인하려는 사실이 주어진다.
			고객의 답변이 그 사실을 이해한 것으로 볼 수 있는지 판정한다.

			## 판정 기준

			- UNDERSTOOD: 고객이 해당 사실을 자기 말로 정확히 설명했다.
			  표현이 서툴러도 핵심을 파악했다면 UNDERSTOOD다.
			- MISUNDERSTOOD: 고객이 해당 사실을 **반대로** 또는 **틀리게** 이해하고 있다.
			- UNCERTAIN: 답변만으로는 이해 여부를 판단할 수 없다.
			  "잘 모르겠다", "네", 무응답에 가까운 답, 질문과 무관한 답이 여기 해당한다.

			## 중요한 구분

			MISUNDERSTOOD와 UNCERTAIN을 섞지 않는다. 이후 흐름이 다르다.

			- 고객이 틀린 내용을 **적극적으로 말했다** → MISUNDERSTOOD
			- 고객이 **말하지 않았거나 모른다고 했다** → UNCERTAIN

			예:
			- "원금은 보장되는 거죠?" → MISUNDERSTOOD (반대로 이해)
			- "잘 모르겠어요" → UNCERTAIN
			- "네" (그것만) → UNCERTAIN (이해했는지 확인 불가)
			- "손실이 날 수도 있다는 거네요" → UNDERSTOOD

			## 관대함의 기준

			고객은 금융 전문가가 아니다. 용어를 정확히 쓰지 못해도 개념을 파악했으면
			UNDERSTOOD로 판정한다. 다만 핵심이 빠졌거나 반대로 말했다면 관대하게 넘기지 않는다.

			## 출력 형식

			다른 설명 없이 JSON만 출력한다.

			{"status":"UNDERSTOOD","reason":"판정 근거를 한국어 한두 문장으로"}

			- status는 위 3가지 값 중 하나만 쓴다. 다른 문자열을 만들지 않는다.
			- UNDERSTOOD가 아니면 reason을 반드시 채운다. 직원이 이 사유를 보고 다음 행동을 정한다.
			""";

	private final AiGateway gateway;

	ClaudeAnswerJudge(AiGateway gateway) {
		this.gateway = gateway;
	}

	@Override
	public Verdict judge(JudgeRequest request) {
		AiGateway.AiCall call = new AiGateway.AiCall(
				null,
				STAGE,
				PROMPT_VERSION,
				SYSTEM_PROMPT,
				buildUserMessage(request),
				"riskId=%s, attempt=%d, answerChars=%d"
						.formatted(request.riskId(), request.attempt(), request.answer().length()),
				1024L);

		return gateway.call(call, this::parse);
	}

	private String buildUserMessage(JudgeRequest request) {
		return """
				## 확인하려는 위험

				%s — %s

				%s

				## 고객에게 한 질문

				%s

				## 고객의 답변

				%s
				""".formatted(request.riskId(), request.title(), request.fact(),
				request.question(), request.answer());
	}

	private Verdict parse(String responseText) {
		JsonNode node = JsonResponses.parse(responseText);

		UnderstandingStatus status =
				JsonResponses.requireEnum(node, "status", UnderstandingStatus.class);
		String reason = JsonResponses.optionalText(node, "reason");

		// ck_reason_required 를 여기서 먼저 막는다. DB 까지 가면 원인이 LLM 응답이라는 게 안 보인다
		if (status != UnderstandingStatus.UNDERSTOOD && reason == null) {
			throw new AiGateway.ResponseParseException(
					"%s 판정에는 사유가 필요하다".formatted(status));
		}
		return new Verdict(status, reason);
	}
}
