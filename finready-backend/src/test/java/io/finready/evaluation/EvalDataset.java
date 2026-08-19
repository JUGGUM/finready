package io.finready.evaluation;

import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.CoverageStatus;
import io.finready.product.CoveragePolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 평가 데이터셋과 검수 Risk 스키마를 읽는 <b>단일 지점</b>.
 *
 * <p>읽는 곳이 여럿이면 시드 스키마가 바뀔 때 어떤 평가가 옛 구조를 들고 조용히 통과하는지
 * 알 수 없다. 라벨을 잘못 읽는 평가기는 <b>틀렸다는 사실조차 드러나지 않으므로</b>
 * 잘못된 결론을 그대로 문서에 남긴다.
 *
 * <p>운영 코드가 아니다. {@code src/test} 에만 있고 애플리케이션은 이 클래스를 모른다.
 */
final class EvalDataset {

	private static final String RISK_SCHEMA = "/seed/product_a_risk_schema.json";
	private static final String CONSULTATIONS = "/eval/demo_seed.json";

	private final List<Risk> risks;
	private final List<Scenario> scenarios;

	EvalDataset() {
		ObjectMapper mapper = new ObjectMapper();
		this.risks = readRisks(read(mapper, RISK_SCHEMA));
		this.scenarios = readScenarios(read(mapper, CONSULTATIONS));
	}

	List<Risk> risks() {
		return risks;
	}

	List<Scenario> scenarios() {
		return scenarios;
	}

	/** {@code NOT_APPLICABLE} 은 분석 대상이 아니다 — 운영 파이프라인과 같은 기준으로 거른다 */
	List<CoverageClassifier.RiskPrompt> prompts() {
		return risks.stream()
				.filter(risk -> risk.policy() != CoveragePolicy.NOT_APPLICABLE)
				.map(risk -> new CoverageClassifier.RiskPrompt(risk.riskId(), risk.title(), risk.fact()))
				.toList();
	}

	Map<String, CoveragePolicy> policies() {
		Map<String, CoveragePolicy> policies = new LinkedHashMap<>();
		risks.forEach(risk -> policies.put(risk.riskId(), risk.policy()));
		return policies;
	}

	// ------------------------------------------------------------------

	private List<Risk> readRisks(JsonNode root) {
		List<Risk> parsed = new ArrayList<>();
		for (JsonNode risk : root.get("risks")) {
			parsed.add(new Risk(
					risk.get("riskId").asString(),
					risk.get("title").asString(),
					risk.get("fact").asString(),
					CoveragePolicy.valueOf(risk.get("coveragePolicy").asString())));
		}
		return parsed;
	}

	private List<Scenario> readScenarios(JsonNode root) {
		List<Scenario> parsed = new ArrayList<>();
		for (JsonNode node : root.get("consultations")) {
			Map<String, CoverageStatus> goldLabels = new LinkedHashMap<>();
			node.get("coverageGroundTruth").properties().forEach(entry ->
					goldLabels.put(entry.getKey(), CoverageStatus.valueOf(entry.getValue().asString())));

			parsed.add(new Scenario(
					node.get("id").asString(),
					node.get("title").asString(),
					node.get("text").asString(),
					node.get("expectedGateResult").asString(),
					goldLabels));
		}
		return parsed;
	}

	private JsonNode read(ObjectMapper mapper, String classpathResource) {
		try (InputStream in = EvalDataset.class.getResourceAsStream(classpathResource)) {
			if (in == null) {
				throw new IllegalStateException("리소스를 찾지 못했다: " + classpathResource);
			}
			return mapper.readTree(in);
		}
		catch (Exception ex) {
			throw new IllegalStateException("데이터셋을 읽지 못했다: " + classpathResource, ex);
		}
	}

	record Risk(String riskId, String title, String fact, CoveragePolicy policy) {
	}

	/**
	 * @param goldLabels 손으로 정한 정답. 이 데이터셋은 <b>라벨을 먼저 정하고 상담문을 생성</b>하므로
	 *                   라벨이 곧 정답이다 (사후 라벨링 비용이 0인 대신, 라벨이 틀리면
	 *                   평가자가 틀린 상태가 된다 — {@code DemoSeedGateConsistencyTest} 가 그걸 막는다)
	 */
	record Scenario(String id, String title, String text, String expectedGate,
	                Map<String, CoverageStatus> goldLabels) {

		@Override
		public String toString() {
			return id;
		}
	}
}
