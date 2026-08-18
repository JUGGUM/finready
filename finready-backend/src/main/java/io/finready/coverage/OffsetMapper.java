package io.finready.coverage;

import java.util.Arrays;

/**
 * 원문을 공백 정규화하면서 <b>정규화 인덱스 → 원문 인덱스</b> 매핑을 함께 만든다 (TRD §8).
 *
 * <p>왜 매핑이 필요한가. LLM 이 인용한 evidence 는 원문과 공백이 다르기 마련이라
 * 원문에서 그대로 찾으면 자주 실패한다. 그래서 양쪽을 정규화해 비교하는데, 이때 나온
 * <b>정규화 문자열의 인덱스를 그대로 응답에 쓰면 안 된다.</b> 공백이 병합된 만큼 원문과
 * 위치가 어긋나 프론트 하이라이트가 밀린다(openapi Evidence.startOffset).
 *
 * <p>인덱스 단위는 <b>UTF-16 code unit</b> 이다. {@code String.charAt} / {@code length()} 와
 * 같은 기준이며 {@code consultation_revision.char_count} 와도 같다. 서로게이트 쌍은 두 code
 * unit 모두 비공백이라 각각 매핑되므로, 이모지가 섞여도 경계가 어긋나지 않는다.
 *
 * <p>이 클래스는 LLM 을 모른다. 규칙 4("LLM 이 반환한 offset 을 쓰지 않는다")를 지키는
 * 재계산 지점이 여기다.
 */
public final class OffsetMapper {

	private final String normalized;

	/** normalized 의 각 code unit 이 원문 어디에서 왔는지 */
	private final int[] toOriginal;

	private OffsetMapper(String normalized, int[] toOriginal) {
		this.normalized = normalized;
		this.toOriginal = toOriginal;
	}

	/**
	 * 연속 공백을 하나로 접고 앞뒤 공백을 버린다.
	 *
	 * <p>병합된 공백은 <b>뒤따르는 비공백 문자의 원문 인덱스</b>를 가리킨다. 정규화된
	 * evidence 는 항상 비공백으로 시작·종료하므로 매칭 경계가 공백에 걸리는 일이 없고,
	 * 따라서 이 근사는 결과에 영향을 주지 않는다.
	 */
	public static OffsetMapper of(String raw) {
		StringBuilder builder = new StringBuilder(raw.length());
		int[] map = new int[raw.length()];
		boolean spacePending = false;

		for (int i = 0; i < raw.length(); i++) {
			char current = raw.charAt(i);

			if (Character.isWhitespace(current)) {
				// 선행 공백은 접지 않고 버린다. builder 가 비어 있으면 아직 본문 전이다
				spacePending = !builder.isEmpty();
				continue;
			}

			if (spacePending) {
				map[builder.length()] = i;
				builder.append(' ');
				spacePending = false;
			}

			map[builder.length()] = i;
			builder.append(current);
		}

		return new OffsetMapper(builder.toString(), Arrays.copyOf(map, builder.length()));
	}

	public String normalized() {
		return normalized;
	}

	/**
	 * 정규화 문자열에서 {@code needle} 이 나타나는 횟수. 0·1·2+ 를 구분해야 하므로
	 * "몇 번째"가 아니라 개수를 센다 — 2회 이상이면 AMBIGUOUS 이며 예외 없이
	 * provenance 실패다(TRD §8.4).
	 *
	 * <p>세 번째 이상은 세지 않고 2에서 멈춘다. 판정에 필요한 건 "1인가 아닌가"뿐이다.
	 */
	public int countOccurrences(String needle) {
		if (needle.isEmpty()) {
			return 0;
		}
		int count = 0;
		int from = 0;
		while (true) {
			int found = normalized.indexOf(needle, from);
			if (found < 0) {
				return count;
			}
			count++;
			if (count >= 2) {
				return count;
			}
			from = found + 1;   // 겹치는 반복(예: "가가가" 안의 "가가")도 별개로 센다
		}
	}

	/** 정규화 인덱스 구간 → 원문 구간. end 는 exclusive */
	public OriginalRange toOriginalRange(String needle) {
		int start = normalized.indexOf(needle);
		if (start < 0) {
			throw new IllegalStateException("정규화 문자열에 없는 구간을 매핑하려 했다");
		}
		int lastCodeUnit = start + needle.length() - 1;
		return new OriginalRange(toOriginal[start], toOriginal[lastCodeUnit] + 1);
	}

	/** 원문 기준 UTF-16 code unit 구간. {@code end} 는 exclusive */
	public record OriginalRange(int start, int end) {
	}
}
