package io.finready.coverage;

/**
 * evidence 가 원문에 실제로 있는지에 대한 판정 결과.
 *
 * <p>{@code coverage_result} 의 ck_provenance_consistency 를 코드에서도 성립시킨다 —
 * valid 면 offset 두 개가 반드시 있고 실패 사유는 없다. 그 반대도 마찬가지다.
 * 생성자를 막고 팩토리만 열어둔 이유가 이것이다.
 */
public record ProvenanceCheck(
		boolean valid,
		Integer startOffset,
		Integer endOffset,
		ProvenanceFailureReason failureReason
) {

	public ProvenanceCheck {
		if (valid && (startOffset == null || endOffset == null || failureReason != null)) {
			throw new IllegalArgumentException(
					"provenance 성공은 offset 두 개를 갖고 실패 사유가 없어야 한다 (ck_provenance_consistency)");
		}
		if (!valid && failureReason == null) {
			throw new IllegalArgumentException("provenance 실패는 사유가 있어야 한다");
		}
	}

	public static ProvenanceCheck found(int startOffset, int endOffset) {
		return new ProvenanceCheck(true, startOffset, endOffset, null);
	}

	/** 실패 시 offset 을 남기지 않는다. 프론트가 하이라이트할 근거가 없기 때문이다 */
	public static ProvenanceCheck failed(ProvenanceFailureReason reason) {
		return new ProvenanceCheck(false, null, null, reason);
	}
}
