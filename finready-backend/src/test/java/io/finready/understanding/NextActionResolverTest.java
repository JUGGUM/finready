package io.finready.understanding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약(TRD §6.6)의 nextAction 표를 그대로 고정한다.
 *
 * <p>프론트가 이 값 하나만 보고 화면을 옮기므로(규칙 8), 한 칸이 틀리면 "이해했는데 재설명으로
 * 감" 같은 흐름이 된다. 표가 작아서 전수로 덮는다.
 */
@DisplayName("NextActionResolver — 계약 표")
class NextActionResolverTest {

	private final NextActionResolver resolver = new NextActionResolver();

	@Test
	@DisplayName("UNDERSTOOD, 남은 Risk 있음 → NEXT_RISK")
	void understoodWithRemaining() {
		assertThat(resolver.afterAnswer(UnderstandingStatus.UNDERSTOOD, 1, true))
				.isEqualTo(NextAction.NEXT_RISK);
	}

	@Test
	@DisplayName("UNDERSTOOD, 마지막 Risk → GO_TO_REPORT")
	void understoodLastRisk() {
		assertThat(resolver.afterAnswer(UnderstandingStatus.UNDERSTOOD, 1, false))
				.isEqualTo(NextAction.GO_TO_REPORT);
	}

	@Test
	@DisplayName("MISUNDERSTOOD, attempt 1 → REEXPLAIN")
	void misunderstoodFirstAttempt() {
		assertThat(resolver.afterAnswer(UnderstandingStatus.MISUNDERSTOOD, 1, true))
				.isEqualTo(NextAction.REEXPLAIN);
	}

	/**
	 * PRD §7.5 가 경로를 갈라 놨다 — MISUNDERSTOOD 만 재설명을 거치고 UNCERTAIN 은 바로
	 * 후속 확인이다. 헷갈리기 쉬운 지점이라 따로 고정한다.
	 */
	@Test
	@DisplayName("UNCERTAIN, attempt 1 → RECHECK (재설명으로 가지 않는다)")
	void uncertainSkipsReexplain() {
		assertThat(resolver.afterAnswer(UnderstandingStatus.UNCERTAIN, 1, true))
				.isEqualTo(NextAction.RECHECK);
	}

	@ParameterizedTest
	@EnumSource(value = UnderstandingStatus.class, names = {"MISUNDERSTOOD", "UNCERTAIN"})
	@DisplayName("attempt 2 후에도 안 풀리면 STAFF_RESOLUTION_REQUIRED")
	void secondAttemptFailureNeedsStaff(UnderstandingStatus status) {
		assertThat(resolver.afterAnswer(status, 2, true))
				.isEqualTo(NextAction.STAFF_RESOLUTION_REQUIRED);
	}

	@Test
	@DisplayName("attempt 2 에서 UNDERSTOOD 면 남은 Risk 여부로 갈린다")
	void secondAttemptUnderstood() {
		assertThat(resolver.afterAnswer(UnderstandingStatus.UNDERSTOOD, 2, true))
				.isEqualTo(NextAction.NEXT_RISK);
		assertThat(resolver.afterAnswer(UnderstandingStatus.UNDERSTOOD, 2, false))
				.isEqualTo(NextAction.GO_TO_REPORT);
	}

	@Test
	@DisplayName("직원 처리 후 — 남은 Risk 있으면 NEXT_RISK, 없으면 GO_TO_REPORT")
	void afterStaffResolution() {
		assertThat(resolver.afterStaffResolution(true)).isEqualTo(NextAction.NEXT_RISK);
		assertThat(resolver.afterStaffResolution(false)).isEqualTo(NextAction.GO_TO_REPORT);
	}

	@Test
	@DisplayName("REEXPLAIN 은 MISUNDERSTOOD attempt 1 에서만 나온다")
	void reexplainOnlyFromMisunderstoodFirstAttempt() {
		for (UnderstandingStatus status : UnderstandingStatus.values()) {
			for (int attempt = 1; attempt <= 2; attempt++) {
				for (boolean remaining : new boolean[]{true, false}) {
					if (resolver.afterAnswer(status, attempt, remaining) == NextAction.REEXPLAIN) {
						assertThat(status).isEqualTo(UnderstandingStatus.MISUNDERSTOOD);
						assertThat(attempt).isEqualTo(1);
					}
				}
			}
		}
	}
}
