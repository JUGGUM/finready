package io.finready.coverage;

import io.finready.product.CoveragePolicy;
import io.finready.product.ProductRisk;
import io.finready.session.SessionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * openapi.yml v1.4.2 CoverageResponse.
 * required 는 [sessionId, revisionId, gateStatus, canProceedToUnderstanding, risks] 다.
 *
 * <p>엔티티를 그대로 내보내지 않는다 — {@code coverage_result} 의 id·created_at 처럼 계약에
 * 없는 컬럼이 새는 것을 막는다(F01 에서 같은 이유로 DemoProductResponse 를 뒀다).
 */
public record CoverageResponse(
		String sessionId,
		Long revisionId,
		SessionStatus sessionStatus,
		GateStatus gateStatus,
		boolean canProceedToUnderstanding,
		List<String> blockingRiskIds,
		List<String> warningRiskIds,
		List<RiskView> risks,
		AnalysisView analysis
) {

	/**
	 * @param classifierStatus AI 원판정. 절대 변경되지 않는다(PRD §7.6)
	 * @param coverageStatus   검증 후 확정 상태. Gate 판정은 이 값을 쓴다
	 * @param downgraded       둘이 다른지. <b>배지 표시용 파생 필드이며 저장하지 않는다</b>(규칙 2)
	 */
	public record RiskView(
			String riskId,
			String title,
			CoveragePolicy coveragePolicy,
			CoverageStatus classifierStatus,
			CoverageStatus coverageStatus,
			boolean downgraded,
			String classifierReason,
			String verificationReason,
			EvidenceView evidence,
			SemanticRelation semanticRelation,
			OverrideView override
	) {

		static RiskView from(CoverageResult result, ProductRisk risk, GateOverride override) {
			return new RiskView(
					result.getRiskId(),
					risk == null ? null : risk.getTitle(),
					risk == null ? null : risk.getCoveragePolicy(),
					result.getClassifierStatus(),
					result.getCoverageStatus(),
					result.getClassifierStatus() != result.getCoverageStatus(),
					result.getClassifierReason(),
					result.getVerificationReason(),
					EvidenceView.from(result),
					result.getSemanticRelation(),
					OverrideView.from(override));
		}
	}

	/**
	 * offset 은 <b>원문(revision.text) 기준 UTF-16 code unit</b> 이며 서버가 재계산한 값이다.
	 * LLM 이 반환한 offset 은 쓰지 않는다(규칙 4).
	 */
	public record EvidenceView(
			String text,
			Integer startOffset,
			Integer endOffset,
			boolean provenanceValid,
			ProvenanceFailureReason provenanceFailureReason
	) {

		/** 인용 자체가 없으면 evidence 를 만들지 않는다 — 계약이 null 을 허용한다 */
		static EvidenceView from(CoverageResult result) {
			if (result.getEvidenceText() == null || result.getEvidenceText().isBlank()) {
				return null;
			}
			return new EvidenceView(
					result.getEvidenceText(),
					result.getEvidenceStart(),
					result.getEvidenceEnd(),
					result.isProvenanceValid(),
					result.getProvenanceFailureReason());
		}
	}

	public record OverrideView(
			String riskId,
			OverrideCategory category,
			String reason,
			Boolean staffExplanationConfirmed,
			String actor,
			OffsetDateTime createdAt
	) {

		static OverrideView from(GateOverride override) {
			if (override == null) {
				return null;
			}
			return new OverrideView(
					override.getRiskId(),
					override.getCategory(),
					override.getReason(),
					override.getStaffExplanationConfirmed(),
					override.getActor(),
					override.getCreatedAt());
		}
	}

	/**
	 * 성능·재현 관측용. 화면 표시 의무는 없으며 고객 View 에는 렌더링하지 않는다.
	 *
	 * <p>멱등 재사용(이미 분석된 revision)일 때는 이번 호출에서 잰 값이 없으므로 null 이다.
	 * 0 을 넣으면 "0ms 에 끝났다"로 읽혀 관측 데이터가 오염된다.
	 */
	public record AnalysisView(
			Long classifierLatencyMs,
			Long verifierLatencyMs,
			String promptVersion,
			int ambiguityRetryCount
	) {
	}

	static CoverageResponse of(String sessionId,
	                           Long revisionId,
	                           SessionStatus sessionStatus,
	                           GateEvaluator.GateVerdict verdict,
	                           List<CoverageResult> results,
	                           Map<String, ProductRisk> risksById,
	                           Map<String, GateOverride> overridesByRiskId,
	                           AnalysisView analysis) {

		List<RiskView> riskViews = results.stream()
				.map(result -> RiskView.from(
						result,
						risksById.get(result.getRiskId()),
						overridesByRiskId.get(result.getRiskId())))
				.toList();

		return new CoverageResponse(
				sessionId,
				revisionId,
				sessionStatus,
				verdict.gateStatus(),
				verdict.canProceedToUnderstanding(),
				verdict.blockingRiskIds(),
				verdict.warningRiskIds(),
				riskViews,
				analysis);
	}
}
