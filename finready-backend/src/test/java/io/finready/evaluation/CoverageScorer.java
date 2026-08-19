package io.finready.evaluation;

import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.CoverageResult;
import io.finready.coverage.CoverageResultFactory;
import io.finready.coverage.CoverageStatus;
import io.finready.coverage.CoverageStatusResolver;
import io.finready.coverage.GateEvaluator;
import io.finready.coverage.ProvenanceCheck;
import io.finready.coverage.ProvenanceFailureReason;
import io.finready.coverage.SemanticRelation;
import io.finready.product.CoveragePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 분류기 출력을 정답 라벨과 대조해 채점한다. baseline 과 LLM 이 <b>같은 자로</b> 재도록
 * 한 곳에 둔다 — 채점이 두 벌이면 비교 결과가 분류기 차이인지 채점기 차이인지 모른다.
 *
 * <p>Gate 는 손으로 세지 않고 운영과 같은 {@link GateEvaluator} 로 재계산한다.
 * 여기서 Gate 규칙을 다시 적으면 서비스가 규칙을 바꿀 때 평가만 옛 규칙으로 통과한다.
 */
final class CoverageScorer {

	private final GateEvaluator gateEvaluator = new GateEvaluator();
	private final CoverageResultFactory factory = new CoverageResultFactory(new CoverageStatusResolver());
	private final Map<String, CoveragePolicy> policies;

	CoverageScorer(Map<String, CoveragePolicy> policies) {
		this.policies = policies;
	}

	Score score(EvalDataset.Scenario scenario, List<CoverageClassifier.RiskVerdict> verdicts) {
		Map<String, CoverageStatus> predicted = new LinkedHashMap<>();
		verdicts.forEach(v -> predicted.put(v.riskId(), v.status()));

		Map<String, CoverageStatus> gold = scenario.goldLabels();

		int correct = 0;
		for (Map.Entry<String, CoverageStatus> entry : predicted.entrySet()) {
			if (entry.getValue() == gold.get(entry.getKey())) {
				correct++;
			}
		}

		List<CoverageResult> results = predicted.entrySet().stream()
				.map(entry -> toResult(scenario.id(), entry.getKey(), entry.getValue()))
				.toList();

		GateEvaluator.GateVerdict verdict = gateEvaluator.evaluate(results, policies, Set.of());

		return new Score(scenario.id(), correct, predicted.size(),
				verdict.gateStatus().name(), verdict.blockingRiskIds(), predicted);
	}

	/**
	 * 라벨을 {@link CoverageResult} 로 옮긴다. {@code EXPLAINED} 는 provenance 통과 +
	 * {@code SUPPORTS} 라야 성립하므로(규칙 3) 그 조합을 만들어 준다. Gate 판정은
	 * {@code coverageStatus} 만 보므로 evidence 세부는 결과에 영향이 없다.
	 *
	 * <p>{@code DemoSeedGateConsistencyTest} 가 같은 변환을 한다. 그쪽은 라벨을,
	 * 여기서는 예측을 넣는 차이뿐이다.
	 */
	private CoverageResult toResult(String scenarioId, String riskId, CoverageStatus status) {
		if (status == CoverageStatus.EXPLAINED) {
			return factory.create(scenarioId, 1L, riskId, status, "평가", "평가",
					"평가 근거", ProvenanceCheck.found(0, 20), SemanticRelation.SUPPORTS);
		}
		return factory.create(scenarioId, 1L, riskId, status, "평가", null, null,
				ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY), null);
	}

	/**
	 * @param blockingRiskIds Gate 를 막은 Risk. <b>일치 여부만 보면 안 되는 이유가 여기 있다</b> —
	 *                        엉뚱한 Risk 가 막아 준 덕에 Gate 결과만 맞는 경우가 실제로 있다
	 */
	record Score(String scenarioId,
	             int correct,
	             int total,
	             String gateStatus,
	             List<String> blockingRiskIds,
	             Map<String, CoverageStatus> predicted) {
	}
}
