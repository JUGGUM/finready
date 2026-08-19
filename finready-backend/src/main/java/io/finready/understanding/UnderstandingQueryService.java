package io.finready.understanding;

import io.finready.product.ProductRisk;
import io.finready.product.ProductRiskRepository;
import io.finready.session.ConsultationSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 저장된 이해확인 이력을 읽어 {@code GET /sessions/{id}} 의 {@code understanding} 을 만든다.
 * <b>LLM 을 부르지 않고, 아무것도 쓰지 않는다.</b>
 *
 * <p>이 서비스가 없으면 새로고침이 데모를 되돌린다 — 재설명까지 진행해도 화면이 복구되지
 * 않는다. 계약이 {@code pendingQuestion} 을 "새로고침 복구의 근거"로 규정한 이유다.
 */
@Service
public class UnderstandingQueryService {

	private final ProductRiskRepository productRiskRepository;
	private final SessionQuestionRepository questionRepository;
	private final UnderstandingResultRepository resultRepository;
	private final RiskWorkflowStateRepository workflowStateRepository;
	private final StaffResolutionRepository staffResolutionRepository;
	private final NextActionResolver nextActionResolver;

	public UnderstandingQueryService(ProductRiskRepository productRiskRepository,
	                                 SessionQuestionRepository questionRepository,
	                                 UnderstandingResultRepository resultRepository,
	                                 RiskWorkflowStateRepository workflowStateRepository,
	                                 StaffResolutionRepository staffResolutionRepository,
	                                 NextActionResolver nextActionResolver) {
		this.productRiskRepository = productRiskRepository;
		this.questionRepository = questionRepository;
		this.resultRepository = resultRepository;
		this.workflowStateRepository = workflowStateRepository;
		this.staffResolutionRepository = staffResolutionRepository;
		this.nextActionResolver = nextActionResolver;
	}

	/**
	 * Risk 별 이해확인 상태. 질문이 아직 발급되지 않았으면 빈 목록이다.
	 *
	 * <p>기준은 {@code risk_workflow_state} 다 — 질문 대상 판정(Override 반영)을 이미 거친
	 * 결과이므로, 여기서 대상 규칙을 다시 계산하면 두 곳이 어긋날 수 있다.
	 */
	public List<RiskUnderstandingState> statesOf(ConsultationSession session) {
		List<RiskWorkflowState> workflowStates =
				workflowStateRepository.findBySessionIdOrderByRiskIdAsc(session.getId());
		if (workflowStates.isEmpty()) {
			return List.of();
		}

		Map<String, ProductRisk> risks = productRiskRepository
				.findByProductIdOrderByRiskIdAsc(session.getProductId()).stream()
				.collect(Collectors.toMap(ProductRisk::getRiskId, Function.identity(),
						(first, second) -> first, LinkedHashMap::new));

		Map<String, List<SessionQuestion>> questionsByRisk = questionRepository
				.findBySessionIdOrderByRiskIdAscAttemptAsc(session.getId()).stream()
				.collect(Collectors.groupingBy(SessionQuestion::getRiskId,
						LinkedHashMap::new, Collectors.toList()));

		Map<String, List<UnderstandingResult>> resultsByRisk = resultRepository
				.findBySessionIdOrderByRiskIdAscAttemptAsc(session.getId()).stream()
				.collect(Collectors.groupingBy(UnderstandingResult::getRiskId,
						LinkedHashMap::new, Collectors.toList()));

		Map<String, StaffResolution> resolutionsByRisk = staffResolutionRepository
				.findBySessionIdOrderByRiskIdAsc(session.getId()).stream()
				.collect(Collectors.toMap(StaffResolution::getRiskId, Function.identity(),
						(first, second) -> first, LinkedHashMap::new));

		List<RiskUnderstandingState> states = new ArrayList<>(workflowStates.size());
		for (RiskWorkflowState workflowState : workflowStates) {
			String riskId = workflowState.getRiskId();
			List<UnderstandingResult> answered =
					resultsByRisk.getOrDefault(riskId, List.of());
			ProductRisk risk = risks.get(riskId);

			states.add(new RiskUnderstandingState(
					riskId,
					risk == null ? null : risk.getTitle(),
					answered.stream().map(RiskUnderstandingState.AttemptView::from).toList(),
					RiskUnderstandingState.StaffResolutionView.from(resolutionsByRisk.get(riskId)),
					workflowState.getWorkflowStatus(),
					workflowState.getFinalDisposition(),
					RiskUnderstandingState.PendingQuestionView.from(
							pendingQuestion(questionsByRisk.getOrDefault(riskId, List.of()), answered))));
		}
		return states;
	}

	/**
	 * 새로고침 복구용 {@code nextAction}. 이해확인이 시작되지 않았으면 null 이다 —
	 * 계약이 "Coverage 단계이거나 세션 종료 후에는 null이며, 이때는 resumePoint를 쓴다"고 정한다.
	 */
	public NextAction resumeActionOf(List<RiskUnderstandingState> states) {
		if (states.isEmpty()) {
			return null;
		}
		return nextActionResolver.resume(states.stream()
				.map(state -> new NextActionResolver.ResumeState(
						state.workflowStatus(),
						state.pendingQuestion() == null ? null : state.pendingQuestion().attempt(),
						lastAiStatusOf(state)))
				.toList());
	}

	// ------------------------------------------------------------------

	/**
	 * 발급됐지만 답변이 없는 질문. 답변이 저장되면 null 이 된다(계약).
	 *
	 * <p>attempt 로 대조한다 — 질문과 답변이 (session, risk, attempt) 로 1:1 이기 때문이다.
	 * 개수만 세면 attempt 2 질문만 있고 attempt 1 답변이 있는 상태를 "답변 완료"로 오판한다.
	 */
	private SessionQuestion pendingQuestion(List<SessionQuestion> questions,
	                                        List<UnderstandingResult> answered) {
		java.util.Set<Integer> answeredAttempts = answered.stream()
				.map(UnderstandingResult::getAttempt)
				.map(Integer::valueOf)
				.collect(Collectors.toSet());

		SessionQuestion pending = null;
		for (SessionQuestion question : questions) {
			if (!answeredAttempts.contains((int) question.getAttempt())
					&& (pending == null || question.getAttempt() > pending.getAttempt())) {
				pending = question;
			}
		}
		return pending;
	}

	private UnderstandingStatus lastAiStatusOf(RiskUnderstandingState state) {
		return state.attempts().isEmpty()
				? null
				: state.attempts().getLast().aiStatus();
	}
}
