package io.finready.session;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.common.StateMachine;
import io.finready.coverage.CoverageQueryService;
import io.finready.product.CustomerProfileRepository;
import io.finready.product.Product;
import io.finready.product.ProductRepository;
import io.finready.understanding.NextAction;
import io.finready.understanding.RiskUnderstandingState;
import io.finready.understanding.UnderstandingQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * F01 세션 생성 / F02 Revision 저장 / 세션 상태 복구.
 */
@Service
public class SessionService {

	/** consultation_revision.ck_char_count 상한. 계약의 maxLength 와 같다 */
	private static final int MAX_TRANSCRIPT_LENGTH = 8000;

	private final ConsultationSessionRepository sessionRepository;
	private final ConsultationRevisionRepository revisionRepository;
	private final ProductRepository productRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final CoverageQueryService coverageQueryService;
	private final UnderstandingQueryService understandingQueryService;
	private final StateMachine stateMachine;

	public SessionService(ConsultationSessionRepository sessionRepository,
	                      ConsultationRevisionRepository revisionRepository,
	                      ProductRepository productRepository,
	                      CustomerProfileRepository customerProfileRepository,
	                      CoverageQueryService coverageQueryService,
	                      UnderstandingQueryService understandingQueryService,
	                      StateMachine stateMachine) {
		this.sessionRepository = sessionRepository;
		this.revisionRepository = revisionRepository;
		this.productRepository = productRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.coverageQueryService = coverageQueryService;
		this.understandingQueryService = understandingQueryService;
		this.stateMachine = stateMachine;
	}

	/**
	 * 생성 시점의 productRiskVersion 을 snapshot 으로 고정한다.
	 * 시드가 바뀌어도 진행 중 세션의 판정 기준은 변하지 않는다 (TRD §4.1).
	 */
	@Transactional
	public SessionResponse createSession(CreateSessionRequest request) {
		String productId = require(request.productId(), "productId");
		String customerId = require(request.customerId(), "customerId");

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND,
						"상품을 찾을 수 없습니다."));

		// 계약 ErrorCode 에 고객용 404 가 없다. 존재하지 않는 customerId 는 잘못된 요청으로 다룬다
		if (!customerProfileRepository.existsById(customerId)) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "고객 정보를 찾을 수 없습니다.");
		}

		// sessionId 는 varchar(40). UUID 36자가 들어간다. 접두사를 붙이면 넘친다
		ConsultationSession session = new ConsultationSession(
				UUID.randomUUID().toString(),
				product.getId(),
				customerId,
				product.getProductRiskVersion());

		return SessionResponse.from(sessionRepository.save(session));
	}

	/**
	 * Revision 은 immutable 이다. 보완 설명도 전체 텍스트로 새 행을 만든다 (TRD §5.2).
	 *
	 * <p>직전 revision 과 텍스트가 완전히 같으면 새로 만들지 않고 기존 것을 돌려준다(계약 명시).
	 * 계약이 201 만 정의하므로 이때도 201 로 나간다.
	 */
	@Transactional
	public RevisionResponse createRevision(String sessionId, CreateRevisionRequest request) {
		ConsultationSession session = loadSession(sessionId);

		if (stateMachine.isClosed(session.getStatus())) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					"종료된 상담에는 내용을 추가할 수 없습니다.");
		}

		String text = request.text();
		if (text == null || text.isBlank()) {
			throw new ApiException(ErrorCode.TRANSCRIPT_EMPTY, "상담 내용을 입력해 주세요.");
		}
		if (text.length() > MAX_TRANSCRIPT_LENGTH) {
			throw new ApiException(ErrorCode.TRANSCRIPT_TOO_LONG,
					"상담 내용은 " + MAX_TRANSCRIPT_LENGTH + "자를 넘을 수 없습니다.");
		}

		Optional<ConsultationRevision> latest =
				revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(sessionId);

		if (latest.isPresent() && latest.get().getText().equals(text)) {
			return RevisionResponse.from(latest.get());
		}

		int nextRevisionNo = latest.map(r -> r.getRevisionNo() + 1).orElse(1);

		// text 를 다듬지 않고 그대로 저장한다. evidence offset 이 이 문자열 기준으로 계산되므로
		// trim 하면 나중에 서버가 재계산한 offset 이 화면과 어긋난다 (규칙 4)
		ConsultationRevision saved = revisionRepository.save(
				new ConsultationRevision(sessionId, nextRevisionNo, text));

		return RevisionResponse.from(saved);
	}

	/**
	 * PRD §13 새로고침 대응. 현재 단계를 다시 그리는 데 필요한 것을 한 번에 준다.
	 *
	 * <p><b>LLM 을 부르지 않는다.</b> 전부 저장된 값을 읽어 조립할 뿐이므로, 새로고침을 몇 번
	 * 하든 요금이 들지 않고 판정도 바뀌지 않는다.
	 *
	 * <p>Coverage 와 Understanding 은 각 모듈의 조회 서비스가 만든다. 여기서 직접 조립하면
	 * Gate 재판정과 질문 대상 규칙이 이 클래스에도 생겨, 분석 직후 응답과 새로고침 응답이
	 * 서로 다른 코드로 만들어진다.
	 */
	@Transactional(readOnly = true)
	public SessionSnapshotResponse getSnapshot(String sessionId) {
		ConsultationSession session = loadSession(sessionId);

		RevisionResponse currentRevision = revisionRepository
				.findTopBySessionIdOrderByRevisionNoDesc(sessionId)
				.map(RevisionResponse::from)
				.orElse(null);

		List<RiskUnderstandingState> understanding = understandingQueryService.statesOf(session);

		return new SessionSnapshotResponse(
				session.getId(),
				session.getProductId(),
				session.getCustomerId(),
				session.getProductRiskVersion(),
				session.getStatus(),
				session.getCreatedAt(),
				session.getClosedAt(),
				resumePointOf(session.getStatus()),
				nextActionOf(session, understanding),
				currentRevision,
				coverageQueryService.latestFor(session).orElse(null),
				understanding);
	}

	/**
	 * 계약: "Understanding 단계 진행 중이면 현재 시점의 분기값. Coverage 단계이거나 세션 종료
	 * 후에는 null이며, 이때는 resumePoint를 쓴다."
	 *
	 * <p>종료된 세션에서 null 로 두는 것이 중요하다 — 값이 남아 있으면 프론트가 끝난 상담에서
	 * 다음 행동 버튼을 그린다.
	 */
	private NextAction nextActionOf(ConsultationSession session,
	                                List<RiskUnderstandingState> understanding) {
		if (stateMachine.isClosed(session.getStatus())) {
			return null;
		}
		return understandingQueryService.resumeActionOf(understanding);
	}

	private ConsultationSession loadSession(String sessionId) {
		return sessionRepository.findById(sessionId)
				.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND,
						"상담 세션을 찾을 수 없습니다."));
	}

	private String require(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, field + " 값이 필요합니다.");
		}
		return value;
	}

	/**
	 * SessionStatus → 복귀 화면.
	 *
	 * <p><b>TRD에 규정이 없다.</b> §6.6은 Understanding 단계의 nextAction → 화면만 정한다.
	 * 아래는 프론트 화면 정의(S01 상품·고객 선택 / S02 상담 입력 / S03 Coverage·Gate /
	 * S04~S06 이해확인·재설명 / S07 직원 처리 / S08 리포트)를 전제로 한 매핑이며,
	 * 프론트와 대조해 확정해야 한다.
	 */
	private ResumePoint resumePointOf(SessionStatus status) {
		return switch (status) {
			case DRAFT -> ResumePoint.S02;
			case COVERAGE_ANALYZED, GATE_BLOCKED -> ResumePoint.S03;
			case UNDERSTANDING_IN_PROGRESS -> ResumePoint.S04;
			case AWAITING_STAFF_REVIEW -> ResumePoint.S07;
			case SESSION_CLOSED_BY_STAFF, SESSION_CLOSED_WITH_UNRESOLVED -> ResumePoint.S08;
		};
	}
}
