package io.finready.session;

/** POST /api/sessions 요청 본문. openapi required: [productId, customerId] */
public record CreateSessionRequest(String productId, String customerId) {
}
