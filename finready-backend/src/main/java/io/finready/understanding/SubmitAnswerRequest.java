package io.finready.understanding;

/**
 * {@code /understanding}(attempt 1) 과 {@code /recheck}(attempt 2) 의 requestBody.
 * 두 엔드포인트가 같은 모양이라 하나로 둔다 — attempt 는 경로가 정하지, 클라이언트가 정하지 않는다.
 *
 * @param answerSource 고객 직접 입력인지 직원 대독인지. 판정 자체에는 쓰지 않고 기록에만 남는다
 */
public record SubmitAnswerRequest(
		String riskId,
		String answer,
		AnswerSource answerSource
) {
}
