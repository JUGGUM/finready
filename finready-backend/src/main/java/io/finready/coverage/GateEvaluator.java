package io.finready.coverage;

import io.finready.product.CoveragePolicy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gate 판정 (PRD §7.4). <b>서버만 계산한다</b> — 프론트는 risks 배열을 순회해 재계산하지 않고
 * {@code gateStatus} / {@code canProceedToUnderstanding} 를 그대로 쓴다(규칙 8).
 *
 * <p>판정 대상은 {@code coverageStatus} 다. {@code classifierStatus}(AI 원판정)로 Gate 를
 * 매기면 검증을 통과하지 못한 근거로 문을 열게 된다.
 */
@Component
public class GateEvaluator {

	/** GATE_REQUIRED Risk 가 이 상태면 문을 막는다. EXPLAINED 만 통과다 */
	private static final Set<CoverageStatus> BLOCKING =
			Set.of(CoverageStatus.INSUFFICIENT, CoverageStatus.NOT_FOUND, CoverageStatus.CONTRADICTED);

	/**
	 * @param results       분석된 Risk 별 결과
	 * @param policies      riskId → coveragePolicy. NOT_APPLICABLE 은 애초에 분석 대상이 아니다
	 * @param overriddenRiskIds 직원이 Override 한 Risk. Gate 판정에서만 빠지고 기록은 남는다
	 */
	public GateVerdict evaluate(List<CoverageResult> results,
	                            Map<String, CoveragePolicy> policies,
	                            Set<String> overriddenRiskIds) {

		List<String> blockingRiskIds = results.stream()
				.filter(r -> policies.get(r.getRiskId()) == CoveragePolicy.GATE_REQUIRED)
				.filter(r -> BLOCKING.contains(r.getCoverageStatus()))
				.map(CoverageResult::getRiskId)
				.filter(riskId -> !overriddenRiskIds.contains(riskId))
				.toList();

		// WARN_ONLY 는 문을 막지 않는다. 종료 전 acknowledge 대상으로만 모은다
		List<String> warningRiskIds = results.stream()
				.filter(r -> policies.get(r.getRiskId()) == CoveragePolicy.WARN_ONLY)
				.filter(r -> BLOCKING.contains(r.getCoverageStatus()))
				.map(CoverageResult::getRiskId)
				.toList();

		if (!blockingRiskIds.isEmpty()) {
			return new GateVerdict(GateStatus.GATE_BLOCKED, false, blockingRiskIds, warningRiskIds);
		}

		// Override 로 열렸는지, 원래 열려 있었는지를 구분한다. 리포트에 남아야 하는 차이다
		boolean openedByOverride = results.stream()
				.filter(r -> policies.get(r.getRiskId()) == CoveragePolicy.GATE_REQUIRED)
				.filter(r -> BLOCKING.contains(r.getCoverageStatus()))
				.anyMatch(r -> overriddenRiskIds.contains(r.getRiskId()));

		GateStatus status = openedByOverride
				? GateStatus.READY_WITH_STAFF_OVERRIDE
				: GateStatus.READY_FOR_UNDERSTANDING;

		return new GateVerdict(status, true, List.of(), warningRiskIds);
	}

	/**
	 * @param canProceedToUnderstanding 고객 이해 확인 버튼 활성화 여부. 프론트가 재계산하지 않는다
	 * @param blockingRiskIds           현재 Gate 를 막고 있는 Risk. 빈 배열이면 통과
	 * @param warningRiskIds            WARN_ONLY 중 미확인·불충분. 종료 전 acknowledge 대상
	 */
	public record GateVerdict(
			GateStatus gateStatus,
			boolean canProceedToUnderstanding,
			List<String> blockingRiskIds,
			List<String> warningRiskIds
	) {
	}
}
