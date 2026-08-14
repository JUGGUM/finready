package io.finready.common;

/**
 * 계약에 정의된 오류를 던질 때 쓴다. HTTP 상태와 recoverable 은 ErrorCode 가 들고 있다.
 *
 * <p>message 는 <b>화면에 그대로 노출된다.</b> 내부 예외 문구·스택트레이스·식별자를 담지 않는다
 * (TRD §12, CLAUDE.md 규칙 10).
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;
	private final String riskId;

	public ApiException(ErrorCode code, String message) {
		this(code, message, null);
	}

	public ApiException(ErrorCode code, String message, String riskId) {
		super(message);
		this.code = code;
		this.riskId = riskId;
	}

	public ErrorCode code() {
		return code;
	}

	public String riskId() {
		return riskId;
	}
}
