package io.finready.understanding;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.common.StateMachine;
import io.finready.session.ConsultationSession;
import io.finready.session.ConsultationSessionRepository;
import io.finready.session.SessionStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이해확인 쓰기의 트랜잭션 경계. {@code CoverageWriter} 와 같은 이유로 별도 빈이다 —
 * LLM 호출이 트랜잭션 안에 있으면 안 되고(규칙 6), 같은 클래스의 {@code @Transactional}
 * 메서드를 자기 호출하면 프록시를 타지 않아 트랜잭션이 아예 안 걸린다.
 */
@Component
class UnderstandingWriter {

	private final ConsultationSessionRepository sessionRepository;
	private final SessionQuestionRepository questionRepository;
	private final UnderstandingResultRepository resultRepository;
	private final RiskWorkflowStateRepository workflowStateRepository;
	private final StaffResolutionRepository staffResolutionRepository;
	private final StateMachine stateMachine;

	UnderstandingWriter(ConsultationSessionRepository sessionRepository,
	                    SessionQuestionRepository questionRepository,
	                    UnderstandingResultRepository resultRepository,
	                    RiskWorkflowStateRepository workflowStateRepository,
	                    StaffResolutionRepository staffResolutionRepository,
	                    StateMachine stateMachine) {
		this.sessionRepository = sessionRepository;
		this.questionRepository = questionRepository;
		this.resultRepository = resultRepository;
		this.workflowStateRepository = workflowStateRepository;
		this.staffResolutionRepository = staffResolutionRepository;
		this.stateMachine = stateMachine;
	}

	/** F04 — 질문 발급 + workflow 상태 초기화 + 세션을 이해확인 단계로 */
	@Transactional
	void saveIssuedQuestions(String sessionId,
	                         List<SessionQuestion> questions,
	                         List<RiskWorkflowState> initialStates) {
		questionRepository.saveAll(questions);
		workflowStateRepository.saveAll(initialStates);
		moveTo(sessionId, SessionStatus.UNDERSTANDING_IN_PROGRESS);
	}

	/**
	 * F05/F07 — 판정 저장 + workflow 전이 (+ 후속 질문 발급).
	 *
	 * <p>{@code workflowState} 는 호출부가 이미 전이시켜 넘긴 <b>영속 인스턴스</b>다.
	 * 여기서 다시 읽어 전이시키면 전이가 두 곳에서 일어나 일원화가 깨진다.
	 *
	 * @param recheckQuestion attempt 2 질문. 발급할 게 없으면 null
	 */
	@Transactional
	SessionStatus saveAnswer(String sessionId,
	                         UnderstandingResult result,
	                         RiskWorkflowState workflowState,
	                         SessionQuestion recheckQuestion,
	                         boolean understandingFinished) {
		resultRepository.save(result);
		workflowStateRepository.save(workflowState);
		if (recheckQuestion != null) {
			questionRepository.save(recheckQuestion);
		}
		return understandingFinished
				? moveTo(sessionId, SessionStatus.AWAITING_STAFF_REVIEW)
				: currentStatusOf(sessionId);
	}

	/**
	 * F06 — 재설명 응답에 실을 후속 질문만 저장한다.
	 *
	 * <p>워크플로 전이가 없다. 재설명 시점의 Risk 는 이미 IN_PROGRESS 이며,
	 * 다음 전이는 attempt 2 답변({@code /recheck})에서 일어난다.
	 */
	@Transactional
	void saveRecheckQuestion(SessionQuestion recheckQuestion) {
		questionRepository.save(recheckQuestion);
	}

	/**
	 * F07 — 직원 처리 저장 + workflow 를 COMPLETE 로.
	 *
	 * <p>{@code understanding_result} 를 건드리지 않는다(규칙 1). 여기서 UPDATE 가 한 줄이라도
	 * 생기면 AI 원판정이 사라진다.
	 */
	@Transactional
	SessionStatus saveStaffResolution(String sessionId,
	                                  StaffResolution resolution,
	                                  RiskWorkflowState workflowState,
	                                  boolean understandingFinished) {
		staffResolutionRepository.save(resolution);
		workflowStateRepository.save(workflowState);
		return understandingFinished
				? moveTo(sessionId, SessionStatus.AWAITING_STAFF_REVIEW)
				: currentStatusOf(sessionId);
	}

	private SessionStatus moveTo(String sessionId, SessionStatus target) {
		ConsultationSession session = load(sessionId);
		if (session.getStatus() != target) {
			session.transitionTo(target, stateMachine);
		}
		return session.getStatus();
	}

	private SessionStatus currentStatusOf(String sessionId) {
		return load(sessionId).getStatus();
	}

	private ConsultationSession load(String sessionId) {
		return sessionRepository.findById(sessionId)
				.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND,
						"상담 세션을 찾을 수 없습니다."));
	}
}
