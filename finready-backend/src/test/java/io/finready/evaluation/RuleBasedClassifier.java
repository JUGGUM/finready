package io.finready.evaluation;

import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.CoverageStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule baseline — 키워드 개수만 세는 {@link CoverageClassifier} 구현. <b>대조군이다.</b>
 *
 * <p>존재 이유는 하나다. LLM 분류기가 세션당 약 $0.11 을 쓰는데, 대조군이 없으면
 * "정확도 92%"가 잘한 것인지 키워드 매칭도 내는 점수인지 알 수 없다. 이 클래스가
 * <b>돈을 쓰지 않는 쪽의 점수</b>를 만든다.
 *
 * <p>규칙은 {@code /eval/rule_baseline_keywords.json} 이 정의한다 — 서로 다른 키워드가
 * 상담 원문에 0개면 {@code NOT_FOUND}, 1개면 {@code INSUFFICIENT}, 2개 이상이면
 * {@code EXPLAINED}.
 *
 * <p><b>{@code CONTRADICTED} 를 낼 수 없다.</b> 단어가 있는지만 보는 규칙에는 "설명이
 * 틀렸다"를 판단할 수단이 자체가 없다. 이것이 baseline 의 상한이고, 상한이 존재한다는
 * 사실이 이 평가에서 가장 중요한 값이다 — {@code RuleBaselineTest} 가 테스트로 고정한다.
 *
 * <p>운영 코드가 아니다. {@code src/test} 에만 있고 애플리케이션은 이 클래스를 모른다.
 */
final class RuleBasedClassifier implements CoverageClassifier {

	private static final String KEYWORDS = "/eval/rule_baseline_keywords.json";

	/** 키워드 목록을 고치면 올린다. 프롬프트는 없지만 재현 조건은 이 파일이다 (TRD §7.2) */
	private static final String VERSION = "rule-baseline-v1";

	/** riskId → 서로 다른 키워드. 순서를 유지해 evidence 가 실행마다 흔들리지 않게 한다 */
	private final Map<String, Set<String>> keywords;

	RuleBasedClassifier() {
		this.keywords = readKeywords();
	}

	@Override
	public List<RiskVerdict> classify(String sessionId, String transcript, List<RiskPrompt> risks) {
		String haystack = squash(transcript);

		List<RiskVerdict> verdicts = new ArrayList<>();
		for (RiskPrompt risk : risks) {
			List<String> matched = matchedKeywords(risk.riskId(), haystack);
			CoverageStatus status = statusOf(matched.size());

			verdicts.add(new RiskVerdict(
					risk.riskId(),
					status,
					"키워드 %d개 일치%s".formatted(matched.size(),
							matched.isEmpty() ? "" : " — " + String.join(", ", matched)),
					// 근거가 없는데 문장을 지어내지 않는다. 0개면 인용할 것도 없다
					matched.isEmpty() ? null : sentenceContaining(transcript, matched.getFirst())));
		}
		return verdicts;
	}

	@Override
	public String promptVersion() {
		return VERSION;
	}

	// ------------------------------------------------------------------

	/**
	 * 0 / 1 / 2+ → 4상태. {@code CONTRADICTED} 는 어떤 입력으로도 나오지 않는다.
	 */
	private CoverageStatus statusOf(int hits) {
		if (hits == 0) {
			return CoverageStatus.NOT_FOUND;
		}
		return hits == 1 ? CoverageStatus.INSUFFICIENT : CoverageStatus.EXPLAINED;
	}

	/**
	 * <b>서로 다른</b> 키워드만 센다. 같은 단어를 열 번 말한 상담이 유리해지면 규칙이
	 * 설명의 유무가 아니라 길이를 재게 된다.
	 */
	private List<String> matchedKeywords(String riskId, String squashedTranscript) {
		List<String> matched = new ArrayList<>();
		for (String keyword : keywords.getOrDefault(riskId, Set.of())) {
			if (squashedTranscript.contains(squash(keyword))) {
				matched.add(keyword);
			}
		}
		return matched;
	}

	/**
	 * 공백을 지우고 비교한다. 키워드에 {@code "원금 보장"}·{@code "최대 손실"}·{@code "세 개"}
	 * 처럼 띄어쓰기가 든 항목이 있어, 그대로 두면 상담문이 {@code "원금보장"} 으로 적는 순간
	 * 놓친다.
	 *
	 * <p><b>띄어쓰기 때문에 약해진 baseline 을 이기는 것은 LLM 이 이긴 게 아니라 대조군을
	 * 잘못 만든 것이다.</b> 한국어는 어절 경계가 없어 {@code contains} 가 이미 부분일치이므로
	 * 공백을 지운다고 매칭의 성질이 달라지지도 않는다.
	 */
	private String squash(String text) {
		return text.replaceAll("\\s+", "");
	}

	/**
	 * 매칭된 키워드가 든 문장을 그대로 돌려준다. null 로 둬도 채점은 되지만, 그러면 포트의
	 * 출력 모양이 LLM 구현과 달라져 baseline 을 실제 파이프라인(provenance 검증)에
	 * 통과시켜 볼 수 없다.
	 *
	 * <p>offset 은 싣지 않는다 — 포트가 애초에 받지 않는다(규칙 4). 위치는 서버가 원문에서
	 * 다시 찾는다.
	 *
	 * <p><b>마침표를 무조건 문장 끝으로 보면 안 된다.</b> 키워드에 {@code "0.80%"}·{@code "3.0%"}·
	 * {@code "21.6%"} 처럼 소수점이 든 항목이 있어, 그렇게 쪼개면 키워드가 두 조각으로 갈려
	 * 방금 매칭된 근거를 못 찾고 조용히 null 을 돌려준다. 뒤에 공백이나 문자열 끝이 오는
	 * 마침표만 문장 경계로 본다.
	 */
	private String sentenceContaining(String transcript, String keyword) {
		String needle = squash(keyword);

		for (String line : transcript.split("\\n|\\.(?=\\s|$)")) {
			if (squash(line).contains(needle)) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					return trimmed;
				}
			}
		}
		// 키워드가 문장 경계에 걸쳐 있는 경우. 원문 전체를 돌려주느니 없다고 하는 편이 정직하다
		return null;
	}

	private Map<String, Set<String>> readKeywords() {
		try (InputStream in = RuleBasedClassifier.class.getResourceAsStream(KEYWORDS)) {
			if (in == null) {
				throw new IllegalStateException("리소스를 찾지 못했다: " + KEYWORDS);
			}
			JsonNode root = new ObjectMapper().readTree(in);

			Map<String, Set<String>> parsed = new LinkedHashMap<>();
			root.get("keywords").properties().forEach(entry -> {
				Set<String> words = new LinkedHashSet<>();
				entry.getValue().forEach(word -> words.add(word.asString()));
				parsed.put(entry.getKey(), words);
			});
			return parsed;
		}
		catch (Exception ex) {
			throw new IllegalStateException("키워드 목록을 읽지 못했다: " + KEYWORDS, ex);
		}
	}
}
