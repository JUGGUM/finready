package io.finready.common;

import io.finready.session.SessionStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * TRD §5.1 SessionStatus 전이표. 상태 전이는 여기를 통과한다(CLAUDE.md 규칙 7).
 *
 * <p>표를 통째로 넣는다. 트리거되는 시점은 F03 이후지만 쪼개서 넣으면
 * "단일 지점"이 성립하지 않고, 어느 전이가 빠졌는지도 안 보인다.
 *
 * <p>{@code SESSION_CLOSED_*} 에서 나가는 전이는 없다. 종료된 세션은 어떤 경로로도
 * 되살아나지 않는다.
 *
 * <p>패키지 의존이 common → session 으로 가는데, session 도 common(ApiException)을
 * 참조하므로 순환이다. TRD §2.1 이 StateMachine 을 common 에 두라고 명시해서 감수한다.
 * 대안은 SessionStatus 를 common 으로 옮기는 것인데, 다른 도메인 enum 들과 배치가 어긋난다.
 */
@Component
public class StateMachine {

	private static final Map<SessionStatus, Set<SessionStatus>> ALLOWED = new EnumMap<>(SessionStatus.class);

	static {
		ALLOWED.put(SessionStatus.DRAFT,
				EnumSet.of(SessionStatus.COVERAGE_ANALYZED, SessionStatus.GATE_BLOCKED));
		ALLOWED.put(SessionStatus.COVERAGE_ANALYZED,
				EnumSet.of(SessionStatus.GATE_BLOCKED, SessionStatus.UNDERSTANDING_IN_PROGRESS));
		ALLOWED.put(SessionStatus.GATE_BLOCKED,
				EnumSet.of(SessionStatus.COVERAGE_ANALYZED));
		ALLOWED.put(SessionStatus.UNDERSTANDING_IN_PROGRESS,
				EnumSet.of(SessionStatus.AWAITING_STAFF_REVIEW));
		ALLOWED.put(SessionStatus.AWAITING_STAFF_REVIEW,
				EnumSet.of(SessionStatus.SESSION_CLOSED_BY_STAFF, SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED));
		ALLOWED.put(SessionStatus.SESSION_CLOSED_BY_STAFF, EnumSet.noneOf(SessionStatus.class));
		ALLOWED.put(SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED, EnumSet.noneOf(SessionStatus.class));
	}

	/**
	 * @throws ApiException 허용되지 않은 전이면 INVALID_STATE_TRANSITION(409)
	 */
	public void assertCanTransition(SessionStatus from, SessionStatus to) {
		if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					"현재 단계에서는 진행할 수 없습니다. 화면을 새로고침해 주세요.");
		}
	}

	public boolean canTransition(SessionStatus from, SessionStatus to) {
		return ALLOWED.getOrDefault(from, Set.of()).contains(to);
	}

	/** 종료 상태는 나가는 전이가 없다. Revision 추가 같은 쓰기 작업을 막는 데도 쓴다 */
	public boolean isClosed(SessionStatus status) {
		return status == SessionStatus.SESSION_CLOSED_BY_STAFF
				|| status == SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED;
	}
}
