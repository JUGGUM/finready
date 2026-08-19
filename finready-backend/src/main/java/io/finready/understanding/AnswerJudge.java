package io.finready.understanding;

/**
 * 고객 답변 3상태 판정 포트 (F05/F07). <b>구현체가 아직 없다</b> — 모델 미결정(TRD D-02).
 *
 * <p>Coverage 쪽 포트와 달리 <b>배치가 아니다.</b> 고객이 답변을 하나 제출할 때마다 한 번
 * 호출되며, 그 사이에 화면이 응답을 기다린다. 묶을 대상이 애초에 없다.
 *
 * <p>구현할 때 지킬 것:
 * <ul>
 *   <li>enum 밖의 값이 오면 파싱 실패로 처리한다. 임의 매핑 금지(규칙 9).</li>
 *   <li>{@code MISUNDERSTOOD}/{@code UNCERTAIN} 은 <b>사유가 필수</b>다 —
 *       DB 의 {@code ck_reason_required} 가 강제한다(PRD §9 F05). 사유 없이 반환하면
 *       INSERT 에서 터지므로 구현체가 먼저 파싱 실패로 다루는 편이 낫다.</li>
 *   <li>호출은 트랜잭션 밖에서 한다(규칙 6).</li>
 * </ul>
 */
public interface AnswerJudge {

	Verdict judge(String sessionId, JudgeRequest request);

	/**
	 * @param fact     이 Risk 가 이해됐다고 볼 수 있는 사실. 검수된 값이다
	 * @param question 실제로 고객에게 나간 문항. 발급된 것과 다른 걸 넘기면 판정 근거가 어긋난다
	 * @param attempt  1 또는 2. 후속 확인은 맥락이 다르므로 프롬프트가 달라질 수 있다
	 */
	record JudgeRequest(String riskId,
	                    String title,
	                    String fact,
	                    String question,
	                    String answer,
	                    int attempt) {
	}

	/** @param reason UNDERSTOOD 가 아니면 반드시 채운다 */
	record Verdict(UnderstandingStatus status, String reason) {
	}
}
