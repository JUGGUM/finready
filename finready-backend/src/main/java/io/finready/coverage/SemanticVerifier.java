package io.finready.coverage;

import java.util.List;

/**
 * Evidence Semantic Verifier 포트 (TRD §8.5). {@link CoverageClassifier} 와 마찬가지로
 * <b>구현체가 아직 없다</b> — LLM 모델·요금제가 미결정이기 때문이다(TRD D-02).
 *
 * <p>분류기와 역할이 다르다. 분류기는 "이 Risk 가 설명됐는가"를 보고, Verifier 는
 * <b>이미 인용된 근거가 그 주장을 실제로 뒷받침하는가</b>를 본다. 둘을 한 호출로 합치지 않는
 * 이유는 키워드가 겹치지만 의미가 반대인 경우를 잡기 위해서다 — 예를 들어 "낙인이 없다"를
 * "원금이 지켜진다"로 등치시킨 상담문은 분류기 눈에는 R01 을 설명한 것처럼 보이지만
 * 의미로는 CONTRADICTS 다.
 *
 * <p>구현할 때 지킬 것:
 * <ul>
 *   <li>enum 밖의 문자열이 오면 파싱 실패로 처리한다. 임의 매핑 금지(규칙 9).</li>
 *   <li>호출은 트랜잭션 밖에서 한다(규칙 6).</li>
 *   <li>대상 축소로 성능을 벌지 않는다 — 계약이 명시적으로 금지한다. 레이턴시가 문제면
 *       프롬프트 토큰 축소·모델 교체·부분 병렬화로 대응한다.</li>
 * </ul>
 */
public interface SemanticVerifier {

	/**
	 * 대상 Risk 전체를 <b>1회 batch call</b> 로 재검증한다.
	 *
	 * @param transcript revision 원문. 인용 구간만 보내면 앞뒤 맥락이 잘려 반대 의미를 놓친다
	 */
	List<RelationVerdict> verify(String transcript, List<VerificationRequest> requests);

	/**
	 * 이 구현이 쓰는 프롬프트 버전 (TRD §7.2). 분류기와 따로 관리한다 — 한쪽만 고치는 일이
	 * 잦고, 그때 어느 쪽이 바뀌었는지가 재현에 필요하다.
	 */
	default String promptVersion() {
		return null;
	}

	/**
	 * @param fact         이 Risk 가 설명됐다고 볼 수 있는 사실. 검수된 값이다
	 * @param evidenceText 분류기가 인용한 구간. 서버가 원문 대조를 통과시킨 것만 넘어온다
	 */
	record VerificationRequest(String riskId, String title, String fact, String evidenceText) {
	}

	/**
	 * @param riskId   요청한 riskId 와 같아야 한다. 다르면 파싱 실패로 다룬다
	 * @param relation 4관계 판정
	 * @param reason   판정 근거. {@code verification_reason} 에 그대로 들어간다
	 */
	record RelationVerdict(String riskId, SemanticRelation relation, String reason) {
	}
}
