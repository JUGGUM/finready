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

	private static final List<String> NEGATION_MARKERS = List.of("않", "아니", "없", "못", "안 ", "말고");

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
	 * @return 위반 목록. 비어 있으면 통과다
	 */
	public List<GuardrailViolation> inspect(String explanation, String evidence) {
		List<GuardrailViolation> violations = new ArrayList<>(2);
		if (explanation == null || explanation.isBlank()) {
			return violations;   // 빈 응답은 파싱 단계에서 이미 걸린다
		}
		if (hasMitigatingExpression(explanation)) {
			violations.add(GuardrailViolation.MITIGATING_EXPRESSION);
		}
		if (hasUnsupportedNumber(explanation, evidence)) {
			violations.add(GuardrailViolation.UNSUPPORTED_NUMBER);
		}
		return violations;
	}

	// ------------------------------------------------------------------

	private boolean hasMitigatingExpression(String explanation) {
		for (String banned : ALWAYS_BANNED) {
			if (explanation.contains(banned)) {
				return true;
			}
		}
		for (String banned : BANNED_UNLESS_NEGATED) {
			int at = explanation.indexOf(banned);
			while (at >= 0) {
				if (!isNegatedAfter(explanation, at + banned.length())) {
					return true;
				}
				at = explanation.indexOf(banned, at + banned.length());
			}
		}
		return false;
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
	 */
	private boolean hasUnsupportedNumber(String explanation, String evidence) {
		Set<BigDecimal> allowed = numbersIn(evidence);
		for (BigDecimal used : numbersIn(explanation)) {
			if (!allowed.contains(used)) {
				return true;
			}
		}
		return false;
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
