package io.finready.explanation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F06 Guardrail (TRD D-04).
 *
 * <p>이 테스트의 존재 이유는 <b>부정형 오탐</b>이다. 금칙어 목록에 "보장"·"확정"·"안전"이
 * 들어가는데, 이 상품의 검수된 사실 자체가 부정형이다 — <i>"원금이 보장되지 않습니다"</i>.
 * 단순 {@code contains} 로 구현하면 <b>가장 정확한 재설명이 매번 fallback 으로 떨어진다.</b>
 * 나중에 "왜 이렇게 복잡하지"라며 단순화하면 여기가 깨진다.
 */
@DisplayName("Guardrail — 재설명 검수 범위 이탈 검사")
class GuardrailTest {

	private final Guardrail guardrail = new Guardrail();

	/** R01 의 실제 시드 값에 해당하는 근거 */
	private static final String EVIDENCE = """
			만기평가일에 3개 기초자산 중 하나라도 최초기준가격의 65% 미만이면 원금 손실이 발생한다.
			낙인 배리어가 없다는 것은 손실 요건의 판단 방식이 다르다는 뜻이지 원금 보장을 의미하지 않는다.
			총 보수는 연 0.80%다.
			""";

	@Nested
	@DisplayName("완화 표현 — 부정형은 통과해야 한다")
	class Negation {

		@Test
		@DisplayName("'보장되지 않습니다'는 위반이 아니다 — 이게 정확히 맞는 설명이다")
		void negatedGuaranteeIsNotViolation() {
			String explanation = "이 상품은 원금이 보장되지 않습니다. "
					+ "만기에 기초자산이 기준가격의 65% 미만이면 원금 손실이 발생합니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations()).isEmpty();
		}

		@Test
		@DisplayName("'손실이 확정되지 않는다'도 위반이 아니다")
		void negatedFixedIsNotViolation() {
			String explanation = "지수가 잠시 내려가도 그것만으로 손실이 확정되지 않습니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations()).isEmpty();
		}

		@Test
		@DisplayName("'원금 보장을 의미하지 않는다'도 위반이 아니다")
		void negatedFurtherAwayIsNotViolation() {
			String explanation = "낙인이 없다는 것은 원금 보장을 의미하지 않습니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations()).isEmpty();
		}

		@Test
		@DisplayName("긍정형 '보장됩니다'는 위반이다")
		void affirmativeGuaranteeIsViolation() {
			String explanation = "이 상품은 원금이 보장됩니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
					.containsExactly(GuardrailViolation.MITIGATING_EXPRESSION);
		}

		/**
		 * 실측에서 나온 문장이다 (2026-08-19). R01 재설명이 두 시도 모두 여기서 걸려
		 * fallback 으로 떨어졌다 — "보장은 별개다"가 부정어 없이 부정을 뜻하기 때문이다.
		 */
		@Test
		@DisplayName("'원금 보장은 별개입니다'는 위반이 아니다 — 실제 오탐 사례")
		void distancingExpressionIsNotViolation() {
			String explanation = "만기까지 보유하셔도 원금을 받지 못하실 수 있습니다. "
					+ "중간에 팔지 않는 것과 원금 보장은 별개입니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations()).isEmpty();
		}

		@Test
		@DisplayName("'보장과 다릅니다'·'보장과 무관합니다'도 위반이 아니다")
		void otherDistancingFormsAreNotViolations() {
			assertThat(guardrail.inspect("이것은 원금 보장과 다릅니다.", EVIDENCE).violations()).isEmpty();
			assertThat(guardrail.inspect("낙인 구조는 원금 보장과 무관합니다.", EVIDENCE).violations()).isEmpty();
		}

		@Test
		@DisplayName("뒷문장의 부정어가 앞문장을 면제하지 않는다")
		void negationDoesNotLeakAcrossClauses() {
			// 절 경계에서 끊지 않으면 뒤의 "없"이 앞의 "보장"을 면제해 위반을 놓친다.
			// 놓치는 쪽이 더 위험하다 — 고객이 잘못된 문장을 그대로 읽는다
			String explanation = "원금은 보장됩니다. 손실이 날 일은 없습니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
					.contains(GuardrailViolation.MITIGATING_EXPRESSION);
		}
	}

	@Nested
	@DisplayName("완화 표현 — 문맥과 무관하게 걸리는 것")
	class AlwaysBanned {

		@Test
		@DisplayName("'사실상'은 부정형이어도 위반이다")
		void practicallyIsAlwaysViolation() {
			String explanation = "노낙인이라 사실상 원금은 지켜진다고 보셔도 됩니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
					.contains(GuardrailViolation.MITIGATING_EXPRESSION);
		}

		@Test
		@DisplayName("'거의 없다'는 확률을 낮춰 말하는 표현이라 위반이다")
		void rareIsViolation() {
			String explanation = "세 지수가 동시에 반토막 날 가능성은 거의 없습니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
					.contains(GuardrailViolation.MITIGATING_EXPRESSION);
		}

		@Test
		@DisplayName("가입 권유는 위반이다 — 이 도구는 판매 도구가 아니다")
		void recommendationIsViolation() {
			String explanation = "지금 가입하시는 게 좋은 기회입니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
					.contains(GuardrailViolation.MITIGATING_EXPRESSION);
		}
	}

	@Nested
	@DisplayName("근거 없는 숫자")
	class Numbers {

		@Test
		@DisplayName("근거에 있는 숫자는 통과한다")
		void supportedNumberPasses() {
			String explanation = "기초자산이 최초기준가격의 65% 미만이면 손실이 발생합니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations()).isEmpty();
		}

		@Test
		@DisplayName("근거에 없는 숫자는 위반이다")
		void unsupportedNumberIsViolation() {
			String explanation = "최대 손실률은 마이너스 100%입니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
					.containsExactly(GuardrailViolation.UNSUPPORTED_NUMBER);
		}

		@Test
		@DisplayName("표기만 다른 같은 숫자는 통과한다 — 0.80 과 0.8 은 같다")
		void equivalentNumberFormatPasses() {
			// 문자열로 비교하면 정확한 숫자가 '지어낸 숫자'로 걸린다
			String explanation = "총 보수는 연 0.8%입니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations()).isEmpty();
		}

		@Test
		@DisplayName("자릿수 쉼표가 있어도 값으로 비교한다")
		void thousandSeparatorIsNormalized() {
			assertThat(guardrail.inspect("1,000만원을 넣으셨다면", "1000만원 기준").violations()).isEmpty();
		}

		/**
		 * 실측 사례 (2026-08-19). 모델이 고객의 오해("한 10퍼센트, 20퍼센트")를 반박하려고
		 * 그 숫자를 인용했다.
		 *
		 * <p><b>이건 의도된 동작이다.</b> 고객 답변을 허용 근거에 넣으면 모델이 고객의 틀린
		 * 숫자를 사실로 단언해도 막지 못한다 — fallback 은 안전하지만 틀린 숫자는 안전하지 않다.
		 * 대신 {@code reexplain-v2} 프롬프트가 "고객이 말한 숫자를 되풀이하지 말라"고 지시한다.
		 */
		@Test
		@DisplayName("고객이 말한 숫자를 인용해도 위반이다 — 근거에 없으면 없는 것이다")
		void quotingCustomerNumberIsStillViolation() {
			String explanation = "이 상품의 손실은 10~20%로 제한되지 않습니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
					.contains(GuardrailViolation.UNSUPPORTED_NUMBER);
		}

		@Test
		@DisplayName("숫자를 아예 쓰지 않으면 위반이 아니다")
		void noNumberIsNotViolation() {
			String explanation = "원금이 보장되지 않으며 손실이 발생할 수 있습니다.";

			assertThat(guardrail.inspect(explanation, EVIDENCE).violations()).isEmpty();
		}
	}

	@Test
	@DisplayName("두 위반이 동시에 잡힌다")
	void bothViolations() {
		String explanation = "손실 확률은 사실상 5% 수준입니다.";

		assertThat(guardrail.inspect(explanation, EVIDENCE).violations())
				.containsExactlyInAnyOrder(
						GuardrailViolation.MITIGATING_EXPRESSION,
						GuardrailViolation.UNSUPPORTED_NUMBER);
	}

	@Test
	@DisplayName("빈 응답은 파싱 단계가 잡는다 — 여기서는 위반으로 세지 않는다")
	void blankIsNotInspected() {
		assertThat(guardrail.inspect("  ", EVIDENCE).violations()).isEmpty();
		assertThat(guardrail.inspect(null, EVIDENCE).violations()).isEmpty();
	}

	/**
	 * 무엇에 걸렸는지 남기지 않으면 목록을 조정할 근거가 없다. 실제로 R01 재설명이 fallback 으로
	 * 떨어졌는데 원인을 알 수 없어 이 기능을 넣었다 (2026-08-19).
	 */
	@Nested
	@DisplayName("무엇에 걸렸는지 남긴다")
	class Diagnostics {

		@Test
		@DisplayName("걸린 표현과 그 앞뒤 문맥을 함께 준다")
		void reportsMatchedExpressionWithContext() {
			Guardrail.Inspection inspection =
					guardrail.inspect("노낙인이라 사실상 안심하셔도 됩니다.", EVIDENCE);

			assertThat(inspection.passed()).isFalse();
			assertThat(inspection.matches()).singleElement().asString().contains("사실상");
		}

		@Test
		@DisplayName("부정형 예외에 걸린 경우 문맥이 함께 나온다 — 단어만으로는 판단할 수 없다")
		void reportsContextForNegationCandidates() {
			Guardrail.Inspection inspection =
					guardrail.inspect("원금이 보장된다고 이해하셨다면, 그것은 사실과 다릅니다.", EVIDENCE);

			assertThat(inspection.matches()).singleElement().asString()
					.contains("보장")
					.contains("이해하셨다면");
		}

		@Test
		@DisplayName("근거에 없는 숫자는 그 숫자를 준다")
		void reportsUnsupportedNumber() {
			Guardrail.Inspection inspection = guardrail.inspect("최대 손실은 -100%입니다.", EVIDENCE);

			assertThat(inspection.matches()).containsExactly("100");
		}

		@Test
		@DisplayName("통과하면 아무것도 남기지 않는다")
		void passingLeavesNothing() {
			Guardrail.Inspection inspection =
					guardrail.inspect("원금이 보장되지 않습니다.", EVIDENCE);

			assertThat(inspection.passed()).isTrue();
			assertThat(inspection.matches()).isEmpty();
		}
	}
}
