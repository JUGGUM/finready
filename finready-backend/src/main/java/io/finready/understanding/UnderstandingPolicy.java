package io.finready.understanding;

/**
 * 이해확인 정책 상수. 흩어지면 서로 어긋난다.
 *
 * <p>{@code MAX_ATTEMPTS} 는 <b>세 곳이 같은 값이어야 한다</b> — 여기, DB 의
 * {@code ck_understanding_attempt}/{@code ck_question_attempt}
 * ({@code attempt between 1 and 2}), 그리고 {@code application.yaml} 의
 * {@code finready.understanding.max-attempts}. 설정으로 낮추는 것은 되지만
 * <b>2를 넘길 수는 없다</b> — DB check 제약이 먼저 거부한다.
 */
final class UnderstandingPolicy {

	/** attempt 1 = /understanding, attempt 2 = /recheck */
	static final int MAX_ATTEMPTS = 2;

	static final short FIRST_ATTEMPT = 1;
	static final short RECHECK_ATTEMPT = 2;

	private UnderstandingPolicy() {
	}
}
