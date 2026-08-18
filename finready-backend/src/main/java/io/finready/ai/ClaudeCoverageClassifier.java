package io.finready.ai;

import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.CoverageStatus;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * F03 Coverage 분류기 — Claude 구현.
 *
 * <p>Risk 전체를 <b>1회 batch call</b> 로 분류한다. Risk 마다 부르면 9배의 요금과 레이턴시가 든다.
 *
 * <p>evidence 의 offset 을 <b>받지 않는다</b>. 포트가 아예 필드를 두지 않았고(규칙 4),
 * 서버가 원문에서 다시 찾는다. 모델에게 위치를 묻지 않으면 틀린 위치를 받을 일도 없다.
 */
class ClaudeCoverageClassifier implements CoverageClassifier {

	/** 프롬프트를 고치면 반드시 올린다 — Hold-out 재현 조건이다 (TRD §7.2) */
	private static final String PROMPT_VERSION = "coverage-v1";
	private static final String STAGE = "COVERAGE_CLASSIFY";

	private static final String SYSTEM_PROMPT = """
			당신은 ELS(주가연계증권) 상담 내용을 검토하는 보조 도구다.
			상담원이 각 위험 항목을 고객에게 설명했는지를 항목 단위로 판정한다.

			판정은 법적 효력이 없으며 상담원을 돕기 위한 참고 자료다.

			## 판정 기준

			각 위험 항목에 대해 다음 4가지 중 하나로 판정한다.

			- EXPLAINED: 해당 위험의 핵심 사실이 상담 내용에 명확히 설명되어 있다.
			- INSUFFICIENT: 해당 위험을 언급했으나 핵심 사실이 빠졌거나 불충분하다.
			- NOT_FOUND: 해당 위험에 대한 언급이 상담 내용에 없다.
			- CONTRADICTED: 해당 위험을 사실과 반대로 설명했거나 오해를 유발했다.

			## 중요한 판정 원칙

			1. 단어가 등장한다고 설명된 것이 아니다. "조기상환"이라는 단어가 나와도 조건을
			   설명하지 않았다면 INSUFFICIENT다.
			2. 위험을 축소하거나 반대 의미로 전달한 경우 CONTRADICTED다. 예를 들어
			   "낙인이 없다"는 사실을 "원금이 지켜진다"는 의미로 전달했다면 CONTRADICTED다.
			3. 상담 내용에 없는 것을 추측해서 채우지 않는다.

			## 근거 인용 규칙

			판정의 근거가 되는 구간을 상담 내용에서 **그대로 복사**해 evidenceText에 넣는다.

			- 반드시 상담 내용에 있는 문자열을 글자 그대로 옮긴다. 요약하거나 다듬지 않는다.
			- 15자 이상 300자 이하로 인용한다.
			- 상담 내용 전체에서 그 구간이 한 번만 나타나도록 충분히 길게 인용한다.
			- 근거가 없으면(NOT_FOUND 등) evidenceText를 null로 둔다.

			## 출력 형식

			다른 설명 없이 JSON만 출력한다.

			{"results":[{"riskId":"R01","status":"EXPLAINED","reason":"판정 근거를 한국어 한두 문장으로","evidenceText":"상담 내용에서 그대로 복사한 구간"}]}

			- 요청받은 모든 riskId에 대해 정확히 하나씩 결과를 낸다. 빠뜨리거나 중복하지 않는다.
			- status는 위 4가지 값 중 하나만 쓴다. 다른 문자열을 만들지 않는다.
			""";

	private final AiGateway gateway;

	ClaudeCoverageClassifier(AiGateway gateway) {
		this.gateway = gateway;
	}

	@Override
	public List<RiskVerdict> classify(String transcript, List<RiskPrompt> risks) {
		String userMessage = buildUserMessage(transcript, risks);

		AiGateway.AiCall call = new AiGateway.AiCall(
				null,                       // sessionId 는 포트가 모른다. 세션 단위 집계는 stage 로 한다
				STAGE,
				PROMPT_VERSION,
				SYSTEM_PROMPT,
				userMessage,
				"risks=%d, transcriptChars=%d".formatted(risks.size(), transcript.length()),
				4096L);

		return gateway.call(call, this::parse);
	}

	private String buildUserMessage(String transcript, List<RiskPrompt> risks) {
		StringBuilder builder = new StringBuilder();
		builder.append("## 위험 항목\n\n");
		for (RiskPrompt risk : risks) {
			builder.append("### ").append(risk.riskId()).append(" — ").append(risk.title()).append('\n');
			builder.append(risk.fact()).append("\n\n");
		}
		builder.append("## 상담 내용\n\n").append(transcript).append('\n');
		return builder.toString();
	}

	private List<RiskVerdict> parse(String responseText) {
		JsonNode results = JsonResponses.requireArray(JsonResponses.parse(responseText), "results");

		List<RiskVerdict> verdicts = new ArrayList<>();
		for (JsonNode item : results) {
			verdicts.add(new RiskVerdict(
					JsonResponses.requireText(item, "riskId"),
					JsonResponses.requireEnum(item, "status", CoverageStatus.class),
					JsonResponses.optionalText(item, "reason"),
					JsonResponses.optionalText(item, "evidenceText")));
		}
		return verdicts;
	}
}
