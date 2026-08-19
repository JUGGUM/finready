package io.finready.explanation;

/** openapi {@code POST /sessions/{sessionId}/reexplain} 요청 본문. required: [riskId] */
public record ReExplainRequest(String riskId) {
}
