package io.finready.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Set;

/**
 * 모든 오류 응답을 openapi Error 스키마 하나로 모은다.
 *
 * <p>스택트레이스와 내부 예외 문구는 응답에 넣지 않는다 (TRD §12).
 * 원인은 서버 로그에만 남기고, 화면에는 requestId 로 연결한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * 재시도로 해소되는 제약. <b>유니크 제약만</b> 넣는다.
	 *
	 * <p>{@code uq_revision} — revisionNo 를 읽고 +1 하는 채번이라 동시 요청이 같은 번호를
	 * 계산할 수 있다. 재시도하면 다음 번호를 읽으므로 성공한다.
	 */
	private static final Set<String> RETRYABLE_CONSTRAINTS = Set.of("uq_revision");

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

	/**
	 * consultation_session.version(@Version) 충돌. 더블 클릭으로 같은 세션을 동시에 고칠 때 난다
	 * (TRD §5.3). recoverable=true 라 프론트가 재시도 버튼을 띄운다.
	 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		ErrorCode code = ErrorCode.CONCURRENT_SESSION_UPDATE;
		log.warn("{} - 낙관적 락 충돌: {}", code, ex.getMessage());
		return ResponseEntity
				.status(code.status())
				.body(new ErrorResponse(code, "다른 작업이 먼저 반영됐습니다. 새로고침 후 다시 시도해 주세요.",
						null, code.recoverable(), RequestIdFilter.current()));
	}

	/**
	 * 제약 위반. <b>전부를 409 로 뭉뚱그리지 않는다.</b>
	 *
	 * <p>동시 채번 충돌({@code uq_revision})은 재시도하면 성공하는 상황이므로 409 +
	 * recoverable 로 내보낸다. 반면 {@code ck_*} 체크 제약 위반은 애플리케이션이 DB 규칙을
	 * 우회했다는 뜻이다 — 특히 {@code ck_explained_requires_verification}(규칙 3)이 걸렸다면
	 * 검증을 건너뛴 코드가 있다는 신호다. 이런 것을 친절한 409 로 감싸면 버그가 조용히 묻히므로
	 * 기존대로 500 + 스택트레이스로 남긴다.
	 *
	 * @see docs/decisions/2026-08-14-revision-no-race-condition.md
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		// 이름을 못 찾으면(null) 재시도 대상이 아닌 것으로 본다 — 보수적으로 500 을 택한다
		String constraint = ConstraintNames.of(ex);

		// Postgres 는 제약 이름을 소문자로 접지만 드라이버가 그대로 준다는 보장은 없다
		if (constraint != null && RETRYABLE_CONSTRAINTS.contains(constraint.toLowerCase(Locale.ROOT))) {
			ErrorCode code = ErrorCode.CONCURRENT_SESSION_UPDATE;
			log.warn("{} - 제약 {} 충돌. 동시 요청으로 판단한다", code, constraint);
			return ResponseEntity
					.status(code.status())
					.body(new ErrorResponse(code, "다른 작업이 먼저 반영됐습니다. 다시 시도해 주세요.",
							null, code.recoverable(), RequestIdFilter.current()));
		}

		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		log.error("제약 위반 [{}] — 애플리케이션이 DB 규칙을 우회했을 수 있다", constraint, ex);
		return ResponseEntity
				.status(code.status())
				.body(new ErrorResponse(code, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
						null, code.recoverable(), RequestIdFilter.current()));
	}

	/** 본문이 JSON 이 아니거나 형식이 깨진 경우. 안 잡으면 500 으로 나간다 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
		ErrorCode code = ErrorCode.INVALID_REQUEST;
		log.warn("{} - 요청 본문을 읽지 못함", code);
		return ResponseEntity
				.status(code.status())
				.body(new ErrorResponse(code, "요청 형식이 올바르지 않습니다.",
						null, code.recoverable(), RequestIdFilter.current()));
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
