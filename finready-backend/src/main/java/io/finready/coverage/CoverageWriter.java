package io.finready.coverage;

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
 * Coverage 쓰기의 트랜잭션 경계.
 *
 * <p><b>왜 서비스에서 분리했나.</b> {@link CoverageAnalysisService} 는 LLM 을 호출하므로
 * 트랜잭션 안에 있으면 안 된다(규칙 6 — DB role 에 {@code idle_in_transaction_session_timeout=30s}
 * 가 걸려 있어 30초짜리 커넥션 점유는 런타임에 터진다). 그렇다고 서비스 안에 {@code @Transactional}
 * 메서드를 두고 자기 자신을 호출하면 <b>프록시를 타지 않아 트랜잭션이 아예 안 걸린다</b> —
 * 조용히 실패하는 종류라 더 나쁘다. 그래서 별도 빈으로 뺐다.
 *
 * <p>세션을 다시 읽는 것도 의도적이다. 서비스가 읽어둔 인스턴스는 LLM 호출 시간만큼 묵은
 * detached 객체라, 여기서 새로 읽어야 {@code @Version} 낙관적 락이 실제로 동시 수정을 잡는다.
 */
@Component
class CoverageWriter {

	private final ConsultationSessionRepository sessionRepository;
	private final CoverageResultRepository coverageResultRepository;
	private final GateOverrideRepository gateOverrideRepository;
	private final StateMachine stateMachine;

	CoverageWriter(ConsultationSessionRepository sessionRepository,
	               CoverageResultRepository coverageResultRepository,
	               GateOverrideRepository gateOverrideRepository,
	               StateMachine stateMachine) {
		this.sessionRepository = sessionRepository;
		this.coverageResultRepository = coverageResultRepository;
		this.gateOverrideRepository = gateOverrideRepository;
		this.stateMachine = stateMachine;
	}

	/** 분석 결과 저장 + 세션 상태 전이를 한 트랜잭션으로 묶는다 */
	@Transactional
	SessionStatus saveAnalysis(String sessionId, List<CoverageResult> results, SessionStatus target) {
		coverageResultRepository.saveAll(results);
		return moveTo(sessionId, target);
	}

	/** Override 저장 + (열렸다면) 세션 상태 전이 */
	@Transactional
	SessionStatus saveOverrides(String sessionId, List<GateOverride> overrides, SessionStatus target) {
		gateOverrideRepository.saveAll(overrides);
		return moveTo(sessionId, target);
	}

	/**
	 * 이미 목표 상태면 전이를 시도하지 않는다.
	 *
	 * <p>전이표에 자기 자신으로 가는 전이가 없기 때문이다 — 보완 설명 뒤 재분석했는데 여전히
	 * 통과라면 {@code COVERAGE_ANALYZED → COVERAGE_ANALYZED} 가 되는데, 이건 상태 변화가
	 * 아니라 변화 없음이다. 전이표에 자기 루프를 넣으면 "같은 상태로 갈 수 있다"는 뜻이 되어
	 * 다른 곳에서 의미가 흐려지므로, 표가 아니라 여기서 걸러낸다.
	 */
	private SessionStatus moveTo(String sessionId, SessionStatus target) {
		ConsultationSession session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND,
						"상담 세션을 찾을 수 없습니다."));

		if (session.getStatus() != target) {
			session.transitionTo(target, stateMachine);
		}
		return session.getStatus();
	}
}
