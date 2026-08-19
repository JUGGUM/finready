package io.finready.understanding;

/**
 * openapi {@code POST /sessions/{sessionId}/risks/{riskId}/staff-resolution} 요청 본문.
 * required: [disposition, reason, actor]. reason 은 5~500자 (ck_resolution_reason_len).
 */
public record StaffResolutionRequest(StaffDisposition disposition, String reason, String actor) {
}
