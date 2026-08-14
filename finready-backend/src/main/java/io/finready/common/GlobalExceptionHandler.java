package io.finready.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 오류 응답을 openapi Error 스키마 하나로 모은다.
 *
 * <p>스택트레이스와 내부 예외 문구는 응답에 넣지 않는다 (TRD §12).
 * 원인은 서버 로그에만 남기고, 화면에는 requestId 로 연결한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
		ErrorCode code = ex.code();
		// 계약에 정의된 오류는 정상 흐름이다. 스택트레이스를 남기지 않는다
		log.warn("{} - {}", code, ex.getMessage());
		return ResponseEntity
				.status(code.status())
				.body(new ErrorResponse(code, ex.getMessage(), ex.riskId(),
						code.recoverable(), RequestIdFilter.current()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		// 예상 못 한 오류만 스택트레이스를 남긴다. 응답에는 나가지 않는다
		log.error("처리하지 못한 예외", ex);
		return ResponseEntity
				.status(code.status())
				.body(new ErrorResponse(code, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
						null, code.recoverable(), RequestIdFilter.current()));
	}
}
