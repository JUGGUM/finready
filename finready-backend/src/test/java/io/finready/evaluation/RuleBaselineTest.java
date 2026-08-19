package io.finready.evaluation;

import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.CoverageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule baseline 단독 채점. <b>LLM·Docker·네트워크가 필요 없고 결정적이다.</b>
 * 그래서 {@code evaluate} 로 격리하지 않고 기본 {@code test} 태스크에 둔다 —
 * 키워드 목록을 건드리면 여기서 바로 표가 움직인다.
 *
 * <p><b>단언은 점수가 아니라 성질에 건다.</b> 현재 정확도(49/54)를 그대로 박으면 시나리오가
 * 60건까지 늘어날 때마다 깨지는데, 그 깨짐은 아무것도 알려주지 않는다. 대신 baseline 이
 * 절대 넘을 수 없는 선({@code CONTRADICTED} 를 못 낸다)과 최소선(바닥값)을 고정한다.
 *
 * <p>채점표는 콘솔로 찍는다. 이 평가의 결론은 합계가 아니라 <b>어긋난 자리의 성격</b>에
 * 있어서, 숫자 하나로 줄이면 정작 중요한 것이 사라진다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Rule baseline 채점")
class RuleBaselineTest {

	/**
	 * 키워드 파일이 망가진 것을 잡는 용도다. 현재 실측은 90.7% 이므로 한참 아래에 둔다 —
	 * 여기에 실측치를 박으면 데이터셋이 늘 때마다 무의미하게 깨진다.
	 */
	private static final double FLOOR = 0.70;

	private final EvalDataset dataset = new EvalDataset();
	private final RuleBasedClassifier baseline = new RuleBasedClassifier();

	@Test
	@DisplayName("모든 시나리오 × 모든 Risk 에 판정이 하나씩 나온다")
	void everyRiskGetsExactlyOneVerdict() {
		List<String> requested = dataset.prompts().stream()
				.map(CoverageClassifier.RiskPrompt::riskId)
				.toList();

		for (EvalDataset.Scenario scenario : dataset.scenarios()) {
			List<CoverageClassifier.RiskVerdict> verdicts = classify(scenario);

			assertThat(verdicts)
					.as("%s 판정 대상", scenario.id())
					.extracting(CoverageClassifier.RiskVerdict::riskId)
					.containsExactlyElementsOf(requested);
		}
	}

	/**
	 * <b>baseline 의 상한을 테스트로 고정한다.</b> 키워드가 있는지만 보는 규칙에는
	 * "설명이 틀렸다"를 판단할 수단이 없다. 누군가 이 클래스에 CONTRADICTED 를 내는 분기를
	 * 넣으면 그것은 더 이상 baseline 이 아니라 또 하나의 휴리스틱이고, LLM 과의 비교가
	 * 성립하지 않는다.
	 */
	@Test
	@DisplayName("CONTRADICTED 를 단 한 번도 내지 못한다 — baseline 의 상한")
	void neverProducesContradicted() {
		for (EvalDataset.Scenario scenario : dataset.scenarios()) {
			assertThat(classify(scenario))
					.as("%s", scenario.id())
					.extracting(CoverageClassifier.RiskVerdict::status)
					.doesNotContain(CoverageStatus.CONTRADICTED);
		}
	}

	/**
	 * 키워드가 하나라도 걸렸으면 그 문장이 원문에 있다는 뜻이므로 근거를 못 찾을 수 없다.
	 *
	 * <p>문장 분리를 마침표로 하면 {@code "0.80%"}·{@code "3.0%"}·{@code "21.6%"} 같은
	 * <b>소수점이 든 키워드가 두 조각으로 갈려</b> 방금 매칭한 근거를 다시 못 찾는다.
	 * 판정(개수 세기)은 멀쩡하고 evidence 만 조용히 비어서 눈에 띄지 않는다.
	 *
	 * <p>현재 데이터로는 <b>발동하지 않는다</b> — 매칭된 첫 키워드가 소수점 항목인 경우가
	 * 아직 없어서다. 즉 지금은 잠재된 함정이고, 키워드 목록이나 순서가 바뀌면 그때 터진다.
	 * 그래서 단언으로 걸어 둔다.
	 */
	@Test
	@DisplayName("키워드가 걸린 Risk 는 근거 문장을 함께 낸다")
	void matchedRiskAlwaysCarriesEvidence() {
		for (EvalDataset.Scenario scenario : dataset.scenarios()) {
			for (CoverageClassifier.RiskVerdict verdict : classify(scenario)) {
				if (verdict.status() == CoverageStatus.NOT_FOUND) {
					continue;   // 0개 매칭. 인용할 것이 없는 게 맞다
				}
				assertThat(verdict.evidenceText())
						.as("%s %s — %s", scenario.id(), verdict.riskId(), verdict.reason())
						.isNotBlank();
			}
		}
	}

	@Test
	@DisplayName("같은 입력에 같은 결과를 낸다")
	void isDeterministic() {
		EvalDataset.Scenario scenario = dataset.scenarios().getFirst();

		assertThat(classify(scenario)).isEqualTo(classify(scenario));
	}

	@Test
	@DisplayName("정확도가 바닥값 아래로 떨어지지 않는다")
	void staysAboveFloor() {
		int correct = 0;
		int total = 0;

		CoverageScorer scorer = new CoverageScorer(dataset.policies());
		for (EvalDataset.Scenario scenario : dataset.scenarios()) {
			CoverageScorer.Score score = scorer.score(scenario, classify(scenario));
			correct += score.correct();
			total += score.total();
		}

		assertThat((double) correct / total)
				.as("Risk 정확도 %d/%d — 키워드 목록이 망가졌는지 확인할 것", correct, total)
				.isGreaterThanOrEqualTo(FLOOR);
	}

	/**
	 * 채점표를 찍는다. 단언이 아니라 <b>관측</b>이다 — 이 평가가 답해야 하는 질문
	 * ("LLM 이 값을 하는가")은 합계로 답할 수 없고, 어디서 어떻게 틀리는지를 봐야 한다.
	 */
	@Test
	@DisplayName("채점표 출력")
	void printScoreTable() {
		CoverageScorer scorer = new CoverageScorer(dataset.policies());

		int correct = 0;
		int total = 0;
		int gateHit = 0;

		System.out.printf("%n=== Rule baseline (%s) ===%n", baseline.promptVersion());
		System.out.printf("%-12s %-8s %-24s %-24s %-6s %s%n",
				"시나리오", "Risk", "Gate(baseline)", "Gate(정답)", "일치", "막은 Risk");
		System.out.println("-".repeat(100));

		for (EvalDataset.Scenario scenario : dataset.scenarios()) {
			CoverageScorer.Score score = scorer.score(scenario, classify(scenario));

			boolean gateMatches = score.gateStatus().equals(scenario.expectedGate());
			correct += score.correct();
			total += score.total();
			if (gateMatches) {
				gateHit++;
			}

			System.out.printf("%-12s %d/%-6d %-24s %-24s %-6s %s%n",
					score.scenarioId(), score.correct(), score.total(),
					score.gateStatus(), scenario.expectedGate(),
					gateMatches ? "O" : "X", score.blockingRiskIds());
		}

		System.out.println("-".repeat(100));
		System.out.printf("Risk 정확도 %d/%d (%.1f%%)   |   Gate 정확도 %d/%d%n",
				correct, total, 100.0 * correct / total, gateHit, dataset.scenarios().size());

		printContradictedSection(scorer);
	}

	/**
	 * <b>이 평가의 결론이 나오는 자리다.</b> 집계에 섞으면 1/54 로 묻히는데, 실제로는
	 * 이 항목 하나가 baseline 과 LLM 을 가른다 — 없는 설명을 놓치는 것보다 <b>틀린 설명을
	 * 인증하는 것</b>이 이 상품에서 훨씬 나쁜 실패이기 때문이다.
	 */
	private void printContradictedSection(CoverageScorer scorer) {
		System.out.printf("%n--- gold=CONTRADICTED 항목에서 baseline 이 낸 판정 ---%n");

		boolean found = false;
		for (EvalDataset.Scenario scenario : dataset.scenarios()) {
			CoverageScorer.Score score = scorer.score(scenario, classify(scenario));

			for (Map.Entry<String, CoverageStatus> gold : scenario.goldLabels().entrySet()) {
				if (gold.getValue() != CoverageStatus.CONTRADICTED) {
					continue;
				}
				found = true;
				CoverageStatus predicted = score.predicted().get(gold.getKey());

				System.out.printf("  %s %s: 정답=CONTRADICTED  baseline=%s%s%n",
						scenario.id(), gold.getKey(), predicted,
						predicted == CoverageStatus.EXPLAINED
								? "   ← 오도 설명을 '설명 확인'으로 통과시켰다"
								: "");
			}
		}

		if (!found) {
			System.out.println("  (데이터셋에 CONTRADICTED 라벨이 없다)");
		}
		System.out.println();
	}

	private List<CoverageClassifier.RiskVerdict> classify(EvalDataset.Scenario scenario) {
		return baseline.classify(scenario.id(), scenario.text(), dataset.prompts());
	}
}
