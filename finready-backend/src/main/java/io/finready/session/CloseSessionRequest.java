package io.finready.session;

import java.util.List;

/**
 * openapi.yml v1.4.2 {@code POST /sessions/{id}/close} 요청. required: [actor]
 *
 * <p><b>{@code actorRole} 이 없다.</b> P0 에는 인증이 없어 클라이언트가 신고한 역할은
 * 검증할 수 없고, 검증 불가능한 검사는 통제가 있다는 인상만 만든다. 서버가 감사 이벤트에
 * {@code actorRole=STAFF} 를 고정 기록한다 (계약 명시).
 *
 * @param actor                데모용 직원 식별자. <b>인증된 신원이 아니다</b> — 감사 기록용일 뿐
 *                             권한 증명으로 쓰이지 않는다 (PRD §14.2)
 * @param unresolvedReason     미해결 항목이 있을 때 필수
 * @param acknowledgedWarnings WARN_ONLY 미확인 Risk 의 riskId 전체. 누락 시 400
 */
public record CloseSessionRequest(
		String actor,
		String unresolvedReason,
		List<String> acknowledgedWarnings
) {
}
