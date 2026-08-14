package io.finready.session;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * openapi.yml v1.4.2 SessionSnapshotResponse.
 * 계약이 {@code allOf: [SessionResponse, {...}]} 라 JSON 이 평평하다. record 는 상속이
 * 안 되므로 SessionResponse 필드를 그대로 펼쳐 적는다.
 *
 * <p>coverage · nextAction · understanding 은 아직 만들지 않은 단계의 값이다.
 * 계약이 <b>명시적으로 null 과 빈 배열을 허용</b>한다("Coverage 단계이거나 세션 종료 후에는 null").
 * F03 / F04 에서 실제 타입으로 교체한다 — 그때까지 Object 는 자리표시다.
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
		Object nextAction,
		RevisionResponse currentRevision,
		Object coverage,
		List<Object> understanding
) {
}
