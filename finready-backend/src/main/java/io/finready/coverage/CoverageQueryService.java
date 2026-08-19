package io.finready.coverage;

import io.finready.product.CoveragePolicy;
import io.finready.product.ProductRisk;
import io.finready.product.ProductRiskRepository;
import io.finready.session.ConsultationRevisionRepository;
import io.finready.session.ConsultationSession;
import io.finready.session.SessionStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 저장된 Coverage 결과를 읽어 계약 응답으로 되살린다. <b>LLM 을 부르지 않는다.</b>
 *
 * <p>{@code GET /sessions/{id}} 의 {@code coverage} 필드와 {@link CoverageAnalysisService} 의
 * 멱등 재사용 경로가 <b>같은 코드를 쓰게 하려고</b> 뺐다. 응답을 만드는 곳이 둘이면
 * Gate 재계산 규칙이 갈라지고, 새로고침 결과가 분석 직후 결과와 달라진다.
 *
 * <p>Gate 는 저장하지 않고 매번 다시 판정한다 — {@code gate_override} 가 추가되면 같은
 * {@code coverage_result} 로도 결론이 바뀌기 때문이다(규칙 2, 합성 상태를 저장하지 않는다).
 */
@Service
public class CoverageQueryService {

	private final ConsultationRevisionRepository revisionRepository;
	private final CoverageResultRepository coverageResultRepository;
	private final ProductRiskRepository productRiskRepository;
	private final GateOverrideRepository gateOverrideRepository;
	private final GateEvaluator gateEvaluator;

	public CoverageQueryService(ConsultationRevisionRepository revisionRepository,
	                            CoverageResultRepository coverageResultRepository,
	                            ProductRiskRepository productRiskRepository,
	                            GateOverrideRepository gateOverrideRepository,
	                            GateEvaluator gateEvaluator) {
		this.revisionRepository = revisionRepository;
		this.coverageResultRepository = coverageResultRepository;
		this.productRiskRepository = productRiskRepository;
		this.gateOverrideRepository = gateOverrideRepository;
		this.gateEvaluator = gateEvaluator;
	}

	/**
	 * 최신 revision 의 Coverage 결과. 아직 분석 전이면 비어 있다.
	 *
	 * <p>계약이 {@code coverage: null} 을 허용하므로 없는 것은 오류가 아니다 —
	 * DRAFT 세션을 새로고침한 정상적인 경우다.
	 */
	public Optional<CoverageResponse> latestFor(ConsultationSession session) {
		return revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(session.getId())
				.flatMap(revision -> {
					List<CoverageResult> results = coverageResultRepository
							.findByRevisionIdOrderByRiskIdAsc(revision.getId());
					if (results.isEmpty()) {
						return Optional.empty();
					}
					return Optional.of(respond(session.getStatus(), session.getId(),
							revision.getId(), results, analysableRisks(session.getProductId()),
							// 이번 호출에서 잰 레이턴시가 없다. 0 을 넣으면 "0ms 에 끝났다"로 읽혀
							// 관측 데이터가 오염된다
							null));
				});
	}

	/**
	 * F08 리포트의 {@code overrides} 섹션. Gate 판정과 무관하게 <b>직원이 무엇을 넘겼는지</b>
	 * 그대로 싣는다 — Override 로 열린 Gate 는 열렸다는 사실보다 사유가 중요하다.
	 *
	 * <p>{@code GateOverride} 엔티티를 패키지 밖으로 내보내지 않는다. 계약에 없는 컬럼이
	 * 리포트로 새는 것을 막는 것은 F01 에서 {@code DemoProductResponse} 를 둔 것과 같은 이유다.
	 */
	public List<CoverageResponse.OverrideView> overrideRecordsOf(String sessionId) {
		return overridesOf(sessionId).values().stream()
				.map(CoverageResponse.OverrideView::from)
				.toList();
	}

	/**
	 * 저장된 결과 + 현재 Override 로 Gate 를 다시 판정해 응답을 만든다.
	 *
	 * <p>{@link CoverageAnalysisService} 의 멱등 경로와 Override 경로가 함께 쓴다.
	 */
	CoverageResponse respond(SessionStatus status,
	                         String sessionId,
	                         Long revisionId,
	                         List<CoverageResult> results,
	                         Map<String, ProductRisk> risksById,
	                         CoverageResponse.AnalysisView analysis) {

		Map<String, GateOverride> overrides = overridesOf(sessionId);
		GateEvaluator.GateVerdict verdict =
				gateEvaluator.evaluate(results, policiesOf(risksById), overrides.keySet());

		return CoverageResponse.of(sessionId, revisionId, status, verdict, results,
				risksById, overrides, analysis);
	}

	/**
	 * {@code NOT_APPLICABLE} 은 분석 대상이 아니다 — 상품에 해당하지 않는 Risk 를
	 * "설명 안 됨"으로 세면 Gate 가 영원히 안 열린다.
	 */
	Map<String, ProductRisk> analysableRisks(String productId) {
		return productRiskRepository.findByProductIdOrderByRiskIdAsc(productId).stream()
				.filter(risk -> risk.getCoveragePolicy() != CoveragePolicy.NOT_APPLICABLE)
				.collect(Collectors.toMap(ProductRisk::getRiskId, Function.identity(),
						(first, second) -> first, LinkedHashMap::new));
	}

	Map<String, GateOverride> overridesOf(String sessionId) {
		return gateOverrideRepository.findBySessionIdOrderByRiskIdAsc(sessionId).stream()
				.collect(Collectors.toMap(GateOverride::getRiskId, Function.identity(),
						(first, second) -> first, LinkedHashMap::new));
	}

	private Map<String, CoveragePolicy> policiesOf(Map<String, ProductRisk> risksById) {
		return risksById.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey,
						entry -> entry.getValue().getCoveragePolicy(),
						(first, second) -> first, LinkedHashMap::new));
	}
}
