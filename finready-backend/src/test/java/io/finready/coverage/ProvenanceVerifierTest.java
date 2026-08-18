package io.finready.coverage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙 4의 핵심 — 서버가 원문에서 offset 을 재계산한다.
 *
 * <p>여기서 잡으려는 것은 <b>하이라이트가 밀리는 버그</b>다. 정규화된 인덱스를 그대로
 * 응답에 실으면 공백이 병합된 만큼 어긋나는데, 화면을 띄우기 전에는 아무도 모른다.
 */
@DisplayName("ProvenanceVerifier — evidence 원문 대조")
class ProvenanceVerifierTest {

	private static final int MIN_LENGTH = 15;
	private static final int MAX_LENGTH = 300;

	private final ProvenanceVerifier verifier = new ProvenanceVerifier(MIN_LENGTH, MAX_LENGTH);

	@Nested
	@DisplayName("offset 재계산")
	class OffsetRecalculation {

		@Test
		@DisplayName("공백이 다른 인용도 찾아내고, offset 은 원문 기준으로 돌려준다")
		void mapsBackToOriginalOffsetsDespiteWhitespaceDifference() {
			// 원문에는 줄바꿈과 연속 공백이 있고, LLM 인용문에는 한 칸 공백만 있다
			String transcript = "앞부분입니다.\n\n이 상품은   원금이 보장되지 않습니다.  뒷부분입니다.";
			String evidence = "이 상품은 원금이 보장되지 않습니다.";

			ProvenanceCheck check = verifier.verify(transcript, evidence);

			assertThat(check.valid()).isTrue();
			assertThat(check.failureReason()).isNull();

			// 원문에서 잘라낸 구간이 실제로 그 문장이어야 한다 — 밀렸으면 여기서 깨진다
			String highlighted = transcript.substring(check.startOffset(), check.endOffset());
			assertThat(highlighted).isEqualTo("이 상품은   원금이 보장되지 않습니다.");
		}

		@Test
		@DisplayName("원문 맨 앞 인용의 offset 은 0에서 시작한다")
		void handlesMatchAtStart() {
			String transcript = "원금 손실이 발생할 수 있습니다. 그리고 다른 이야기가 이어집니다.";
			String evidence = "원금 손실이 발생할 수 있습니다.";

			ProvenanceCheck check = verifier.verify(transcript, evidence);

			assertThat(check.valid()).isTrue();
			assertThat(check.startOffset()).isZero();
			assertThat(transcript.substring(check.startOffset(), check.endOffset())).isEqualTo(evidence);
		}

		@Test
		@DisplayName("선행 공백이 있어도 원문 인덱스가 밀리지 않는다")
		void leadingWhitespaceDoesNotShiftOffsets() {
			String transcript = "   \n  원금 손실이 발생할 수 있습니다. 이어지는 설명입니다.";
			String evidence = "원금 손실이 발생할 수 있습니다.";

			ProvenanceCheck check = verifier.verify(transcript, evidence);

			assertThat(check.valid()).isTrue();
			assertThat(transcript.substring(check.startOffset(), check.endOffset())).isEqualTo(evidence);
		}

		@Test
		@DisplayName("이모지가 앞에 있어도 UTF-16 code unit 기준이라 경계가 맞는다")
		void surrogatePairsKeepOffsetsAligned() {
			// 이모지는 code unit 2개다. code point 기준으로 세면 여기서 어긋난다
			String transcript = "😀 안내드립니다. 원금 손실이 발생할 수 있습니다. 끝.";
			String evidence = "원금 손실이 발생할 수 있습니다.";

			ProvenanceCheck check = verifier.verify(transcript, evidence);

			assertThat(check.valid()).isTrue();
			assertThat(transcript.substring(check.startOffset(), check.endOffset())).isEqualTo(evidence);
		}
	}

	@Nested
	@DisplayName("실패 사유")
	class FailureReasons {

		@Test
		@DisplayName("evidence 가 비면 EMPTY")
		void emptyEvidence() {
			assertThat(verifier.verify("아무 내용", null).failureReason())
					.isEqualTo(ProvenanceFailureReason.EMPTY);
			assertThat(verifier.verify("아무 내용", "   ").failureReason())
					.isEqualTo(ProvenanceFailureReason.EMPTY);
		}

		@Test
		@DisplayName("min-length 미만이면 TOO_SHORT — 원문에 있어도 그렇다")
		void tooShortEvenWhenPresent() {
			String transcript = "원금 손실이 발생할 수 있습니다. 이어지는 설명입니다.";

			ProvenanceCheck check = verifier.verify(transcript, "원금 손실");

			assertThat(check.valid()).isFalse();
			assertThat(check.failureReason()).isEqualTo(ProvenanceFailureReason.TOO_SHORT);
		}

		@Test
		@DisplayName("max-length 초과면 TOO_LONG")
		void tooLong() {
			String longEvidence = "가".repeat(MAX_LENGTH + 1);

			ProvenanceCheck check = verifier.verify(longEvidence, longEvidence);

			assertThat(check.valid()).isFalse();
			assertThat(check.failureReason()).isEqualTo(ProvenanceFailureReason.TOO_LONG);
		}

		@Test
		@DisplayName("원문에 없으면 NOT_FOUND")
		void notFound() {
			ProvenanceCheck check = verifier.verify(
					"조기상환 조건을 안내드렸습니다. 계속 설명드리겠습니다.",
					"원금이 보장되지 않는 상품입니다.");

			assertThat(check.valid()).isFalse();
			assertThat(check.failureReason()).isEqualTo(ProvenanceFailureReason.NOT_FOUND);
		}

		@Test
		@DisplayName("두 번 이상 나오면 AMBIGUOUS — 첫 번째를 고르지 않는다 (TRD §8.4)")
		void ambiguousNeverPicksFirstOccurrence() {
			String repeated = "원금 손실이 발생할 수 있습니다.";
			String transcript = repeated + " 중간 설명입니다. " + repeated;

			ProvenanceCheck check = verifier.verify(transcript, repeated);

			assertThat(check.valid()).isFalse();
			assertThat(check.failureReason()).isEqualTo(ProvenanceFailureReason.AMBIGUOUS);
			assertThat(check.startOffset()).isNull();
			assertThat(check.endOffset()).isNull();
		}

		@Test
		@DisplayName("실패하면 offset 을 남기지 않는다 (ck_provenance_consistency)")
		void failureCarriesNoOffsets() {
			ProvenanceCheck check = verifier.verify("아무 내용입니다.", "없는 문장을 인용했습니다.");

			assertThat(check.valid()).isFalse();
			assertThat(check.startOffset()).isNull();
			assertThat(check.endOffset()).isNull();
			assertThat(check.failureReason()).isNotNull();
		}
	}
}
