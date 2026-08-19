package io.finready.explanation;

/**
 * F06 쉬운 말 재설명 생성 포트.
 *
 * <p><b>Vector 검색을 하지 않는다</b>(계약). {@code riskId} 로 검수된 Risk Schema 의
 * {@code sourcePage}·{@code sourceText} 를 직접 조회해 그대로 넘긴다 — 근거가 검수 범위
 * 밖으로 나갈 경로 자체를 만들지 않는다.
 */
public interface ReExplanationGenerator {

	/**
	 * @param sessionId      {@code llm_call_log} 에 남길 세션. <b>관측용이며 프롬프트에 넣지 않는다</b> —
	 *                       이 값이 없으면 어느 상담의 호출인지 알 수 없어 평가 재현이 불가능하다 (TRD §7.2)
	 * @param riskTitle      항목명
	 * @param riskFact       검수된 사실. 재설명이 전달해야 할 내용이다
	 * @param sourceText     상품설명서 원문. 숫자의 출처이기도 하다
	 * @param customerAnswer 고객이 반대로 이해한 내용. 무엇을 바로잡아야 하는지가 여기 있다
	 * @param explanationLevel 고객 프로필의 설명 수준. null 이면 기본 수준으로 쓴다
	 * @return 재설명 본문. 빈 값이면 호출부가 fallback 으로 처리한다
	 */
	String explain(String sessionId,
	               String riskId,
	               String riskTitle,
	               String riskFact,
	               String sourceText,
	               String customerAnswer,
	               String explanationLevel);
}
