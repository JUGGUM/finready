package io.finready.session;

import io.finready.audit.AuditEventType;
import io.finready.audit.AuditRecorder;
import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.common.StateMachine;
import io.finready.coverage.CoverageQueryService;
import io.finready.coverage.CoverageResponse;
import io.finready.coverage.GateStatus;
import io.finready.product.CustomerProfileRepository;
import io.finready.product.Product;
import io.finready.product.ProductRepository;
import io.finready.understanding.FinalDisposition;
import io.finready.understanding.RiskUnderstandingState;
import io.finready.understanding.UnderstandingQueryService;
import io.finready.understanding.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수동 curl 시나리오의 로직 부분. 리포지토리는 대역이다.
 *
 * <p>DB 제약(uq_revision, ck_char_count 등)은 여기서 검증되지 않는다 —
 * F03 에서 Testcontainers 로 붙인다.
 */
class SessionServiceTest {

	private static final String SESSION_ID = "11111111-2222-3333-4444-555555555555";

	private ConsultationSessionRepository sessionRepository;
	private ConsultationRevisionRepository revisionRepository;
	private ProductRepository productRepository;
	private CustomerProfileRepository customerProfileRepository;
	private CoverageQueryService coverageQueryService;
	private UnderstandingQueryService understandingQueryService;
	private AuditRecorder auditRecorder;
	private SessionService sessionService;

	@BeforeEach
	void setUp() {
		sessionRepository = mock(ConsultationSessionRepository.class);
		revisionRepository = mock(ConsultationRevisionRepository.class);
		productRepository = mock(ProductRepository.class);
		customerProfileRepository = mock(CustomerProfileRepository.class);
		coverageQueryService = mock(CoverageQueryService.class);
		understandingQueryService = mock(UnderstandingQueryService.class);
		auditRecorder = mock(AuditRecorder.class);
		sessionService = new SessionService(sessionRepository, revisionRepository,
				productRepository, customerProfileRepository,
				coverageQueryService, understandingQueryService,
				// 종료 조건 판정은 대역이 아니라 진짜를 쓴다 — 이 클래스가 검증하려는 규칙이
				// 그 안에 있고, 대역으로 두면 "무엇을 물어봤나"만 확인하게 된다
				new CloseEligibilityEvaluator(new StateMachine()),
				auditRecorder, new StateMachine());
	}

	private Product product() {
		return new Product("PROD_A", "테스트 상품", "NO_KNOCK_IN_STEP_DOWN", "A-2026-08-12-01",
				"DOC_PROD_A_V1", "/documents/PROD_A/v1.0.pdf", 15,
				"5d355381abe028eb492f3c277236ee35a774150f4dbb24c289d2612ca8c5c47e", null, true);
	}

	private ConsultationSession draftSession() {
		return new ConsultationSession(SESSION_ID, "PROD_A", "CUST_A", "A-2026-08-12-01");
	}

	// ------------------------------------------------------------------
	// POST /api/sessions
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("세션 생성")
	class CreateSession {

		@Test
		@DisplayName("생성 시점의 productRiskVersion 을 snapshot 으로 고정한다")
		void snapshotsProductRiskVersion() {
			when(productRepository.findById("PROD_A")).thenReturn(Optional.of(product()));
			when(customerProfileRepository.existsById("CUST_A")).thenReturn(true);
			when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			SessionResponse response = sessionService.createSession(
					new CreateSessionRequest("PROD_A", "CUST_A"));

			assertThat(response.productRiskVersion()).isEqualTo("A-2026-08-12-01");
			assertThat(response.sessionStatus()).isEqualTo(SessionStatus.DRAFT);
			assertThat(response.closedAt()).isNull();
		}

		@Test
		@DisplayName("sessionId 는 varchar(40) 에 들어가는 길이여야 한다")
		void sessionIdFitsColumn() {
			when(productRepository.findById("PROD_A")).thenReturn(Optional.of(product()));
			when(customerProfileRepository.existsById("CUST_A")).thenReturn(true);
			when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			SessionResponse response = sessionService.createSession(
					new CreateSessionRequest("PROD_A", "CUST_A"));

			assertThat(response.sessionId()).hasSizeLessThanOrEqualTo(40);
		}

		@Test
		@DisplayName("없는 productId 는 PRODUCT_NOT_FOUND(404)")
		void unknownProduct() {
			when(productRepository.findById("NOPE")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> sessionService.createSession(
					new CreateSessionRequest("NOPE", "CUST_A")))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}

		/** 계약 ErrorCode 에 고객용 404 가 없어 400 으로 다룬다 */
		@Test
		@DisplayName("없는 customerId 는 INVALID_REQUEST(400)")
		void unknownCustomer() {
			when(productRepository.findById("PROD_A")).thenReturn(Optional.of(product()));
			when(customerProfileRepository.existsById("CUST_X")).thenReturn(false);

			assertThatThrownBy(() -> sessionService.createSession(
					new CreateSessionRequest("PROD_A", "CUST_X")))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.INVALID_REQUEST);
		}

		@Test
		@DisplayName("productId 가 비면 INVALID_REQUEST, DB 를 조회하지 않는다")
		void blankProductIdShortCircuits() {
			assertThatThrownBy(() -> sessionService.createSession(
					new CreateSessionRequest("  ", "CUST_A")))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.INVALID_REQUEST);

			verify(productRepository, never()).findById(anyString());
		}
	}

	// ------------------------------------------------------------------
	// POST /api/sessions/{id}/revisions
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Revision 저장")
	class CreateRevision {

		@Test
		@DisplayName("첫 revision 은 번호가 1이다")
		void firstRevisionIsNumberOne() {
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.empty());
			when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			RevisionResponse response = sessionService.createRevision(
					SESSION_ID, new CreateRevisionRequest("첫 상담 내용"));

			assertThat(response.revision()).isEqualTo(1);
			assertThat(response.charCount()).isEqualTo("첫 상담 내용".length());
		}

		@Test
		@DisplayName("다른 텍스트면 번호가 직전 +1 이다")
		void nextRevisionIncrements() {
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.of(new ConsultationRevision(SESSION_ID, 3, "이전 내용")));
			when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			RevisionResponse response = sessionService.createRevision(
					SESSION_ID, new CreateRevisionRequest("새 내용"));

			assertThat(response.revision()).isEqualTo(4);
		}

		/** TRD §5.2 — 계약이 "직전과 완전히 동일하면 새로 만들지 않는다"고 규정한다 */
		@Test
		@DisplayName("직전과 텍스트가 같으면 저장하지 않고 기존 것을 반환한다")
		void identicalTextReturnsExistingWithoutSaving() {
			String text = "같은 상담 내용";
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.of(new ConsultationRevision(SESSION_ID, 2, text)));

			RevisionResponse response = sessionService.createRevision(
					SESSION_ID, new CreateRevisionRequest(text));

			assertThat(response.revision()).isEqualTo(2);
			verify(revisionRepository, never()).save(any());
		}

		/**
		 * evidence offset 이 저장된 문자열 기준으로 계산되므로(규칙 4)
		 * 앞뒤 공백을 다듬으면 나중에 서버가 재계산한 offset 이 화면과 어긋난다.
		 */
		@Test
		@DisplayName("텍스트를 다듬지 않고 그대로 저장한다")
		void storesTextVerbatim() {
			String text = "  앞뒤 공백이 있는 내용  ";
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.empty());
			when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			sessionService.createRevision(SESSION_ID, new CreateRevisionRequest(text));

			ArgumentCaptor<ConsultationRevision> captor =
					ArgumentCaptor.forClass(ConsultationRevision.class);
			verify(revisionRepository).save(captor.capture());
			assertThat(captor.getValue().getText()).isEqualTo(text);
			assertThat(captor.getValue().getCharCount()).isEqualTo(text.length());
		}

		@ParameterizedTest
		@CsvSource(value = {"''", "'   '", "'\n\t'"})
		@DisplayName("빈 입력은 TRANSCRIPT_EMPTY(400)")
		void blankTextRejected(String text) {
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));

			assertThatThrownBy(() -> sessionService.createRevision(
					SESSION_ID, new CreateRevisionRequest(text)))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.TRANSCRIPT_EMPTY);
		}

		@Test
		@DisplayName("8000자는 통과하고 8001자는 TRANSCRIPT_TOO_LONG(400)")
		void lengthBoundary() {
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.empty());
			when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			assertThat(sessionService.createRevision(SESSION_ID,
					new CreateRevisionRequest("가".repeat(8000))).charCount()).isEqualTo(8000);

			assertThatThrownBy(() -> sessionService.createRevision(
					SESSION_ID, new CreateRevisionRequest("가".repeat(8001))))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.TRANSCRIPT_TOO_LONG);
		}

		@Test
		@DisplayName("없는 세션은 SESSION_NOT_FOUND(404)")
		void unknownSession() {
			when(sessionRepository.findById("nope")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> sessionService.createRevision(
					"nope", new CreateRevisionRequest("내용")))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.SESSION_NOT_FOUND);
		}

		@Test
		@DisplayName("종료된 세션은 INVALID_STATE_TRANSITION(409)")
		void closedSessionRejected() {
			ConsultationSession session = draftSession();
			StateMachine stateMachine = new StateMachine();
			session.transitionTo(SessionStatus.COVERAGE_ANALYZED, stateMachine);
			session.transitionTo(SessionStatus.UNDERSTANDING_IN_PROGRESS, stateMachine);
			session.transitionTo(SessionStatus.AWAITING_STAFF_REVIEW, stateMachine);
			session.transitionTo(SessionStatus.SESSION_CLOSED_BY_STAFF, stateMachine);

			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

			assertThatThrownBy(() -> sessionService.createRevision(
					SESSION_ID, new CreateRevisionRequest("종료 후 추가 시도")))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

			verify(revisionRepository, never()).save(any());
		}

		/** 종료 여부를 빈 입력보다 먼저 본다 — 종료된 세션에 빈 값을 보내도 409 여야 한다 */
		@Test
		@DisplayName("종료 검사가 입력 검증보다 먼저다")
		void closedCheckPrecedesValidation() {
			ConsultationSession session = draftSession();
			StateMachine stateMachine = new StateMachine();
			session.transitionTo(SessionStatus.COVERAGE_ANALYZED, stateMachine);
			session.transitionTo(SessionStatus.UNDERSTANDING_IN_PROGRESS, stateMachine);
			session.transitionTo(SessionStatus.AWAITING_STAFF_REVIEW, stateMachine);
			session.transitionTo(SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED, stateMachine);

			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

			assertThatThrownBy(() -> sessionService.createRevision(
					SESSION_ID, new CreateRevisionRequest("")))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
		}
	}

	// ------------------------------------------------------------------
	// GET /api/sessions/{id}
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("스냅샷 조회")
	class Snapshot {

		@Test
		@DisplayName("아직 없는 단계는 null / 빈 배열이다 (계약이 허용)")
		void notYetBuiltStagesAreNull() {
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.empty());

			SessionSnapshotResponse snapshot = sessionService.getSnapshot(SESSION_ID);

			assertThat(snapshot.coverage()).isNull();
			assertThat(snapshot.nextAction()).isNull();
			assertThat(snapshot.understanding()).isEmpty();
			assertThat(snapshot.currentRevision()).isNull();
		}

		@Test
		@DisplayName("currentRevision 은 가장 최근 것이다")
		void currentRevisionIsLatest() {
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(draftSession()));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.of(new ConsultationRevision(SESSION_ID, 5, "최근 내용")));

			SessionSnapshotResponse snapshot = sessionService.getSnapshot(SESSION_ID);

			assertThat(snapshot.currentRevision().revision()).isEqualTo(5);
			assertThat(snapshot.currentRevision().text()).isEqualTo("최근 내용");
		}

		/**
		 * TRD 에 규정이 없는 매핑이다. 프론트 화면 정의와 대조해 확정해야 한다.
		 * 값이 바뀌면 이 테스트가 먼저 깨지도록 고정해둔다.
		 */
		@ParameterizedTest
		@CsvSource({
				"DRAFT, S02",
				"COVERAGE_ANALYZED, S03",
				"GATE_BLOCKED, S03",
				"UNDERSTANDING_IN_PROGRESS, S04",
				"AWAITING_STAFF_REVIEW, S07",
				"SESSION_CLOSED_BY_STAFF, S08",
				"SESSION_CLOSED_WITH_UNRESOLVED, S08"
		})
		@DisplayName("SessionStatus 별 resumePoint 매핑")
		void resumePointMapping(SessionStatus status, ResumePoint expected) {
			ConsultationSession session = sessionInStatus(status);
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.empty());

			assertThat(sessionService.getSnapshot(SESSION_ID).resumePoint()).isEqualTo(expected);
		}

		@Test
		@DisplayName("없는 세션은 SESSION_NOT_FOUND(404)")
		void unknownSession() {
			when(sessionRepository.findById("nope")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> sessionService.getSnapshot("nope"))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.SESSION_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------
	// POST /api/sessions/{id}/close  (F08)
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("세션 종료")
	class CloseSession {

		private void awaitingReview() {
			when(sessionRepository.findById(SESSION_ID))
					.thenReturn(Optional.of(sessionInStatus(SessionStatus.AWAITING_STAFF_REVIEW)));
		}

		@Test
		@DisplayName("미해결이 없으면 SESSION_CLOSED_BY_STAFF 로 닫고 종료자를 남긴다")
		void closesByStaff() {
			awaitingReview();

			SessionResponse response = sessionService.closeSession(
					SESSION_ID, new CloseSessionRequest("staff-001", null, null));

			assertThat(response.sessionStatus()).isEqualTo(SessionStatus.SESSION_CLOSED_BY_STAFF);
			assertThat(response.closedAt()).isNotNull();
		}

		@Test
		@DisplayName("미해결이 있는데 사유가 없으면 UNRESOLVED_REASON_REQUIRED")
		void unresolvedWithoutReasonFails() {
			awaitingReview();
			when(understandingQueryService.statesOf(any())).thenReturn(List.of(
					new RiskUnderstandingState("R01", "제목", List.of(), null,
							WorkflowStatus.MANUAL_REVIEW_REQUIRED, null, null)));

			assertThatThrownBy(() -> sessionService.closeSession(
					SESSION_ID, new CloseSessionRequest("staff-001", "  ", null)))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.UNRESOLVED_REASON_REQUIRED);
		}

		@Test
		@DisplayName("사유가 있으면 SESSION_CLOSED_WITH_UNRESOLVED 로 닫힌다")
		void closesWithUnresolved() {
			ConsultationSession session = sessionInStatus(SessionStatus.AWAITING_STAFF_REVIEW);
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
			when(understandingQueryService.statesOf(any())).thenReturn(List.of(
					new RiskUnderstandingState("R01", "제목", List.of(), null,
							WorkflowStatus.COMPLETE, FinalDisposition.UNRESOLVED, null)));

			SessionResponse response = sessionService.closeSession(SESSION_ID,
					new CloseSessionRequest("staff-001", "고객이 재방문 예정", null));

			assertThat(response.sessionStatus())
					.isEqualTo(SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED);
			assertThat(session.getUnresolvedReason()).isEqualTo("고객이 재방문 예정");
			assertThat(session.getClosedBy()).isEqualTo("staff-001");
		}

		/**
		 * 정상 종료인데 사유가 남아 있으면 리포트에서 "무언가 미해결이었다"로 읽힌다.
		 * 프론트가 입력값을 지우지 않고 보내는 경우가 있어 서버에서 잘라낸다.
		 */
		@Test
		@DisplayName("미해결이 없으면 사유를 보내도 저장하지 않는다")
		void dropsReasonWhenNothingUnresolved() {
			ConsultationSession session = sessionInStatus(SessionStatus.AWAITING_STAFF_REVIEW);
			when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

			sessionService.closeSession(SESSION_ID,
					new CloseSessionRequest("staff-001", "화면에 남아 있던 값", null));

			assertThat(session.getUnresolvedReason()).isNull();
		}

		@Test
		@DisplayName("WARN_ONLY 확인이 빠지면 WARNING_ACKNOWLEDGEMENT_REQUIRED 이고 riskId 를 실어 준다")
		void missingAcknowledgementFails() {
			awaitingReview();
			when(coverageQueryService.latestFor(any())).thenReturn(Optional.of(coverageWithWarnings()));

			assertThatThrownBy(() -> sessionService.closeSession(SESSION_ID,
					new CloseSessionRequest("staff-001", null, List.of("R05"))))
					.isInstanceOf(ApiException.class)
					.satisfies(ex -> {
						assertThat(((ApiException) ex).code())
								.isEqualTo(ErrorCode.WARNING_ACKNOWLEDGEMENT_REQUIRED);
						assertThat(((ApiException) ex).riskId()).isEqualTo("R07");
					});
		}

		/** 개수만 세면 다른 Risk 를 같은 개수만큼 보내도 통과한다 */
		@Test
		@DisplayName("확인 목록은 개수가 아니라 riskId 로 대조한다")
		void acknowledgementIsMatchedByRiskId() {
			awaitingReview();
			when(coverageQueryService.latestFor(any())).thenReturn(Optional.of(coverageWithWarnings()));

			assertThatThrownBy(() -> sessionService.closeSession(SESSION_ID,
					new CloseSessionRequest("staff-001", null, List.of("R01", "R02"))))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.WARNING_ACKNOWLEDGEMENT_REQUIRED);
		}

		@Test
		@DisplayName("모두 확인했으면 종료된다")
		void closesWhenAllWarningsAcknowledged() {
			awaitingReview();
			when(coverageQueryService.latestFor(any())).thenReturn(Optional.of(coverageWithWarnings()));

			SessionResponse response = sessionService.closeSession(SESSION_ID,
					new CloseSessionRequest("staff-001", null, List.of("R05", "R07")));

			assertThat(response.sessionStatus()).isEqualTo(SessionStatus.SESSION_CLOSED_BY_STAFF);
		}

		@Test
		@DisplayName("이해 확인 전에는 종료할 수 없다")
		void cannotCloseBeforeReview() {
			when(sessionRepository.findById(SESSION_ID))
					.thenReturn(Optional.of(sessionInStatus(SessionStatus.UNDERSTANDING_IN_PROGRESS)));

			assertThatThrownBy(() -> sessionService.closeSession(
					SESSION_ID, new CloseSessionRequest("staff-001", null, null)))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
		}

		/** 종료 버튼을 두 번 누르거나 새로고침 후 다시 누르는 것이 409 로 끝나면 안 된다 */
		@Test
		@DisplayName("이미 닫힌 세션에 재호출해도 같은 응답이다 (멱등)")
		void isIdempotent() {
			when(sessionRepository.findById(SESSION_ID))
					.thenReturn(Optional.of(sessionInStatus(SessionStatus.SESSION_CLOSED_BY_STAFF)));

			SessionResponse response = sessionService.closeSession(
					SESSION_ID, new CloseSessionRequest("staff-002", null, null));

			assertThat(response.sessionStatus()).isEqualTo(SessionStatus.SESSION_CLOSED_BY_STAFF);
			verify(auditRecorder, never()).recordStaff(anyString(), any(), anyString(), anyString());
		}

		@Test
		@DisplayName("actor 가 없으면 INVALID_REQUEST")
		void actorIsRequired() {
			when(sessionRepository.findById(SESSION_ID))
					.thenReturn(Optional.of(sessionInStatus(SessionStatus.AWAITING_STAFF_REVIEW)));

			assertThatThrownBy(() -> sessionService.closeSession(
					SESSION_ID, new CloseSessionRequest(" ", null, null)))
					.isInstanceOf(ApiException.class)
					.extracting(ex -> ((ApiException) ex).code())
					.isEqualTo(ErrorCode.INVALID_REQUEST);
		}

		/** 감사 기록이 없으면 "누가 언제 닫았는지"를 되짚을 방법이 사라진다 */
		@Test
		@DisplayName("종료를 감사 이벤트로 남긴다")
		void recordsAuditEvent() {
			awaitingReview();

			sessionService.closeSession(SESSION_ID,
					new CloseSessionRequest("staff-001", null, null));

			verify(auditRecorder).recordStaff(eq(SESSION_ID), eq(AuditEventType.SESSION_CLOSED),
					eq("staff-001"), anyString());
		}

		private CoverageResponse coverageWithWarnings() {
			return new CoverageResponse(SESSION_ID, 1L, SessionStatus.AWAITING_STAFF_REVIEW,
					GateStatus.READY_FOR_UNDERSTANDING, true,
					List.of(), List.of("R05", "R07"), List.of(), null);
		}
	}

	/** 전이표를 따라 목표 상태까지 실제로 걸어간다. 임의로 상태를 심을 방법이 없다(규칙 7) */
	private ConsultationSession sessionInStatus(SessionStatus target) {
		ConsultationSession session = draftSession();
		StateMachine sm = new StateMachine();
		switch (target) {
			case DRAFT -> { }
			case COVERAGE_ANALYZED -> session.transitionTo(SessionStatus.COVERAGE_ANALYZED, sm);
			case GATE_BLOCKED -> session.transitionTo(SessionStatus.GATE_BLOCKED, sm);
			case UNDERSTANDING_IN_PROGRESS -> {
				session.transitionTo(SessionStatus.COVERAGE_ANALYZED, sm);
				session.transitionTo(SessionStatus.UNDERSTANDING_IN_PROGRESS, sm);
			}
			case AWAITING_STAFF_REVIEW -> {
				session.transitionTo(SessionStatus.COVERAGE_ANALYZED, sm);
				session.transitionTo(SessionStatus.UNDERSTANDING_IN_PROGRESS, sm);
				session.transitionTo(SessionStatus.AWAITING_STAFF_REVIEW, sm);
			}
			case SESSION_CLOSED_BY_STAFF, SESSION_CLOSED_WITH_UNRESOLVED -> {
				session.transitionTo(SessionStatus.COVERAGE_ANALYZED, sm);
				session.transitionTo(SessionStatus.UNDERSTANDING_IN_PROGRESS, sm);
				session.transitionTo(SessionStatus.AWAITING_STAFF_REVIEW, sm);
				session.transitionTo(target, sm);
			}
		}
		return session;
	}
}
