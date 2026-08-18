package io.finready.coverage;

import io.finready.product.CoveragePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평가 데이터셋의 <b>내부 정합성</b>을 검증한다. LLM 을 호출하지 않는다 —
 * 손으로 적은 라벨({@code coverageGroundTruth})과 손으로 적은 기대값
 * ({@code expectedGateResult})이 서로 맞는지만 본다.
 *
 * <p>이 데이터셋은 "라벨을 먼저 정하고 상담문을 생성"하는 방식으로 만든다. 그래서 라벨이
 * 곧 정답이고, 라벨과 기대 Gate 가 어긋나면 <b>모델을 평가하는 자가 틀린 상태</b>가 된다.
 * 시나리오가 60건까지 늘어날 예정이라 사람 눈으로 맞추는 것은 유지되지 않는다.
 *
 * <p>정책({@code coveragePolicy})은 시드에서 읽는다. 상수로 베껴 두면 시드 정책이 바뀔 때
 * 이 테스트만 옛 값을 들고 통과해 버린다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("demo_seed 데이터셋 정합성")
class DemoSeedGateConsistencyTest {

	private static final String SEED_PATH = "/seed/product_a_risk_schema.json";
	private static final String DATASET_PATH = "/eval/demo_seed.json";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GateEvaluator gateEvaluator = new GateEvaluator();
	private final CoverageResultFactory factory = new CoverageResultFactory(new CoverageStatusResolver());

	@Test
	@DisplayName("모든 시나리오에 본문이 채워져 있다")
	void everyScenarioHasText() {
		for (Scenario scenario : scenarios()) {
			assertThat(scenario.text())
					.as("%s 본문", scenario.id())
					.isNotBlank()
					.doesNotContain("생성 예정");
		}
	}

	@Test
	@DisplayName("라벨이 시드의 Risk 9건을 빠짐없이 덮는다")
	void groundTruthCoversEveryRisk() {
		Set<String> seedRiskIds = policies().keySet();

		for (Scenario scenario : scenarios()) {
			assertThat(scenario.groundTruth().keySet())
					.as("%s 라벨 대상", scenario.id())
					.containsExactlyInAnyOrderElementsOf(seedRiskIds);
		}
	}

	/**
	 * 손으로 적은 {@code expectedGateResult} 를 실제 Gate 판정 로직으로 재계산해 대조한다.
	 * 데이터셋이 서비스와 같은 규칙을 쓰는지 확인하는 것이라, 규칙이 바뀌면 여기가 먼저 깨진다.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("scenarios")
	@DisplayName("expectedGateResult 가 GateEvaluator 결과와 일치한다")
	void expectedGateMatchesEvaluator(Scenario scenario) {
		List<CoverageResult> results = scenario.groundTruth().entrySet().stream()
				.map(entry -> toResult(scenario.id(), entry.getKey(), entry.getValue()))
				.toList();

		GateEvaluator.GateVerdict verdict = gateEvaluator.evaluate(results, policies(), Set.of());

		assertThat(verdict.gateStatus().name())
				.as("%s — blocking=%s", scenario.id(), verdict.blockingRiskIds())
				.isEqualTo(scenario.expectedGate());
	}

	/**
	 * 라벨을 {@link CoverageResult} 로 옮긴다. EXPLAINED 는 provenance 통과 + SUPPORTS 라야
	 * 성립하므로(규칙 3) 그 조합을 만들어 준다. 나머지는 근거 없음으로 둔다 —
	 * Gate 판정은 coverageStatus 만 보므로 evidence 세부는 결과에 영향이 없다.
	 */
	private CoverageResult toResult(String scenarioId, String riskId, String status) {
		CoverageStatus label = CoverageStatus.valueOf(status);

		if (label == CoverageStatus.EXPLAINED) {
			return factory.create(scenarioId, 1L, riskId, label, "데이터셋 라벨", "데이터셋 라벨",
					"데이터셋 근거", ProvenanceCheck.found(0, 20), SemanticRelation.SUPPORTS);
		}
		return factory.create(scenarioId, 1L, riskId, label, "데이터셋 라벨", null, null,
				ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY), null);
	}

	private Map<String, CoveragePolicy> policies() {
		JsonNode risks = read(SEED_PATH).get("risks");
		Map<String, CoveragePolicy> policies = new LinkedHashMap<>();
		for (JsonNode risk : risks) {
			policies.put(risk.get("riskId").asString(),
					CoveragePolicy.valueOf(risk.get("coveragePolicy").asString()));
		}
		return policies;
	}

	private List<Scenario> scenarios() {
		List<Scenario> scenarios = new ArrayList<>();
		for (JsonNode node : read(DATASET_PATH).get("consultations")) {
			Map<String, String> groundTruth = new LinkedHashMap<>();
			node.get("coverageGroundTruth").properties()
					.forEach(entry -> groundTruth.put(entry.getKey(), entry.getValue().asString()));

			scenarios.add(new Scenario(
					node.get("id").asString(),
					node.get("text").asString(),
					node.get("expectedGateResult").asString(),
					groundTruth));
		}
		return scenarios;
	}

	private JsonNode read(String classpathResource) {
		try (InputStream in = getClass().getResourceAsStream(classpathResource)) {
			if (in == null) {
				throw new IllegalStateException("리소스를 찾지 못했다: " + classpathResource);
			}
			return objectMapper.readTree(in);
		} catch (Exception ex) {
			throw new IllegalStateException("데이터셋을 읽지 못했다: " + classpathResource, ex);
		}
	}

	private record Scenario(String id, String text, String expectedGate, Map<String, String> groundTruth) {
		@Override
		public String toString() {
			return id;
		}
	}
}
