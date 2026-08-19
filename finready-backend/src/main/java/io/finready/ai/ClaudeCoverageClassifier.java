package io.finready.ai;

import com.anthropic.models.messages.OutputConfig;
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
 *
 * <p><b>Risk 카탈로그는 system 에 둔다.</b> 상품이 같으면 전 세션 동일하므로 캐시 prefix 로
 * 맞고, 캐시는 prefix 일치라 변하는 상담 원문과 같은 메시지에 두면 캐시가 아예 안 붙는다.
 * v1 은 카탈로그를 user 에 넣어 캐시가 사실상 놀았다.
 */
class ClaudeCoverageClassifier implements CoverageClassifier {

	/**
	 * 프롬프트를 고치면 반드시 올린다 — Hold-out 재현 조건이다 (TRD §7.2).
	 *
	 * <p>v1 → v2: Risk 카탈로그를 system 으로 이동(캐싱), 핵심/부연 구분 원칙 추가,
	 * NOT_FOUND 정당화 + Risk 경계 규칙 추가, reason 길이 제한.
	 */
	private static final String PROMPT_VERSION = "coverage-v2";
	private static final String STAGE = "COVERAGE_CLASSIFY";

	/** 분류는 구조화 추출에 가깝다. 다만 CONTRADICTED 판별에 의미 추론이 필요해 low 로는 내리지 않는다 */
	private static final OutputConfig.Effort EFFORT = OutputConfig.Effort.MEDIUM;

	private static final String INSTRUCTIONS = """
			당신은 ELS(주가연계증권) 상담 내용을 검토하는 보조 도구다.
			상담원이 각 위험 항목을 고객에게 설명했는지를 항목 단위로 판정한다.

			판정은 법적 효력이 없으며 상담원을 돕기 위한 참고 자료다.

			## 판정 기준

			각 위험 항목에 대해 다음 4가지 중 하나로 판정한다.

			- EXPLAINED: 해당 위험의 핵심이 상담 내용에 전달되었다.
			- INSUFFICIENT: 해당 위험을 다루기는 했으나 핵심이 빠졌다.
			- NOT_FOUND: 해당 위험을 다룬 내용이 상담에 없다.
			- CONTRADICTED: 해당 위험을 사실과 반대로 설명했거나 오해를 유발했다.

			## 무엇이 "핵심"인가

			각 위험 항목의 설명에는 핵심과 부연이 섞여 있다. 판정 기준은
			**고객이 이 위험 때문에 무엇을 잃을 수 있는지 알게 되었는가**다.

			- 핵심: 그 위험이 실제로 존재한다는 것, 그리고 고객이 입을 수 있는 불이익
			- 부연: 그 판정을 계산하는 방법 — 기준 수치, 산식, 차수, 배리어 값 등

			**부연이 빠졌다고 INSUFFICIENT로 내리지 않는다.** 핵심이 전달되었으면 EXPLAINED다.

			예시:
			- "원금이 보장되지 않고 손실이 날 수 있다"까지 말했다면 원금 손실 위험은 EXPLAINED다.
			  손실 판정 기준선(예: 65%)을 말하지 않았다는 이유로 내리지 않는다.
			- 반대로 손실 **범위**가 핵심인 항목이라면, 범위를 말하지 않은 것은 핵심 누락이다.
			  "손실이 날 수도 있다"만으로는 부족하다.

			판단이 서면 그 항목의 설명 첫 문장이 무엇을 말하는지 보라. 대체로 그것이 핵심이다.

			## 언급이 없으면 NOT_FOUND다

			NOT_FOUND는 흔하고 정상적인 판정이다. 상담에서 다루지 않은 위험이 있는 것이
			이 도구가 찾으려는 것이므로, 억지로 근거를 만들지 않는다.

			- 해당 위험을 다룬 문장이 없으면 NOT_FOUND로 판정하고 evidenceText를 null로 둔다.
			- 관련 있어 보이는 문장을 찾아내려 애쓰지 않는다.

			## 위험 항목 사이의 경계

			각 위험 항목은 서로 다른 것을 다룬다. 한 문장을 여러 항목의 근거로 재사용하지 않는다.

			- 인용하려는 문장이 **다른 항목의 주제에 더 가깝다면**, 이 항목에 대해서는
			  근거가 없는 것이다. NOT_FOUND로 판정한다.
			- 단어가 겹친다고 같은 주제가 아니다. 예를 들어 "조건이 까다롭다"는 표현은
			  기초자산 개수를 말하는 것일 수도, 상환 조건을 말하는 것일 수도 있다.
			  문장이 실제로 무엇을 설명하는지 보고 판단한다.

			## 그 밖의 판정 원칙

			1. 단어가 등장한다고 설명된 것이 아니다. 어떤 항목의 주제를 다루는 문장이 있지만
			   핵심을 설명하지 않았다면 INSUFFICIENT다. 주제 자체가 없으면 NOT_FOUND다.
			2. 위험을 축소하거나 반대 의미로 전달한 경우 CONTRADICTED다. 예를 들어
			   "낙인이 없다"는 사실을 "원금이 지켜진다"는 의미로 전달했다면 CONTRADICTED다.
			   다만 원금 손실 가능성을 이미 명확히 고지한 뒤 구조를 부연한 것은 오해 유발이 아니다.
			3. 상담 내용에 없는 것을 추측해서 채우지 않는다.

			## 근거 인용 규칙

			판정의 근거가 되는 구간을 상담 내용에서 **그대로 복사**해 evidenceText에 넣는다.

			- 반드시 상담 내용에 있는 문자열을 글자 그대로 옮긴다. 요약하거나 다듬지 않는다.
			- 15자 이상 300자 이하로 인용한다.
			- 상담 내용 전체에서 그 구간이 한 번만 나타나도록 충분히 길게 인용한다.
			- 근거가 없으면(NOT_FOUND 등) evidenceText를 null로 둔다.

			## 출력 형식

			다른 설명 없이 JSON만 출력한다.

			{"results":[{"riskId":"R01","status":"EXPLAINED","reason":"판정 근거","evidenceText":"상담 내용에서 그대로 복사한 구간"}]}

			- 요청받은 모든 riskId에 대해 정확히 하나씩 결과를 낸다. 빠뜨리거나 중복하지 않는다.
			- status는 위 4가지 값 중 하나만 쓴다. 다른 문자열을 만들지 않는다.
			- reason은 한 문장, 60자 이내로 쓴다.
			""";

	private final AiGateway gateway;

	ClaudeCoverageClassifier(AiGateway gateway) {
		this.gateway = gateway;
	}

	@Override
	public String promptVersion() {
		return PROMPT_VERSION;
	}

	@Override
	public List<RiskVerdict> classify(String sessionId, String transcript, List<RiskPrompt> risks) {
		AiGateway.AiCall call = new AiGateway.AiCall(
				sessionId,
				STAGE,
				PROMPT_VERSION,
				buildSystemPrompt(risks),
				buildUserMessage(transcript),
				"risks=%d, transcriptChars=%d".formatted(risks.size(), transcript.length()),
				4096L,
				EFFORT);

		return gateway.call(call, this::parse);
	}

	/**
	 * 지시문 + Risk 카탈로그. <b>상품이 같으면 매 호출 동일한 바이트여야</b> 캐시가 붙는다 —
	 * 호출부가 Risk 를 항상 같은 순서로 넘기므로(riskId 정렬) 성립한다.
	 */
	private String buildSystemPrompt(List<RiskPrompt> risks) {
		StringBuilder builder = new StringBuilder(INSTRUCTIONS);
		builder.append("\n## 위험 항목\n\n");
		for (RiskPrompt risk : risks) {
			builder.append("### ").append(risk.riskId()).append(" — ").append(risk.title()).append('\n');
			builder.append(risk.fact()).append("\n\n");
		}
		return builder.toString();
	}

	/** 세션마다 달라지는 것은 상담 원문뿐이다. 캐시 prefix 뒤에 온다 */
	private String buildUserMessage(String transcript) {
		return "## 상담 내용\n\n" + transcript + "\n";
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
