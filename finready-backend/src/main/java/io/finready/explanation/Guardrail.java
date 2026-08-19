package io.finready.explanation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F06 재설명 Guardrail (TRD D-04).
 *
 * <p>LLM 이 만든 재설명이 <b>검수 범위를 벗어났는지</b>만 본다. 좋은 설명인지 판단하지 않는다.
 * 위반이면 호출부가 1회 재생성하고, 재실패하면 검수된 {@code fallbackPlainExplanation} 으로 대체한다.
 *
 * <p>LLM 자가검증을 쓰지 않은 이유는 비용이 아니라 <b>재현성</b>이다. 금칙어 검사는 결정적이라
 * 테스트로 고정되고 실행마다 흔들리지 않는다. Coverage 실측에서 경계 판정이 실행마다
 * 뒤집히는 것을 이미 봤는데, 그 성질을 안전장치에 넣을 수는 없다.
 */
@Component
public class Guardrail {

	/**
	 * 문맥과 무관하게 위반이다. 부정형으로 쓸 일이 없는 표현들이다.
	 *
	 * <p>어간만 담는다 — "드물다/드물고/드물어서"를 모두 잡으려면 "드물"이어야 한다.
	 */
	private static final List<String> ALWAYS_BANNED = List.of(
			// 위험 축소
			"사실상", "거의 없", "드물", "걱정 안 하셔도", "걱정하지 않으셔도", "걱정하실 필요",
			"손해 볼 일", "크게 문제", "별일 없",
			// 가입 권유
			"추천", "가입하시는 게", "가입하시는 것", "좋은 기회", "지금이 적기", "지금이 기회");

	/**
	 * <b>이 목록이 이 클래스의 핵심이다.</b>
	 *
	 * <p>"보장"·"확정"·"안전"을 단순 {@code contains} 로 막으면 <b>맞는 설명이 걸린다.</b>
	 * 이 상품의 검수된 사실 자체가 부정형이기 때문이다 —
	 * <i>"원금이 보장되지 않습니다"</i>, <i>"손실이 확정되지 않습니다"</i> 가 정확히 그 문장이다.
	 * 금칙어 목록을 그대로 구현했다면 <b>가장 정확한 재설명이 매번 fallback 으로 떨어졌을 것이다.</b>
	 *
	 * <p>그래서 이 단어들은 <b>뒤따르는 절에 부정 표현이 없을 때만</b> 위반으로 센다.
	 */
	private static final List<String> BANNED_UNLESS_NEGATED = List.of(
			"보장", "확정", "안전", "원금은 지켜", "원금이 지켜", "문제없", "문제 없");

	/**
	 * 부정만 찾으면 부족하다. 한국어는 <b>거리를 두는 표현</b>으로도 같은 뜻을 만든다.
	 *
	 * <p>{@code 별개}·{@code 다르}·{@code 무관} 은 실측에서 추가했다 (2026-08-19).
	 * R01 재설명이 두 시도 모두 이 문장에서 걸려 fallback 으로 떨어졌다:
	 *
	 * <pre>"중간에 팔지 않는 것과 원금 보장은 별개입니다."</pre>
	 *
	 * "보장은 별개다"는 <b>원금이 안 지켜진다</b>는 뜻이다. 정확하고 조심스러운 문장인데
	 * 부정어가 없다는 이유로 위반이 됐다. 목록을 늘릴 때는 반드시 실제 생성물에서 근거를
	 * 확보할 것 — 짐작으로 넣으면 이번엔 위반을 놓치는 쪽으로 틀린다.
	 */
	private static final List<String> NEGATION_MARKERS =
			List.of("않", "아니", "없", "못", "안 ", "말고",
					// 르 불규칙이라 "다르"만으로는 "다릅니다"를 못 잡는다. 활용형을 함께 둔다
					"별개", "다르", "다릅", "다른", "달라", "무관");

	/**
	 * 부정 표현을 찾는 범위. 절 경계(구두점)에서 끊으므로 대개 이보다 짧게 끝난다.
	 *
	 * <p>경계에서 끊지 않으면 <i>"원금이 보장됩니다. 손실은 없습니다"</i> 의 "없"이
	 * 앞 문장의 "보장"을 면제해 버린다 — 위반을 놓치는 쪽이라 더 위험하다.
	 */
	private static final int NEGATION_WINDOW = 14;

	private static final String CLAUSE_BOUNDARIES = ".!?\n,;·";

	/** 소수점·자릿수 구분 쉼표를 포함한 숫자 토큰 */
	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

	/**
	 * @param explanation 검사할 재설명
	 * @param evidence    허용된 근거 — {@code risk.fact} + {@code risk.sourceText} 를 이어 붙인 것.
	 *                    여기 없는 숫자는 LLM 이 지어낸 것이다
	 * @return 위반 목록과 <b>무엇에 걸렸는지</b>. 비어 있으면 통과다
	 */
	public Inspection inspect(String explanation, String evidence) {
		List<GuardrailViolation> violations = new ArrayList<>(2);
		List<String> matches = new ArrayList<>(2);
		if (explanation == null || explanation.isBlank()) {
			return new Inspection(violations, matches);   // 빈 응답은 파싱 단계에서 이미 걸린다
		}

		String mitigating = firstMitigatingExpression(explanation);
		if (mitigating != null) {
			violations.add(GuardrailViolation.MITIGATING_EXPRESSION);
			matches.add(mitigating);
		}
		String number = firstUnsupportedNumber(explanation, evidence);
		if (number != null) {
			violations.add(GuardrailViolation.UNSUPPORTED_NUMBER);
			matches.add(number);
		}
		return new Inspection(violations, matches);
	}

	/**
	 * 검사 결과.
	 *
	 * <p>{@code matches} 는 <b>계약에 없다</b> — 응답에 싣지 않고 로그로만 쓴다.
	 * 이게 없으면 "무엇 때문에 fallback 으로 떨어졌는지"를 알 방법이 raw_response 를
	 * 직접 뒤지는 것뿐이라, 목록을 조정할 근거를 모을 수 없다.
	 *
	 * @param matches 걸린 표현(또는 숫자). 위반당 하나씩, 첫 번째 것만 담는다
	 */
	public record Inspection(List<GuardrailViolation> violations, List<String> matches) {

		public boolean passed() {
			return violations.isEmpty();
		}
	}

	// ------------------------------------------------------------------

	/** @return 걸린 표현. 없으면 null */
	private String firstMitigatingExpression(String explanation) {
		for (String banned : ALWAYS_BANNED) {
			if (explanation.contains(banned)) {
				return banned;
			}
		}
		for (String banned : BANNED_UNLESS_NEGATED) {
			int at = explanation.indexOf(banned);
			while (at >= 0) {
				if (!isNegatedAfter(explanation, at + banned.length())) {
					// 어느 문맥에서 걸렸는지가 목록 조정의 근거다 — 단어만으로는 판단할 수 없다
					return banned + " (" + snippetAround(explanation, at, banned.length()) + ")";
				}
				at = explanation.indexOf(banned, at + banned.length());
			}
		}
		return null;
	}

	/** 걸린 지점 앞뒤를 잘라 로그에 남긴다 */
	private String snippetAround(String text, int at, int length) {
		int from = Math.max(0, at - 10);
		int to = Math.min(text.length(), at + length + NEGATION_WINDOW);
		return text.substring(from, to).replace('\n', ' ');
	}

	/** 같은 절 안에서만 부정 표현을 찾는다 */
	private boolean isNegatedAfter(String text, int from) {
		int limit = Math.min(text.length(), from + NEGATION_WINDOW);
		int end = from;
		while (end < limit && CLAUSE_BOUNDARIES.indexOf(text.charAt(end)) < 0) {
			end++;
		}
		String clause = text.substring(from, end);
		return NEGATION_MARKERS.stream().anyMatch(clause::contains);
	}

	/**
	 * 재설명의 숫자가 모두 근거에 있는지 본다.
	 *
	 * <p>값으로 비교한다 — {@code 0.80} 과 {@code 0.8} 은 같은 숫자다. 문자열로 비교하면
	 * 표기만 다른 정확한 숫자가 "지어낸 숫자"로 걸린다.
	 *
	 * @return 근거에 없는 첫 숫자. 없으면 null
	 */
	private String firstUnsupportedNumber(String explanation, String evidence) {
		Set<BigDecimal> allowed = numbersIn(evidence);
		for (BigDecimal used : numbersIn(explanation)) {
			if (!allowed.contains(used)) {
				return used.toPlainString();
			}
		}
		return null;
	}

	private Set<BigDecimal> numbersIn(String text) {
		Set<BigDecimal> numbers = new LinkedHashSet<>();
		if (text == null) {
			return numbers;
		}
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			try {
				numbers.add(new BigDecimal(matcher.group().replace(",", "")).stripTrailingZeros());
			} catch (NumberFormatException ignored) {
				// 숫자로 읽히지 않는 토큰은 비교 대상이 아니다
			}
		}
		return numbers;
	}
}
