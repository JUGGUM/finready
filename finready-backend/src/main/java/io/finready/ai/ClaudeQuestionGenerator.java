package io.finready.ai;

import com.anthropic.models.messages.OutputConfig;
import io.finready.understanding.QuestionGenerator;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * F04 이해확인 질문 생성 — Claude 구현.
 *
 * <p><b>모델은 문항을 창작하지 않는다.</b> 검수된 원문을 주고 <b>표현만</b> 고객 수준에 맞게
 * 다듬게 한다. 검수되지 않은 질문이 고객에게 나가면 그 자체가 상담 품질 문제다.
 *
 * <p>실패를 예외로 올리지 않고 <b>빈 목록</b>으로 돌려준다. 호출부가 검수 문항으로 메우고
 * {@code source: FALLBACK} 으로 표시하는 정상 경로를 이미 갖고 있다 — 여기서 던지면
 * 이해확인 단계 전체가 막힌다.
 */
class ClaudeQuestionGenerator implements QuestionGenerator {

	/** 프롬프트를 고치면 반드시 올린다 (TRD §7.2). v2: effort 명시 */
	private static final String PROMPT_VERSION = "question-v2";
	private static final String STAGE = "QUESTION_PHRASE";

	/**
	 * 검수 문항의 표현만 바꾸는 작업이라 추론이 거의 필요 없다. 실패해도 검수 원문으로
	 * 대체되는 정상 경로가 있어서 여기서 아끼는 위험이 가장 작다.
	 */
	private static final OutputConfig.Effort EFFORT = OutputConfig.Effort.LOW;

	private static final String SYSTEM_PROMPT = """
			당신은 ELS 상담에서 고객의 이해를 확인하는 질문을 다듬는 도구다.

			검수를 마친 질문 원문이 주어진다. 당신은 **그 질문의 의미를 유지한 채 표현만**
			고객의 이해 수준에 맞게 조정한다.

			## 반드시 지킬 것

			1. 질문을 새로 만들지 않는다. 원문이 묻는 것과 다른 것을 묻지 않는다.
			2. 확인하려는 대상(무엇을 이해했는지)을 바꾸지 않는다.
			3. 답을 유도하지 않는다. "~하다는 점 이해하셨죠?"처럼 예/아니오로 끝나는 형태를
			   만들지 않는다. 고객이 자기 말로 설명하게 하는 열린 질문을 유지한다.
			4. 원문이 이미 적절하면 그대로 두어도 된다.

			## 고객 수준 조정

			explanationLevel이 EASY면 전문 용어를 쉬운 말로 바꾸고 문장을 짧게 한다.
			NORMAL이면 원문의 표현을 크게 바꾸지 않는다.

			## 출력 형식

			다른 설명 없이 JSON만 출력한다.

			{"results":[{"riskId":"R01","question":"다듬은 질문"}]}

			- 다듬을 수 없는 항목은 결과에서 빼도 된다. 그 경우 검수 원문이 그대로 쓰인다.
			""";

	private final AiGateway gateway;

	ClaudeQuestionGenerator(AiGateway gateway) {
		this.gateway = gateway;
	}

	@Override
	public List<PhrasedQuestion> phrase(String sessionId, List<QuestionSeed> seeds) {
		AiGateway.AiCall call = new AiGateway.AiCall(
				sessionId,
				STAGE,
				PROMPT_VERSION,
				SYSTEM_PROMPT,
				buildUserMessage(seeds),
				"seeds=%d".formatted(seeds.size()),
				2048L,
				EFFORT);

		return gateway.call(call, this::parse);
	}

	private String buildUserMessage(List<QuestionSeed> seeds) {
		StringBuilder builder = new StringBuilder();
		QuestionSeed first = seeds.isEmpty() ? null : seeds.getFirst();
		if (first != null && first.customerLabel() != null) {
			builder.append("## 고객\n\n")
					.append(first.customerLabel())
					.append(" (explanationLevel=")
					.append(first.explanationLevel())
					.append(")\n\n");
		}
		builder.append("## 질문 목록\n\n");
		for (QuestionSeed seed : seeds) {
			builder.append("### ").append(seed.riskId()).append(" — ").append(seed.title()).append('\n');
			builder.append("확인하려는 사실: ").append(seed.fact()).append('\n');
			builder.append("검수된 질문 원문: ").append(seed.baseQuestion()).append("\n\n");
		}
		return builder.toString();
	}

	private List<PhrasedQuestion> parse(String responseText) {
		JsonNode results = JsonResponses.requireArray(JsonResponses.parse(responseText), "results");

		List<PhrasedQuestion> phrased = new ArrayList<>();
		for (JsonNode item : results) {
			phrased.add(new PhrasedQuestion(
					JsonResponses.requireText(item, "riskId"),
					JsonResponses.requireText(item, "question")));
		}
		return phrased;
	}
}
