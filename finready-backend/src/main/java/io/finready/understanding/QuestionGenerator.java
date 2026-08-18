package io.finready.understanding;

import java.util.List;

/**
 * 이해확인 질문 생성 포트 (F04). <b>구현체가 아직 없다</b> — 모델 미결정(TRD D-02).
 *
 * <p><b>모델은 문항을 창작하지 않는다.</b> 계약이 "검수 문항의 표현만 조정한다"고 못 박았다 —
 * 검수되지 않은 질문이 고객에게 나가면 그 자체가 상담 품질 문제가 된다. 그래서 포트가
 * {@code baseQuestion}(검수된 원문)을 반드시 함께 받는다. 받지 않으면 창작을 막을 방법이 없다.
 *
 * <p>생성 실패는 예외가 아니라 <b>정상 경로</b>다 — 호출부가 검수 문항을 그대로 쓰고
 * {@code source: FALLBACK} 으로 표시한다. 그래서 이 포트는 실패 시 예외를 던지기보다
 * 해당 Risk 를 결과에서 빼는 편이 낫고, 호출부가 빠진 것을 fallback 으로 메운다.
 */
public interface QuestionGenerator {

	/**
	 * 대상 Risk 전체를 <b>1회 batch call</b> 로 다듬는다.
	 *
	 * <p>반환에 없는 riskId 는 fallback 으로 처리된다 — 부분 성공을 허용한다는 뜻이다.
	 * 분류기({@code CoverageClassifier})가 부분 응답을 파싱 실패로 다루는 것과 다른데,
	 * 거기는 빠진 Risk 를 채울 검수 값이 없지만 여기는 있기 때문이다.
	 */
	List<PhrasedQuestion> phrase(List<QuestionSeed> seeds);

	/**
	 * @param baseQuestion      검수된 원문 질문. 모델은 이 의미를 벗어나면 안 된다
	 * @param customerLabel     고객 프로필 라벨. 표현 수준 조정에만 쓴다
	 * @param explanationLevel  EASY / NORMAL
	 */
	record QuestionSeed(String riskId,
	                    String title,
	                    String fact,
	                    String baseQuestion,
	                    String customerLabel,
	                    String explanationLevel) {
	}

	/** @param question 다듬어진 문항. 검수 원문의 의미를 유지해야 한다 */
	record PhrasedQuestion(String riskId, String question) {
	}
}
