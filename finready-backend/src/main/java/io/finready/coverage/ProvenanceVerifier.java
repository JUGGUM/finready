package io.finready.coverage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * evidence 가 상담 원문에 <b>정확히 1회</b> 존재하는지 검증하고, 원문 기준 offset 을
 * 서버가 다시 계산한다 (TRD §8.3~8.4, CLAUDE.md 규칙 4).
 *
 * <p>LLM 을 호출하지 않는다. 문자열 검사만으로 끝나므로 모델 선정(TRD D-02)과 무관하게
 * 지금 확정할 수 있는 부분이다.
 *
 * <p><b>판정 순서가 곧 실패 사유의 우선순위다.</b> 비었는지 → 길이가 범위 안인지 →
 * 원문에 몇 번 나오는지. 길이 검사를 먼저 하는 이유는, 너무 짧은 인용은 원문 여러 곳에
 * 우연히 맞아 AMBIGUOUS 로 잡히기 때문이다. 그때 "모호함"이라고 알리면 직원은 상담문을
 * 고치려 들지만 실제 원인은 인용이 짧다는 것이라 행동이 어긋난다.
 */
@Component
public class ProvenanceVerifier {

	private final int minLength;
	private final int maxLength;

	public ProvenanceVerifier(@Value("${finready.evidence.min-length}") int minLength,
	                          @Value("${finready.evidence.max-length}") int maxLength) {
		this.minLength = minLength;
		this.maxLength = maxLength;
	}

	/**
	 * @param transcript   revision 원문. 다듬지 않은 그대로여야 offset 이 화면과 맞는다
	 * @param evidenceText LLM 이 인용했다고 주장하는 구간
	 */
	public ProvenanceCheck verify(String transcript, String evidenceText) {
		if (evidenceText == null || evidenceText.isBlank()) {
			return ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY);
		}

		OffsetMapper transcriptMapper = OffsetMapper.of(transcript);
		String needle = OffsetMapper.of(evidenceText).normalized();

		// 길이는 정규화 후로 잰다. 원문 쪽 공백이 어떻든 "실제 인용된 내용의 양"이 기준이다
		if (needle.length() < minLength) {
			return ProvenanceCheck.failed(ProvenanceFailureReason.TOO_SHORT);
		}
		if (needle.length() > maxLength) {
			return ProvenanceCheck.failed(ProvenanceFailureReason.TOO_LONG);
		}

		int occurrences = transcriptMapper.countOccurrences(needle);
		if (occurrences == 0) {
			return ProvenanceCheck.failed(ProvenanceFailureReason.NOT_FOUND);
		}
		if (occurrences > 1) {
			// 재량 판단이 개입하지 않는다. 첫 번째 것을 고르지 않는다 (TRD §8.4)
			return ProvenanceCheck.failed(ProvenanceFailureReason.AMBIGUOUS);
		}

		OffsetMapper.OriginalRange range = transcriptMapper.toOriginalRange(needle);
		return ProvenanceCheck.found(range.start(), range.end());
	}
}
