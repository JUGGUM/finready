package io.finready.session;

/** POST /api/sessions/{id}/revisions 요청 본문. openapi required: [text], 1~8000자 */
public record CreateRevisionRequest(String text) {
}
