package io.finready.coverage;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.common.StateMachine;
import io.finready.product.CoveragePolicy;
import io.finready.product.ProductRisk;
import io.finready.product.ProductRiskRepository;
import io.finready.session.ConsultationRevision;
import io.finready.session.ConsultationRevisionRepository;
import io.finready.session.ConsultationSession;
import io.finready.session.ConsultationSessionRepository;
import io.finready.session.SessionStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * F03 Coverage 분석 + Gate Override.
 *
 * <p><b>이 클래스에 {@code @Transactional} 이 없는 것은 의도적이다</b>(규칙 6). LLM 호출이
 * 트랜잭션 안에 들어가면 30초짜리 커넥션 점유가 생기고, DB role 의
 * {@code idle_in_transaction_session_timeout=30s} 에 걸려 런타임에 터진다. 흐름은
 * <b>읽기 → LLM 호출(트랜잭션 밖) → 쓰기</b> 이며, 쓰기만 {@link CoverageWriter} 가 묶는다.
 *
 * <p>판정 로직은 여기 없다. 상태 결정은 {@link CoverageStatusResolver}, 생성은
 * {@link CoverageResultFactory}, Gate 는 {@link GateEvaluator} 가 한다. 이 클래스는
 * 순서와 대상 선정만 책임진다.
 */
@Service
public class CoverageAnalysisService {

	/** {@code ck_override_reason_len} 과 같은 값. DB 가 터지기 전에 계약 오류로 돌려준다 */
	private static final int MIN_OVERRIDE_REASON_LENGTH = 5;

	private final ConsultationSessionRepository sessionRepository;
	private final ConsultationRevisionRepository revisionRepository;
	private final ProductRiskRepository productRiskRepository;
	private final CoverageResultRepository coverageResultRepository;
	private final GateOverrideRepository gateOverrideRepository;
	private final CoverageClassifier classifier;
	private final SemanticVerifier semanticVerifier;
	private final ProvenanceVerifier provenanceVerifier;
	private final CoverageResultFactory coverageResultFactory;
	private final GateEvaluator gateEvaluator;
	private final CoverageWriter writer;
	private final StateMachine stateMachine;

	public CoverageAnalysisService(ConsultationSessionRepository sessionRepository,
	                               ConsultationRevisionRepository revisionRepository,
	                               ProductRiskRepository productRiskRepository,
	                               CoverageResultRepository coverageResultRepository,
	                               GateOverrideRepository gateOverrideRepository,
	                               CoverageClassifier classifier,
	                               SemanticVerifier semanticVerifier,
	                               ProvenanceVerifier provenanceVerifier,
	                               CoverageResultFactory coverageResultFactory,
	                               GateEvaluator gateEvaluator,
	                               CoverageWriter writer,
	                               StateMachine stateMachine) {
		this.sessionRepository = sessionRepository;
		this.revisionRepository = revisionRepository;
		this.productRiskRepository = productRiskRepository;
		this.coverageResultRepository = coverageResultRepository;
		this.gateOverrideRepository = gateOverrideRepository;
		this.classifier = classifier;
		this.semanticVerifier = semanticVerifier;
		this.provenanceVerifier = provenanceVerifier;
		this.coverageResultFactory = coverageResultFactory;
		this.gateEvaluator = gateEvaluator;
		this.writer = writer;
		this.stateMachine = stateMachine;
	}

	/**
	 * Coverage 4상태 분석 + Gate 판정.
	 *
	 * <p><b>멱등하다.</b> 같은 revision 에 결과가 이미 있으면 LLM 을 다시 부르지 않고 그대로
	 * 돌려준다 — 새로고침이 요금과 레이턴시를 다시 물게 하지 않는다. 재분석은 새 revision 을
	 * 만든 뒤 호출한다.
	 */
	public CoverageResponse analyze(String sessionId, AnalyzeCoverageRequest request) {
		ConsultationSession session = loadSession(sessionId);
		if (stateMachine.isClosed(session.getStatus())) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					"종료된 상담은 다시 분석할 수 없습니다.");
		}

		ConsultationRevision revision = resolveRevision(sessionId, request.revisionId());
		Map<String, ProductRisk> risksById = loadAnalysableRisks(session.getProductId());

		List<CoverageResult> existing =
				coverageResultRepository.findByRevisionIdOrderByRiskIdAsc(revision.getId());
		if (!existing.isEmpty()) {
			// 멱등 재사용. 이번 호출에서 잰 레이턴시가 없으므로 analysis 는 null 이다
			return respond(session.getStatus(), sessionId, revision.getId(), existing, risksById, null);
		}

		Analysis analysis =
				runAnalysis(sessionId, revision, new ArrayList<>(risksById.values()));

		Map<String, GateOverride> overrides = loadOverrides(sessionId);
		GateEvaluator.GateVerdict verdict = gateEvaluator.evaluate(
				analysis.results(), policiesOf(risksById), overrides.keySet());

		SessionStatus status =
				writer.saveAnalysis(sessionId, analysis.results(), targetStatusOf(verdict));

		return CoverageResponse.of(sessionId, revision.getId(), status, verdict, analysis.results(),
				risksById, overrides, analysis.toView());
	}

	/**
	 * 직원 Override. AI 원판정과 evidence 는 건드리지 않고 {@code gate_override} 에 행만 추가한다(규칙 1).
	 *
	 * <p>LLM 을 부르지 않지만 쓰기는 {@link CoverageWriter} 를 그대로 탄다 — 같은 클래스 안에
	 * 트랜잭션 스타일이 둘이면 어느 경로가 어떤 경계인지 읽는 사람이 매번 확인해야 한다.
	 */
	public CoverageResponse override(String sessionId, GateOverrideRequest request) {
		ConsultationSession session = loadSession(sessionId);
		if (stateMachine.isClosed(session.getStatus())) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					"종료된 상담은 수정할 수 없습니다.");
		}

		List<String> riskIds = request.riskIds();
		if (riskIds == null || riskIds.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "Override 대상 Risk 를 선택해 주세요.");
		}
		if (request.category() == null) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "Override 사유 분류를 선택해 주세요.");
		}
		if (request.actor() == null || request.actor().isBlank()) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "처리자 정보가 필요합니다.");
		}
		String reason = request.reason();
		if (reason == null || reason.strip().length() < MIN_OVERRIDE_REASON_LENGTH) {
			throw new ApiException(ErrorCode.OVERRIDE_REASON_REQUIRED,
					"Override 사유를 " + MIN_OVERRIDE_REASON_LENGTH + "자 이상 입력해 주세요.");
		}

		ConsultationRevision revision = resolveRevision(sessionId, null);
		Map<String, ProductRisk> risksById = loadAnalysableRisks(session.getProductId());

		List<GateOverride> newOverrides = new ArrayList<>();
		for (String riskId : riskIds) {
			ProductRisk risk = risksById.get(riskId);
			if (risk == null) {
				throw new ApiException(ErrorCode.RISK_NOT_FOUND, "존재하지 않는 Risk 입니다.", riskId);
			}
			// understandingCheck Risk 는 질문 대상에 남길지를 직원이 명시해야 한다.
			// 빠뜨리면 그 Risk 가 질문에서 조용히 사라지거나 남거나가 우연히 정해진다
			if (risk.isUnderstandingCheck() && request.staffExplanationConfirmed() == null) {
				throw new ApiException(ErrorCode.STAFF_EXPLANATION_CONFIRMATION_REQUIRED,
						"이해 확인 대상 Risk 는 설명 완료 여부를 함께 선택해 주세요.", riskId);
			}
			newOverrides.add(new GateOverride(sessionId, riskId, request.category(),
					reason.strip(), request.staffExplanationConfirmed(), request.actor()));
		}

		List<CoverageResult> results =
				coverageResultRepository.findByRevisionIdOrderByRiskIdAsc(revision.getId());
		if (results.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					"Coverage 분석 전에는 Override 할 수 없습니다.");
		}

		Set<String> overriddenRiskIds = new java.util.LinkedHashSet<>(loadOverrides(sessionId).keySet());
		overriddenRiskIds.addAll(riskIds);

		GateEvaluator.GateVerdict verdict =
				gateEvaluator.evaluate(results, policiesOf(risksById), overriddenRiskIds);

		SessionStatus status = writer.saveOverrides(sessionId, newOverrides, targetStatusOf(verdict));

		return CoverageResponse.of(sessionId, revision.getId(), status, verdict, results,
				risksById, loadOverrides(sessionId), null);
	}

	// ------------------------------------------------------------------
	// 분석 파이프라인 — 여기 안에서는 트랜잭션이 열려 있지 않다 (규칙 6)
	// ------------------------------------------------------------------

	private Analysis runAnalysis(String sessionId,
	                             ConsultationRevision revision,
	                             List<ProductRisk> risks) {
		String transcript = revision.getText();

		List<CoverageClassifier.RiskPrompt> prompts = risks.stream()
				.map(risk -> new CoverageClassifier.RiskPrompt(
						risk.getRiskId(), risk.getTitle(), risk.getFact()))
				.toList();

		long classifierStart = System.nanoTime();
		List<CoverageClassifier.RiskVerdict> verdicts = classifier.classify(transcript, prompts);
		long classifierMs = elapsedMs(classifierStart);

		Map<String, CoverageClassifier.RiskVerdict> verdictsById =
				indexExactly(verdicts, CoverageClassifier.RiskVerdict::riskId, prompts.size(),
						risks.stream().map(ProductRisk::getRiskId).collect(Collectors.toSet()));

		// 서버가 원문에서 다시 찾는다. LLM 이 준 offset 은 애초에 포트가 받지 않는다 (규칙 4)
		Map<String, ProvenanceCheck> provenanceById = new LinkedHashMap<>();
		for (ProductRisk risk : risks) {
			provenanceById.put(risk.getRiskId(), provenanceVerifier.verify(
					transcript, verdictsById.get(risk.getRiskId()).evidenceText()));
		}

		List<SemanticVerifier.VerificationRequest> verifierTargets =
				selectVerifierTargets(risks, verdictsById, provenanceById);

		long verifierStart = System.nanoTime();
		Map<String, SemanticVerifier.RelationVerdict> relationsById = verifierTargets.isEmpty()
				? Map.of()
				: indexExactly(semanticVerifier.verify(transcript, verifierTargets),
						SemanticVerifier.RelationVerdict::riskId, verifierTargets.size(),
						verifierTargets.stream()
								.map(SemanticVerifier.VerificationRequest::riskId)
								.collect(Collectors.toSet()));
		long verifierMs = verifierTargets.isEmpty() ? 0L : elapsedMs(verifierStart);

		List<CoverageResult> results = new ArrayList<>(risks.size());
		for (ProductRisk risk : risks) {
			CoverageClassifier.RiskVerdict verdict = verdictsById.get(risk.getRiskId());
			SemanticVerifier.RelationVerdict relation = relationsById.get(risk.getRiskId());

			results.add(coverageResultFactory.create(
					sessionId,
					revision.getId(),
					risk.getRiskId(),
					verdict.status(),
					verdict.reason(),
					relation == null ? null : relation.reason(),
					verdict.evidenceText(),
					provenanceById.get(risk.getRiskId()),
					relation == null ? null : relation.relation()));
		}
		return new Analysis(results, classifierMs, verifierMs,
				classifier.promptVersion(),
				verifierTargets.isEmpty() ? null : semanticVerifier.promptVersion());
	}

	/**
	 * Verifier 를 어떤 Risk 까지 돌릴지.
	 *
	 * <p>계약 문구는 "GATE_REQUIRED + CONTRADICTED 후보"지만 <b>EXPLAINED 후보를 더한다.</b>
	 * 규칙 3 때문에 EXPLAINED 는 {@code semantic = SUPPORTS} 없이는 성립할 수 없어서,
	 * Verifier 를 안 돌린 EXPLAINED 는 {@link CoverageStatusResolver} 가 INSUFFICIENT 로 접는다.
	 * 계약대로만 하면 <b>잘 설명한 WARN_ONLY Risk 가 경고로 둔갑한다.</b>
	 *
	 * <p>실측으로 확인된 문제다 (2026-08-18, CONS_A_003):
	 * <pre>
	 * R06  classifierStatus EXPLAINED   ← 분류기는 정확했다 (라벨과 일치)
	 *      semanticRelation null        ← WARN_ONLY 라 대상에서 빠짐
	 *      coverageStatus   INSUFFICIENT ← 우리가 접었다
	 * </pre>
	 * 대상이 3개 → 4개로 늘 뿐이라 비용 증가는 미미하다. 자세한 경위는
	 * {@code docs/decisions/2026-08-18-explained-constraint-null-hole.md}.
	 *
	 * <p>근거가 원문 대조를 통과하지 못한 Risk 는 여전히 제외한다. 의미 판정의 입력이 될
	 * 인용 자체가 없으므로 물어볼 것이 없고, 물어봤자 요금만 든다.
	 */
	private List<SemanticVerifier.VerificationRequest> selectVerifierTargets(
			List<ProductRisk> risks,
			Map<String, CoverageClassifier.RiskVerdict> verdictsById,
			Map<String, ProvenanceCheck> provenanceById) {

		List<SemanticVerifier.VerificationRequest> targets = new ArrayList<>();
		for (ProductRisk risk : risks) {
			CoverageClassifier.RiskVerdict verdict = verdictsById.get(risk.getRiskId());

			boolean inScope = risk.getCoveragePolicy() == CoveragePolicy.GATE_REQUIRED
					|| verdict.status() == CoverageStatus.CONTRADICTED
					// EXPLAINED 는 semantic 없이 성립하지 않는다 (규칙 3)
					|| verdict.status() == CoverageStatus.EXPLAINED;
			if (!inScope || !provenanceById.get(risk.getRiskId()).valid()) {
				continue;
			}
			targets.add(new SemanticVerifier.VerificationRequest(
					risk.getRiskId(), risk.getTitle(), risk.getFact(), verdict.evidenceText()));
		}
		return targets;
	}

	// ------------------------------------------------------------------
	// 읽기 / 공통
	// ------------------------------------------------------------------

	private ConsultationSession loadSession(String sessionId) {
		return sessionRepository.findById(sessionId)
				.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND,
						"상담 세션을 찾을 수 없습니다."));
	}

	/** 계약이 "분석할 revision 이 없음"을 409 로 정의한다 — 404 가 아니다 */
	private ConsultationRevision resolveRevision(String sessionId, Long revisionId) {
		ConsultationRevision revision = revisionRepository
				.findTopBySessionIdOrderByRevisionNoDesc(sessionId)
				.orElseThrow(() -> new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
						"분석할 상담 내용이 없습니다. 상담 내용을 먼저 저장해 주세요."));

		if (revisionId == null || revisionId.equals(revision.getId())) {
			return revision;
		}
		return revisionRepository.findById(revisionId)
				.filter(candidate -> candidate.getSessionId().equals(sessionId))
				.orElseThrow(() -> new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
						"해당 상담 내용을 찾을 수 없습니다."));
	}

	/**
	 * {@code NOT_APPLICABLE} 은 분석 대상이 아니다 — 분류기에 보내지도, 결과 행을 만들지도 않는다.
	 * 상품에 해당하지 않는 Risk 를 "설명 안 됨"으로 세면 Gate 가 영원히 안 열린다.
	 */
	private Map<String, ProductRisk> loadAnalysableRisks(String productId) {
		Map<String, ProductRisk> risks = productRiskRepository
				.findByProductIdOrderByRiskIdAsc(productId).stream()
				.filter(risk -> risk.getCoveragePolicy() != CoveragePolicy.NOT_APPLICABLE)
				.collect(Collectors.toMap(ProductRisk::getRiskId, Function.identity(),
						(first, second) -> first, LinkedHashMap::new));

		if (risks.isEmpty()) {
			throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "상품의 Risk 정보를 찾을 수 없습니다.");
		}
		return risks;
	}

	private Map<String, GateOverride> loadOverrides(String sessionId) {
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

	private SessionStatus targetStatusOf(GateEvaluator.GateVerdict verdict) {
		return verdict.canProceedToUnderstanding()
				? SessionStatus.COVERAGE_ANALYZED
				: SessionStatus.GATE_BLOCKED;
	}

	private CoverageResponse respond(SessionStatus status,
	                                 String sessionId,
	                                 Long revisionId,
	                                 List<CoverageResult> results,
	                                 Map<String, ProductRisk> risksById,
	                                 CoverageResponse.AnalysisView analysis) {
		Map<String, GateOverride> overrides = loadOverrides(sessionId);
		GateEvaluator.GateVerdict verdict =
				gateEvaluator.evaluate(results, policiesOf(risksById), overrides.keySet());
		return CoverageResponse.of(sessionId, revisionId, status, verdict, results,
				risksById, overrides, analysis);
	}

	/**
	 * 배치 응답이 요청한 Risk 집합과 <b>정확히</b> 일치하는지 본다. 누락·중복·모르는 riskId 는
	 * 전부 파싱 실패다 — 빠진 Risk 를 기본값으로 채우면 그게 곧 "임의 매핑"이고(규칙 9),
	 * 설명 안 된 Risk 가 조용히 통과할 수 있다.
	 */
	private <T> Map<String, T> indexExactly(List<T> items,
	                                        Function<T, String> riskIdOf,
	                                        int expectedSize,
	                                        Set<String> expectedRiskIds) {
		if (items == null || items.size() != expectedSize) {
			throw new ApiException(ErrorCode.AI_PARSING_FAILED,
					"분석 결과를 해석하지 못했습니다. 잠시 후 다시 시도해 주세요.");
		}
		Map<String, T> byRiskId = new LinkedHashMap<>();
		for (T item : items) {
			if (byRiskId.put(riskIdOf.apply(item), item) != null) {
				throw new ApiException(ErrorCode.AI_PARSING_FAILED,
						"분석 결과를 해석하지 못했습니다. 잠시 후 다시 시도해 주세요.");
			}
		}
		if (!byRiskId.keySet().equals(expectedRiskIds)) {
			throw new ApiException(ErrorCode.AI_PARSING_FAILED,
					"분석 결과를 해석하지 못했습니다. 잠시 후 다시 시도해 주세요.");
		}
		return byRiskId;
	}

	private long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000L;
	}

	/**
	 * 결과와 레이턴시를 함께 돌려준다. 레이턴시를 서비스 필드에 담으면 싱글턴 빈에서
	 * 동시 요청끼리 서로의 값을 덮어쓴다 — 관측값이라 틀려도 티가 안 나는 종류의 버그다.
	 */
	private record Analysis(List<CoverageResult> results,
	                       long classifierMs,
	                       long verifierMs,
	                       String classifierPromptVersion,
	                       String verifierPromptVersion) {

		CoverageResponse.AnalysisView toView() {
			return new CoverageResponse.AnalysisView(
					classifierMs, verifierMs, promptVersionLabel(), 0);
		}

		/**
		 * 계약의 {@code promptVersion} 은 문자열 하나지만 판정은 <b>두 프롬프트가 함께</b>
		 * 만든다. 분류기 버전만 적으면 Verifier 를 고친 실험이 같은 값으로 찍혀 재현이 안 된다.
		 * 계약은 형식만 정하므로(자유 문자열) 둘을 합쳐 적는다.
		 *
		 * <p>정확한 호출별 버전은 {@code llm_call_log.prompt_version} 에 따로 남는다.
		 */
		private String promptVersionLabel() {
			if (classifierPromptVersion == null) {
				return null;
			}
			return verifierPromptVersion == null
					? classifierPromptVersion
					: classifierPromptVersion + "+" + verifierPromptVersion;
		}
	}
}
