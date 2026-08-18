package io.finready.ai;

import io.finready.coverage.SemanticVerifier;
import io.finready.coverage.SemanticRelation;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * F03 Evidence Semantic Verifier — Claude 구현.
 *
 * <p>분류기와 나눠 둔 이유가 프롬프트에 그대로 드러난다. 분류기는 "설명했는가"를 넓게 보고,
 * 여기서는 <b>인용된 근거가 그 주장을 실제로 뒷받침하는가</b>만 좁게 본다. 키워드가 겹치지만
 * 의미가 반대인 경우("낙인이 없다" → "원금이 지켜진다")를 잡는 것이 이 호출의 존재 이유다.
 */
class ClaudeSemanticVerifier implements SemanticVerifier {

	/** 프롬프트를 고치면 반드시 올린다 (TRD §7.2) */
	private static final String PROMPT_VERSION = "verifier-v1";
	private static final String STAGE = "SEMANTIC_VERIFY";

	private static final String SYSTEM_PROMPT = """
			당신은 ELS 상담 검토 보조 도구의 근거 검증 단계다.

			앞 단계에서 각 위험 항목에 대해 상담 내용의 한 구간을 근거로 인용했다.
			당신의 일은 **그 인용 구간이 해당 위험의 사실을 실제로 뒷받침하는지** 판정하는 것이다.

			"이 위험이 설명되었는가"를 다시 판정하지 않는다. 오직 인용된 구간과 사실 사이의
			의미 관계만 본다.

			## 판정 기준

			- SUPPORTS: 인용 구간이 해당 사실을 정확히 전달한다.
			- CONTRADICTS: 인용 구간이 해당 사실과 반대되는 내용을 전달하거나,
			  고객이 위험을 반대로 이해하게 만든다.
			- INSUFFICIENT: 관련은 있으나 사실의 핵심이 빠져 있다.
			- UNRELATED: 인용 구간이 해당 사실과 관련이 없다.

			## 특히 주의할 것

			표현이 비슷해 보여도 의미가 반대인 경우를 놓치지 않는다. 예:

			- 사실: "낙인 배리어가 없다는 점이 원금 보장을 의미하지 않는다"
              인용: "낙인이 없는 구조라서 사실상 원금은 지켜집니다"
              → CONTRADICTS. 같은 소재를 다루지만 고객을 반대로 이해시킨다.

			- 사실: "최대 손실률은 -100%다"
			  인용: "손실이 날 수도 있습니다"
			  → INSUFFICIENT. 관련은 있으나 손실 범위라는 핵심이 없다.

			## 출력 형식

			다른 설명 없이 JSON만 출력한다.

			{"results":[{"riskId":"R01","relation":"SUPPORTS","reason":"판정 근거를 한국어 한두 문장으로"}]}

			- 요청받은 모든 riskId에 대해 정확히 하나씩 결과를 낸다. 빠뜨리거나 중복하지 않는다.
			- relation은 위 4가지 값 중 하나만 쓴다. 다른 문자열을 만들지 않는다.
			""";

	private final AiGateway gateway;

	ClaudeSemanticVerifier(AiGateway gateway) {
		this.gateway = gateway;
	}

	@Override
	public List<RelationVerdict> verify(String transcript, List<VerificationRequest> requests) {
		AiGateway.AiCall call = new AiGateway.AiCall(
				null,
				STAGE,
				PROMPT_VERSION,
				SYSTEM_PROMPT,
				buildUserMessage(transcript, requests),
				"targets=%d".formatted(requests.size()),
				2048L);

		return gateway.call(call, this::parse);
	}

	/**
	 * 상담 원문 전체를 함께 보낸다. 인용 구간만 보내면 앞뒤 맥락이 잘려 반대 의미를 놓친다 —
	 * "원금이 지켜진다"가 앞 문장의 조건절에 걸려 있는지 아닌지가 판정을 가른다.
	 */
	private String buildUserMessage(String transcript, List<VerificationRequest> requests) {
		StringBuilder builder = new StringBuilder();
		builder.append("## 검증 대상\n\n");
		for (VerificationRequest request : requests) {
			builder.append("### ").append(request.riskId()).append(" — ").append(request.title()).append('\n');
			builder.append("사실: ").append(request.fact()).append('\n');
			builder.append("인용된 근거: ").append(request.evidenceText()).append("\n\n");
		}
		builder.append("## 상담 내용 전체 (맥락 확인용)\n\n").append(transcript).append('\n');
		return builder.toString();
	}

	private List<RelationVerdict> parse(String responseText) {
		JsonNode results = JsonResponses.requireArray(JsonResponses.parse(responseText), "results");

		List<RelationVerdict> verdicts = new ArrayList<>();
		for (JsonNode item : results) {
			verdicts.add(new RelationVerdict(
					JsonResponses.requireText(item, "riskId"),
					JsonResponses.requireEnum(item, "relation", SemanticRelation.class),
					JsonResponses.optionalText(item, "reason")));
		}
		return verdicts;
	}
}
