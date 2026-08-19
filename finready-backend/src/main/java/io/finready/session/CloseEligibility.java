package io.finready.session;

import java.util.List;

/**
 * openapi.yml v1.4.2 {@code ReportResponse.closeEligibility}.
 *
 * <p>종료 버튼의 활성화 조건이다. <b>프론트가 재계산하지 않는다</b>(규칙 8) — 조건이 두 곳에
 * 있으면 버튼은 눌리는데 서버가 400 을 주는 조합이 생긴다.
 *
 * @param canClose                       지금 종료할 수 있는지. 세션 상태 전이표가 정한다
 * @param requiresUnresolvedReason       true 면 {@code unresolvedReason} 없이 종료할 수 없다
 * @param requiresWarningAcknowledgement 종료 전에 직원이 확인해야 하는 WARN_ONLY Risk.
 *                                       Gate 는 막지 않았지만 <b>기록 없이 넘어가면 안 되는</b> 항목들이다
 * @param expectedCloseStatus            지금 종료하면 어떤 상태가 되는지. 화면 문구가 이 값으로 갈린다
 */
public record CloseEligibility(
		boolean canClose,
		boolean requiresUnresolvedReason,
		List<String> requiresWarningAcknowledgement,
		SessionStatus expectedCloseStatus
) {
}
