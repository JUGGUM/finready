package io.finready.evaluation;

import io.finready.ai.EvalClassifierFactory;
import io.finready.coverage.CoverageClassifier;
import io.finready.coverage.CoverageStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 분류기와 Rule baseline 을 같은 시나리오·같은 채점기로 나란히 놓는다.
 *
 * <p><b>실제 LLM 을 호출한다.</b> {@code ./gradlew evaluate} 로만 돌고 기본 {@code test}
 * 에서는 제외된다. 비용은 시나리오당 약 $0.03, 6건 전체 약 $0.18.
 *
 * <p><b>점수로 단언하지 않는다.</b> 실측상 두 방식은 집계에서 사실상 동점이고,
 * 동점이라는 사실 자체가 이 평가의 산출물이다. "LLM 이 baseline 보다 높다"를 단언으로
 * 걸면 그 사실이 드러날 때 결론을 고치는 게 아니라 테스트를 고치게 된다.
 *
 * <p>그래서 이 클래스는 <b>보고서를 출력하는 것이 목적</b>이고, 단언은 평가가 실제로
 * 수행됐는지(응답을 받았는지)까지만 확인한다.
 */
@Tag("evaluation")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Coverage — LLM vs Rule baseline")
class CoverageBaselineComparisonTest {

	private final EvalDataset dataset = new EvalDataset();
	private final RuleBasedClassifier baseline = new RuleBasedClassifier();

	private CoverageClassifier llm;

	/**
	 * 키가 없으면 <b>실패한다.</b> skip 이 아니다.
	 *
	 * <p>처음에는 {@code Assumptions.assumeTrue} 로 건너뛰게 했다가 되돌렸다. 실제로
	 * 그렇게 돌려 보니 <b>4초 만에 BUILD SUCCESSFUL 이 뜨고 출력이 한 줄도 없었다.</b>
	 * 결과 XML 에도 {@code <skipped/>} 만 남고 assumption 메시지조차 기록되지 않아,
	 * 평가를 돌렸는데 아무 일도 일어나지 않았다는 사실을 알 방법이 없었다.
	 *
	 * <p>{@code evaluate} 는 CI 가 부르지 않는다 — {@code test} 만 돈다. 이 태스크를
	 * 실행하는 것은 "평가를 하겠다"고 명시적으로 타이핑한 사람뿐이고, 그 사람에게
	 * 조용한 초록을 돌려주는 것은 안전장치가 아니라 거짓말이다.
	 */
	@BeforeAll
	void setUp() {
		String apiKey = System.getenv("LLM_API_KEY");

		if (apiKey == null || apiKey.isBlank()) {
			// 예외 메시지만으로는 콘솔에 "IllegalStateException at ...:56" 한 줄만 뜨고
			// 조치 방법은 HTML 리포트를 열어야 보인다. 고쳐야 할 사람이 그 자리에서
			// 읽을 수 있어야 하므로 배너를 직접 찍는다
			System.err.println("""

					┌─ 평가를 실행할 수 없다 ────────────────────────────────────────
					│ LLM_API_KEY 가 비어 있다.
					│
					│ 키를 아래 중 한 곳에 넣는다.
					│   1) ~/.gradle/gradle.properties 에  llmApiKey=sk-ant-...   ← 권장
					│      저장소 밖이라 커밋될 수 없고, IntelliJ 초록 버튼으로도 먹는다
					│   2) 셸 환경변수 LLM_API_KEY
					│
					│ ⚠ IntelliJ 의 "애플리케이션" Run Configuration 에 넣은 환경변수는
					│   여기에 적용되지 않는다. 그 설정은 Spring Boot 실행용이고
					│   Gradle 은 별도 프로세스다.
					│
					│ -PllmApiKey=... 도 동작하지만 셸 히스토리와 ps 에 키가 남는다.
					└───────────────────────────────────────────────────────────────
					""");

			throw new IllegalStateException(
					"LLM_API_KEY 가 없어 평가를 실행할 수 없다 (조치 방법은 위 출력 참조)");
		}

		// 키 값은 찍지 않는다(규칙 10). 길이만으로도 "설정은 됐다"는 확인은 충분하다
		System.out.printf("LLM 분류기 준비 — 키 %d자 확인%n", apiKey.length());

		llm = EvalClassifierFactory.claude(apiKey);
	}

	@Test
	@DisplayName("두 분류기를 대조한다")
	void compare() {
		CoverageScorer scorer = new CoverageScorer(dataset.policies());
		List<Row> rows = new ArrayList<>();

		for (EvalDataset.Scenario scenario : dataset.scenarios()) {
			List<CoverageClassifier.RiskVerdict> baseVerdicts =
					baseline.classify(scenario.id(), scenario.text(), dataset.prompts());
			List<CoverageClassifier.RiskVerdict> llmVerdicts =
					llm.classify(scenario.id(), scenario.text(), dataset.prompts());

			rows.add(new Row(scenario,
					scorer.score(scenario, baseVerdicts),
					scorer.score(scenario, llmVerdicts)));
		}

		printAggregate(rows);
		printDisagreements(rows);
		printContradicted(rows);
	}

	// ------------------------------------------------------------------

	/** ① 집계 — 두 방식을 같은 자로 잰 숫자 */
	private void printAggregate(List<Row> rows) {
		System.out.printf("%n=== ① 집계 (%s vs %s) ===%n", baseline.promptVersion(), llm.promptVersion());
		System.out.printf("%-12s %-16s %-16s %s%n", "시나리오", "Risk(baseline)", "Risk(LLM)", "Gate 일치");
		System.out.println("-".repeat(70));

		int baseCorrect = 0;
		int llmCorrect = 0;
		int total = 0;
		int baseGate = 0;
		int llmGate = 0;

		for (Row row : rows) {
			boolean bGate = row.base().gateStatus().equals(row.scenario().expectedGate());
			boolean lGate = row.llm().gateStatus().equals(row.scenario().expectedGate());

			baseCorrect += row.base().correct();
			llmCorrect += row.llm().correct();
			total += row.base().total();
			if (bGate) {
				baseGate++;
			}
			if (lGate) {
				llmGate++;
			}

			System.out.printf("%-12s %d/%-14d %d/%-14d baseline=%s LLM=%s%n",
					row.scenario().id(),
					row.base().correct(), row.base().total(),
					row.llm().correct(), row.llm().total(),
					bGate ? "O" : "X", lGate ? "O" : "X");
		}

		System.out.println("-".repeat(70));
		System.out.printf("baseline  Risk %d/%d (%.1f%%)   Gate %d/%d%n",
				baseCorrect, total, 100.0 * baseCorrect / total, baseGate, rows.size());
		System.out.printf("LLM       Risk %d/%d (%.1f%%)   Gate %d/%d%n",
				llmCorrect, total, 100.0 * llmCorrect / total, llmGate, rows.size());
	}

	/**
	 * ② 어긋난 자리 — 두 분류기의 판정이 다른 항목만. 같은 답을 낸 항목은 비교에
	 * 기여하지 않으므로 지운다. 여기 남는 줄이 <b>돈이 만든 차이의 전부</b>다.
	 */
	private void printDisagreements(List<Row> rows) {
		System.out.printf("%n=== ② 두 분류기가 갈린 항목 ===%n");
		System.out.printf("%-12s %-6s %-15s %-15s %-15s %s%n",
				"시나리오", "Risk", "정답", "baseline", "LLM", "누가 맞았나");
		System.out.println("-".repeat(90));

		int only = 0;
		for (Row row : rows) {
			for (Map.Entry<String, CoverageStatus> gold : row.scenario().goldLabels().entrySet()) {
				String riskId = gold.getKey();
				CoverageStatus b = row.base().predicted().get(riskId);
				CoverageStatus l = row.llm().predicted().get(riskId);

				if (b == null || l == null || b == l) {
					continue;
				}
				only++;

				String winner = l == gold.getValue() ? "LLM"
						: b == gold.getValue() ? "baseline" : "둘 다 틀림";

				System.out.printf("%-12s %-6s %-15s %-15s %-15s %s%n",
						row.scenario().id(), riskId, gold.getValue(), b, l, winner);
			}
		}

		if (only == 0) {
			System.out.println("  (갈린 항목이 없다 — 두 방식이 완전히 같은 답을 냈다)");
		}
	}

	/**
	 * ③ CONTRADICTED — <b>이 평가의 결론이 나오는 자리다.</b>
	 *
	 * <p>baseline 은 구조적으로 이 값을 낼 수 없다. 그래서 오도 설명이 있는 항목에서
	 * 무엇을 내는지가 중요하다 — {@code EXPLAINED} 를 낸다면 그것은 단순한 오답이 아니라
	 * <b>틀린 설명을 '설명 확인'으로 인증한 것</b>이고, 없는 설명을 놓치는 것보다 나쁘다.
	 *
	 * <p>Gate 결과도 함께 찍는다. baseline 의 Gate 가 맞더라도 <b>그 항목이 막은 것이
	 * 아니라면 우연</b>이므로, 막은 Risk 목록을 봐야 그 사실이 드러난다.
	 */
	private void printContradicted(List<Row> rows) {
		System.out.printf("%n=== ③ gold=CONTRADICTED 항목 ===%n");

		boolean found = false;
		for (Row row : rows) {
			for (Map.Entry<String, CoverageStatus> gold : row.scenario().goldLabels().entrySet()) {
				if (gold.getValue() != CoverageStatus.CONTRADICTED) {
					continue;
				}
				found = true;
				String riskId = gold.getKey();

				System.out.printf("  %s %s — baseline=%s / LLM=%s%n",
						row.scenario().id(), riskId,
						row.base().predicted().get(riskId),
						row.llm().predicted().get(riskId));
				System.out.printf("      Gate: baseline=%s 막은 Risk=%s%s%n",
						row.base().gateStatus(), row.base().blockingRiskIds(),
						row.base().blockingRiskIds().contains(riskId)
								? "" : "   ← 이 항목이 막은 게 아니다. Gate 가 맞았다면 우연이다");
				System.out.printf("            LLM=%s 막은 Risk=%s%n",
						row.llm().gateStatus(), row.llm().blockingRiskIds());
			}
		}

		if (!found) {
			System.out.println("  (데이터셋에 CONTRADICTED 라벨이 없다)");
		}
		System.out.println();
	}

	private record Row(EvalDataset.Scenario scenario,
	                   CoverageScorer.Score base,
	                   CoverageScorer.Score llm) {
	}
}
