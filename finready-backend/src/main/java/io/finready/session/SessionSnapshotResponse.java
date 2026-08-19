package io.finready.session;

import io.finready.coverage.CoverageResponse;
import io.finready.understanding.NextAction;
import io.finready.understanding.RiskUnderstandingState;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * openapi.yml v1.4.2 SessionSnapshotResponse.
 * 계약이 {@code allOf: [SessionResponse, {...}]} 라 JSON 이 평평하다. record 는 상속이
 * 안 되므로 SessionResponse 필드를 그대로 펼쳐 적는다.
 *
 * <p><b>이 응답 하나로 새로고침이 복구된다.</b> 프론트는 여기 실린 값만 보고 화면을 되살리며
 * 어떤 것도 자체 계산하지 않는다(규칙 8).
 *
 * @param nextAction Understanding 단계 진행 중이면 현재 분기값. Coverage 단계이거나
 *                   세션 종료 후에는 null 이며, 이때는 {@code resumePoint} 를 쓴다
 * @param coverage   분석 전이면 null. DRAFT 세션을 새로고침한 정상적인 경우다
 */
public record SessionSnapshotResponse(
		String sessionId,
		String productId,
		String customerId,
		String productRiskVersion,
		SessionStatus sessionStatus,
		OffsetDateTime createdAt,
		OffsetDateTime closedAt,
		ResumePoint resumePoint,
		NextAction nextAction,
		RevisionResponse currentRevision,
		CoverageResponse coverage,
		List<RiskUnderstandingState> understanding
) {
}
