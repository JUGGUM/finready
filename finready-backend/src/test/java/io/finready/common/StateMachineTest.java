package io.finready.common;

import io.finready.session.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TRD §5.1 전이표 전수 검증.
 *
 * <p>7x7 = 49 조합을 전부 본다. 허용 8개를 나열하고 나머지는 자동으로 "막혀야 한다"로
 * 검사하므로, 전이표에 없는 전이를 실수로 열면 여기서 걸린다.
 */
class StateMachineTest {

	private final StateMachine stateMachine = new StateMachine();

	/** TRD §5.1 표에 있는 전이. 여기 없는 조합은 전부 막혀야 한다 */
	private static final Set<List<SessionStatus>> ALLOWED = Set.of(
			List.of(SessionStatus.DRAFT, SessionStatus.COVERAGE_ANALYZED),
			List.of(SessionStatus.DRAFT, SessionStatus.GATE_BLOCKED),
			List.of(SessionStatus.COVERAGE_ANALYZED, SessionStatus.GATE_BLOCKED),
			List.of(SessionStatus.COVERAGE_ANALYZED, SessionStatus.UNDERSTANDING_IN_PROGRESS),
			List.of(SessionStatus.GATE_BLOCKED, SessionStatus.COVERAGE_ANALYZED),
			List.of(SessionStatus.UNDERSTANDING_IN_PROGRESS, SessionStatus.AWAITING_STAFF_REVIEW),
			List.of(SessionStatus.AWAITING_STAFF_REVIEW, SessionStatus.SESSION_CLOSED_BY_STAFF),
			List.of(SessionStatus.AWAITING_STAFF_REVIEW, SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED));

	static List<List<SessionStatus>> allCombinations() {
		List<List<SessionStatus>> combinations = new ArrayList<>();
		for (SessionStatus from : SessionStatus.values()) {
			for (SessionStatus to : SessionStatus.values()) {
				combinations.add(List.of(from, to));
			}
		}
		return combinations;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("allCombinations")
	@DisplayName("49개 조합이 TRD §5.1 표와 정확히 일치한다")
	void everyCombinationMatchesTheTable(List<SessionStatus> pair) {
		SessionStatus from = pair.get(0);
		SessionStatus to = pair.get(1);

		assertThat(stateMachine.canTransition(from, to))
				.as("%s -> %s", from, to)
				.isEqualTo(ALLOWED.contains(pair));
	}

	@Test
	@DisplayName("표에 있는 전이 8개는 예외 없이 통과한다")
	void allowedTransitionsPass() {
		for (List<SessionStatus> pair : ALLOWED) {
			assertThatCode(() -> stateMachine.assertCanTransition(pair.get(0), pair.get(1)))
					.as("%s -> %s", pair.get(0), pair.get(1))
					.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("종료 상태")
	class ClosedStates {

		/**
		 * 종료된 세션이 되살아나면 감사 기록이 무너진다.
		 * CLOSED_* 두 개에서 나가는 14개 전이가 전부 막혀야 한다(자기 자신 포함).
		 */
		@ParameterizedTest
		@EnumSource(SessionStatus.class)
		@DisplayName("SESSION_CLOSED_BY_STAFF 에서는 어디로도 못 간다")
		void closedByStaffIsTerminal(SessionStatus to) {
			assertThat(stateMachine.canTransition(SessionStatus.SESSION_CLOSED_BY_STAFF, to)).isFalse();
		}

		@ParameterizedTest
		@EnumSource(SessionStatus.class)
		@DisplayName("SESSION_CLOSED_WITH_UNRESOLVED 에서는 어디로도 못 간다")
		void closedWithUnresolvedIsTerminal(SessionStatus to) {
			assertThat(stateMachine.canTransition(SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED, to)).isFalse();
		}

		@Test
		@DisplayName("isClosed 는 CLOSED_* 두 개만 true 다")
		void isClosedOnlyForTerminalStates() {
			for (SessionStatus status : SessionStatus.values()) {
				boolean expected = status == SessionStatus.SESSION_CLOSED_BY_STAFF
						|| status == SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED;
				assertThat(stateMachine.isClosed(status)).as("%s", status).isEqualTo(expected);
			}
		}
	}

	@Test
	@DisplayName("같은 상태로의 전이는 허용하지 않는다")
	void selfTransitionIsRejected() {
		for (SessionStatus status : SessionStatus.values()) {
			assertThat(stateMachine.canTransition(status, status)).as("%s", status).isFalse();
		}
	}

	@Test
	@DisplayName("막힌 전이는 INVALID_STATE_TRANSITION(409) 이다")
	void rejectedTransitionThrowsContractError() {
		assertThatThrownBy(() -> stateMachine.assertCanTransition(
				SessionStatus.DRAFT, SessionStatus.SESSION_CLOSED_BY_STAFF))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).code())
				.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

		assertThat(ErrorCode.INVALID_STATE_TRANSITION.status().value()).isEqualTo(409);
	}
}
