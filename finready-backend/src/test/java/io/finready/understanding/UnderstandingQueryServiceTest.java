package io.finready.understanding;

import io.finready.common.GenerationSource;
import io.finready.product.CoveragePolicy;
import io.finready.product.ProductRisk;
import io.finready.product.ProductRiskRepository;
import io.finready.session.ConsultationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 새로고침 복구의 핵심은 {@code pendingQuestion} 이다 — "발급됐지만 아직 답이 없는 질문".
 * 이 값이 틀리면 화면이 엉뚱한 단계로 복구되거나, 이미 답한 질문을 다시 묻는다.
 */
@DisplayName("UnderstandingQueryService — 새로고침 복구")
class UnderstandingQueryServiceTest {

	private static final String SESSION_ID = "S-1";

	private final ProductRiskRepository productRiskRepository = mock(ProductRiskRepository.class);
	private final SessionQuestionRepository questionRepository = mock(SessionQuestionRepository.class);
	private final UnderstandingResultRepository resultRepository = mock(UnderstandingResultRepository.class);
	private final RiskWorkflowStateRepository workflowStateRepository = mock(RiskWorkflowStateRepository.class);
	private final StaffResolutionRepository staffResolutionRepository = mock(StaffResolutionRepository.class);

	private final WorkflowStateMachine workflowStateMachine = new WorkflowStateMachine();

	private UnderstandingQueryService service;
	private ConsultationSession session;

	@BeforeEach
	void setUp() {
		service = new UnderstandingQueryService(productRiskRepository, questionRepository,
				resultRepository, workflowStateRepository, staffResolutionRepository,
				new NextActionResolver());

		session = new ConsultationSession(SESSION_ID, "PROD_A", "CUST_A", "A-2026-08-12-01");

		when(productRiskRepository.findByProductIdOrderByRiskIdAsc("PROD_A"))
				.thenReturn(List.of(risk("R01")));
		when(questionRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
				.thenReturn(List.of());
		when(resultRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
				.thenReturn(List.of());
		when(workflowStateRepository.findBySessionIdOrderByRiskIdAsc(SESSION_ID))
				.thenReturn(List.of(inProgress("R01")));
		when(staffResolutionRepository.findBySessionIdOrderByRiskIdAsc(SESSION_ID))
				.thenReturn(List.of());
	}

	@Test
	@DisplayName("질문 발급 전이면 빈 목록이다 — Coverage 단계 세션의 정상 상태다")
	void beforeQuestionsIsEmpty() {
		when(workflowStateRepository.findBySessionIdOrderByRiskIdAsc(SESSION_ID)).thenReturn(List.of());

		assertThat(service.statesOf(session)).isEmpty();
		assertThat(service.resumeActionOf(List.of())).isNull();
	}

	@Nested
	@DisplayName("pendingQuestion")
	class Pending {

		@Test
		@DisplayName("발급만 되고 답이 없으면 그 질문이 pending 이다")
		void unansweredQuestionIsPending() {
			when(questionRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
					.thenReturn(List.of(question("R01", (short) 1, "첫 질문")));

			RiskUnderstandingState state = service.statesOf(session).getFirst();

			assertThat(state.pendingQuestion()).isNotNull();
			assertThat(state.pendingQuestion().attempt()).isEqualTo(1);
			assertThat(state.pendingQuestion().question()).isEqualTo("첫 질문");
		}

		@Test
		@DisplayName("답이 저장되면 null 이 된다 (계약)")
		void answeredQuestionIsNotPending() {
			when(questionRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
					.thenReturn(List.of(question("R01", (short) 1, "첫 질문")));
			when(resultRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
					.thenReturn(List.of(result("R01", (short) 1, UnderstandingStatus.MISUNDERSTOOD)));

			assertThat(service.statesOf(session).getFirst().pendingQuestion()).isNull();
		}

		@Test
		@DisplayName("attempt 1 은 답했고 2가 남았으면 2가 pending 이다")
		void secondAttemptIsPending() {
			// 개수만 세면 "질문 2 답변 1" 을 보고 답변 완료로 오판한다. attempt 로 대조해야 한다
			when(questionRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
					.thenReturn(List.of(
							question("R01", (short) 1, "첫 질문"),
							question("R01", (short) 2, "후속 질문")));
			when(resultRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
					.thenReturn(List.of(result("R01", (short) 1, UnderstandingStatus.MISUNDERSTOOD)));

			RiskUnderstandingState state = service.statesOf(session).getFirst();

			assertThat(state.pendingQuestion().attempt()).isEqualTo(2);
			assertThat(state.pendingQuestion().question()).isEqualTo("후속 질문");
			assertThat(service.resumeActionOf(List.of(state))).isEqualTo(NextAction.RECHECK);
		}

		@Test
		@DisplayName("재설명 전이면 후속 질문이 없어 REEXPLAIN 으로 복구된다")
		void misunderstoodBeforeReexplainResumesToReexplain() {
			when(questionRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
					.thenReturn(List.of(question("R01", (short) 1, "첫 질문")));
			when(resultRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
					.thenReturn(List.of(result("R01", (short) 1, UnderstandingStatus.MISUNDERSTOOD)));

			List<RiskUnderstandingState> states = service.statesOf(session);

			assertThat(states.getFirst().pendingQuestion()).isNull();
			assertThat(service.resumeActionOf(states)).isEqualTo(NextAction.REEXPLAIN);
		}
	}

	@Test
	@DisplayName("직원 처리가 있어도 AI 판정은 attempt 별로 그대로 실린다 (규칙 1)")
	void staffResolutionDoesNotHideAiStatus() {
		when(questionRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
				.thenReturn(List.of(question("R01", (short) 1, "첫 질문")));
		when(resultRepository.findBySessionIdOrderByRiskIdAscAttemptAsc(SESSION_ID))
				.thenReturn(List.of(result("R01", (short) 1, UnderstandingStatus.MISUNDERSTOOD)));
		when(staffResolutionRepository.findBySessionIdOrderByRiskIdAsc(SESSION_ID))
				.thenReturn(List.of(new StaffResolution(SESSION_ID, "R01",
						StaffDisposition.RESOLVED_BY_STAFF, "구두로 다시 설명함", "staff-1")));

		RiskUnderstandingState state = service.statesOf(session).getFirst();

		assertThat(state.attempts()).hasSize(1);
		assertThat(state.attempts().getFirst().aiStatus()).isEqualTo(UnderstandingStatus.MISUNDERSTOOD);
		assertThat(state.staffResolution().disposition()).isEqualTo(StaffDisposition.RESOLVED_BY_STAFF);
	}

	// ------------------------------------------------------------------

	private ProductRisk risk(String riskId) {
		return new ProductRisk("PROD_A", riskId, "카테고리", riskId + " 제목", riskId + " 사실",
				CoveragePolicy.GATE_REQUIRED, true, 11, "출처 문장",
				"질문", "후속 질문", "쉬운 설명",
				OffsetDateTime.parse("2026-08-12T00:00:00Z"), "TEAM");
	}

	private RiskWorkflowState inProgress(String riskId) {
		RiskWorkflowState state = new RiskWorkflowState(SESSION_ID, riskId);
		state.transitionTo(WorkflowStatus.IN_PROGRESS, null, workflowStateMachine);
		return state;
	}

	private SessionQuestion question(String riskId, short attempt, String text) {
		return new SessionQuestion(SESSION_ID, riskId, attempt, text, GenerationSource.LLM);
	}

	private UnderstandingResult result(String riskId, short attempt, UnderstandingStatus status) {
		return new UnderstandingResult(SESSION_ID, riskId, attempt,
				"질문", GenerationSource.LLM, "고객 답변", AnswerSource.CUSTOMER_DIRECT_DEMO,
				status, status == UnderstandingStatus.UNDERSTOOD ? null : "사유");
	}
}
