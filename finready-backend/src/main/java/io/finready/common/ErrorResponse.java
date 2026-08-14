package io.finready.common;

/**
 * openapi.yml v1.4.2 의 Error 스키마.
 *
 * @param code        계약 enum
 * @param message     화면에 그대로 노출 가능한 한국어 메시지. 내부 예외 문구를 그대로 담지 않는다
 * @param riskId      Risk 단위 오류일 때만. 아니면 null
 * @param recoverable true 면 프론트가 재시도 버튼을 노출한다
 * @param requestId   서버 로그 추적용. RequestIdFilter 가 넣은 MDC 값과 같다
 */
public record ErrorResponse(
		ErrorCode code,
		String message,
		String riskId,
		boolean recoverable,
		String requestId
) {
}
