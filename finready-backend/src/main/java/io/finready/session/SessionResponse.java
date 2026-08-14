package io.finready.session;

import java.time.OffsetDateTime;

/** openapi.yml v1.4.2 SessionResponse. required: [sessionId, sessionStatus] */
public record SessionResponse(
		String sessionId,
		String productId,
		String customerId,
		String productRiskVersion,
		SessionStatus sessionStatus,
		OffsetDateTime createdAt,
		OffsetDateTime closedAt
) {
	public static SessionResponse from(ConsultationSession session) {
		return new SessionResponse(
				session.getId(),
				session.getProductId(),
				session.getCustomerId(),
				session.getProductRiskVersion(),
				session.getStatus(),
				session.getCreatedAt(),
				session.getClosedAt());
	}
}
