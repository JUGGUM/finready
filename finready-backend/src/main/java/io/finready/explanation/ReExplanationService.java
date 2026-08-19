package io.finready.explanation;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.common.GenerationSource;
import io.finready.common.StateMachine;
import io.finready.product.CustomerProfile;
import io.finready.product.CustomerProfileRepository;
import io.finready.product.Product;
import io.finready.product.ProductRepository;
import io.finready.product.ProductRisk;
import io.finready.product.ProductRiskRepository;
import io.finready.session.ConsultationSession;
import io.finready.session.ConsultationSessionRepository;
import io.finready.understanding.NextAction;
import io.finready.understanding.UnderstandingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * F06 검수 근거 직접조회 기반 재설명.
 *
 * <p><b>Vector 검색을 하지 않는다</b>(계약). {@code riskId} 로 검수된 Risk Schema 의
 * {@code sourcePage}·{@code sourceText} 를 직접 조회해 LLM 에 넘긴다.
 *
 * <p>{@code CoverageAnalysisService} 와 같은 이유로 <b>{@code @Transactional} 이 없다</b>(규칙 6) —
 * 읽기 → LLM 호출(트랜잭션 밖) → 쓰기이고, 쓰기만 {@link ReExplanationWriter} 가 묶는다.
 *
 * <p>진입 조건 확인과 후속 질문 발급은 {@link UnderstandingService} 에 맡긴다. 판정 이력을
 * 읽는 규칙과 질문 발급 규칙이 두 모듈로 갈라지면 TRD §4.2·§4.6 의 일원화가 깨진다.
 */
@Service
public class ReExplanationService {

	private static final Logger log = LoggerFactory.getLogger(ReExplanationService.class);

	private final ConsultationSessionRepository sessionRepository;
	private final ProductRepository productRepository;
	private final ProductRiskRepository productRiskRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final ReExplanationRepository reExplanationRepository;
	private final ReExplanationGenerator generator;
	private final Guardrail guardrail;
	private final ReExplanationWriter writer;
	private final UnderstandingService understandingService;
	private final StateMachine stateMachine;

	public ReExplanationService(ConsultationSessionRepository sessionRepository,
	                            ProductRepository productRepository,
	                            ProductRiskRepository productRiskRepository,
	                            CustomerProfileRepository customerProfileRepository,
	                            ReExplanationRepository reExplanationRepository,
	                            ReExplanationGenerator generator,
	                            Guardrail guardrail,
	                            ReExplanationWriter writer,
	                            UnderstandingService understandingService,
	                            StateMachine stateMachine) {
		this.sessionRepository = sessionRepository;
		this.productRepository = productRepository;
		this.productRiskRepository = productRiskRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.reExplanationRepository = reExplanationRepository;
		this.generator = generator;
		this.guardrail = guardrail;
		this.writer = writer;
		this.understandingService = understandingService;
		this.stateMachine = stateMachine;
	}

	public ReExplanationResponse reExplain(String sessionId, ReExplainRequest request) {
		ConsultationSession session = loadOpenSession(sessionId);
		String riskId = require(request == null ? null : request.riskId());

		// MISUNDERSTOOD 가 아니면 409 — UNCERTAIN 은 재설명 없이 바로 후속 확인으로 간다 (PRD §7.5)
		String customerAnswer = understandingService.requireMisunderstoodAnswer(sessionId, riskId);

		ProductRisk risk = productRiskRepository
				.findByProductIdOrderByRiskIdAsc(session.getProductId()).stream()
				.filter(candidate -> candidate.getRiskId().equals(riskId))
				.findFirst()
				.orElseThrow(() -> new ApiException(ErrorCode.RISK_NOT_FOUND,
						"상품 위험 항목을 찾을 수 없습니다.", riskId));

		ReExplanation stored = reExplanationRepository
				.findFirstBySessionIdAndRiskIdOrderByIdDesc(sessionId, riskId)
				.orElseGet(() -> writer.save(generate(session, risk, customerAnswer)));

		// 재설명이 저장된 뒤에 발급한다 — 생성이 실패한 Risk 에 후속 질문만 남으면
		// 새로고침 시 재설명 없이 질문부터 보이게 된다
		UnderstandingService.IssuedQuestion recheck =
				understandingService.issueRecheckQuestion(sessionId, riskId);

		return new ReExplanationResponse(
				riskId,
				customerAnswer,
				risk.getFact(),
				stored.getSourcePage(),
				stored.getSourceText(),
				documentUrlOf(session),
				stored.getExplanation(),
				stored.getGenerationSource(),
				new ReExplanationResponse.GuardrailView(
						stored.isGuardrailRetried(), parseViolations(stored.getGuardrailViolations())),
				recheck.question(),
				recheck.source(),
				// 재설명 후에는 항상 RECHECK 다 (계약). 분기 여지가 없어 Resolver 를 거치지 않는다
				NextAction.RECHECK);
	}

	// ------------------------------------------------------------------

	/**
	 * 생성 → Guardrail → 위반 시 1회 재생성 → 재실패면 검수된 {@code fallbackPlainExplanation}.
	 *
	 * <p>fallback 은 실패가 아니라 <b>계약이 정한 정상 경로</b>다. 검수된 문장이므로 내용이
	 * 틀릴 수 없고, {@code source: FALLBACK} 으로 화면에 표시된다.
	 */
	private ReExplanation generate(ConsultationSession session, ProductRisk risk, String customerAnswer) {
		String evidence = risk.getFact() + "\n" + risk.getSourceText();
		String explanationLevel = explanationLevelOf(session);

		List<GuardrailViolation> violations = List.of();
		boolean retried = false;

		for (int attempt = 1; attempt <= 2; attempt++) {
			String candidate = callGenerator(risk, customerAnswer, explanationLevel);
			if (candidate == null || candidate.isBlank()) {
				break;
			}
			violations = guardrail.inspect(candidate, evidence);
			if (violations.isEmpty()) {
				return new ReExplanation(session.getId(), risk.getRiskId(),
						risk.getSourcePage(), risk.getSourceText(), candidate,
						GenerationSource.LLM, retried, null);
			}
			log.warn("Guardrail 위반 — riskId={}, attempt={}, violations={}",
					risk.getRiskId(), attempt, violations);
			retried = true;
		}

		return new ReExplanation(session.getId(), risk.getRiskId(),
				risk.getSourcePage(), risk.getSourceText(), risk.getFallbackPlainExplanation(),
				GenerationSource.FALLBACK, retried, joinViolations(violations));
	}

	/**
	 * 생성 실패는 흐름을 막지 않는다 — 검수된 {@code fallbackPlainExplanation} 이 있으므로
	 * 여기서 던지면 재설명 화면 자체를 못 띄운다. F04 의 질문 생성과 같은 판단이다.
	 */
	private String callGenerator(ProductRisk risk, String customerAnswer, String explanationLevel) {
		try {
			return generator.explain(risk.getRiskId(), risk.getTitle(), risk.getFact(),
					risk.getSourceText(), customerAnswer, explanationLevel);
		} catch (RuntimeException ex) {
			log.warn("재설명 생성 실패. 검수 문장으로 대체한다 (riskId={})", risk.getRiskId(), ex);
			return null;
		}
	}

	private String explanationLevelOf(ConsultationSession session) {
		return customerProfileRepository.findById(session.getCustomerId())
				.map(CustomerProfile::getExplanationLevel)
				.map(Enum::name)
				.orElse(null);
	}

	private String documentUrlOf(ConsultationSession session) {
		return productRepository.findById(session.getProductId())
				.map(Product::getDocumentUrl)
				.orElse(null);
	}

	private String joinViolations(List<GuardrailViolation> violations) {
		return violations.isEmpty() ? null : violations.stream()
				.map(Enum::name)
				.reduce((left, right) -> left + "," + right)
				.orElse(null);
	}

	/** 계약 밖 값이 들어 있으면 조용히 버린다 — 과거 행 때문에 조회가 실패하면 안 된다 */
	private List<GuardrailViolation> parseViolations(String stored) {
		if (stored == null || stored.isBlank()) {
			return List.of();
		}
		return Arrays.stream(stored.split(","))
				.map(String::strip)
				.map(this::toViolation)
				.flatMap(Optional::stream)
				.toList();
	}

	private Optional<GuardrailViolation> toViolation(String name) {
		try {
			return Optional.of(GuardrailViolation.valueOf(name));
		} catch (IllegalArgumentException ex) {
			log.warn("알 수 없는 guardrail 위반 값이 저장돼 있다: {}", name);
			return Optional.empty();
		}
	}

	private ConsultationSession loadOpenSession(String sessionId) {
		ConsultationSession session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND,
						"상담 세션을 찾을 수 없습니다."));
		if (stateMachine.isClosed(session.getStatus())) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					"종료된 상담은 수정할 수 없습니다.");
		}
		return session;
	}

	private String require(String riskId) {
		if (riskId == null || riskId.isBlank()) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "riskId 값이 필요합니다.");
		}
		return riskId;
	}
}
