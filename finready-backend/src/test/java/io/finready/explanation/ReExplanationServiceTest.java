package io.finready.explanation;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.common.GenerationSource;
import io.finready.common.StateMachine;
import io.finready.product.CoveragePolicy;
import io.finready.product.CustomerProfileRepository;
import io.finready.product.ProductRepository;
import io.finready.product.ProductRisk;
import io.finready.product.ProductRiskRepository;
import io.finready.session.ConsultationSession;
import io.finready.session.ConsultationSessionRepository;
import io.finready.session.SessionStatus;
import io.finready.understanding.NextAction;
import io.finready.understanding.UnderstandingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F06 오케스트레이션. LLM 없이 돈다 — 생성 포트가 목이다.
 *
 * <p>Guardrail 자체의 판정은 {@code GuardrailTest} 가 갖는다.
 * 여기서는 <b>멱등·재생성 횟수·fallback 전환</b>만 본다.
 */
@DisplayName("ReExplanationService — F06 재설명")
class ReExplanationServiceTest {

	private static final String SESSION_ID = "S-1";
	private static final String RISK_ID = "R01";
	private static final String CUSTOMER_ANSWER = "중간에 팔지만 않으면 원금은 받는 걸로 알고 있습니다.";

	private final ConsultationSessionRepository sessionRepository = mock(ConsultationSessionRepository.class);
	private final ProductRepository productRepository = mock(ProductRepository.class);
	private final ProductRiskRepository productRiskRepository = mock(ProductRiskRepository.class);
	private final CustomerProfileRepository customerProfileRepository = mock(CustomerProfileRepository.class);
	private final ReExplanationRepository reExplanationRepository = mock(ReExplanationRepository.class);
	private final ReExplanationGenerator generator = mock(ReExplanationGenerator.class);
	private final ReExplanationWriter writer = mock(ReExplanationWriter.class);
	private final UnderstandingService understandingService = mock(UnderstandingService.class);

	private ReExplanationService service;

	@BeforeEach
	void setUp() {
		service = new ReExplanationService(
				sessionRepository, productRepository, productRiskRepository, customerProfileRepository,
				reExplanationRepository, generator, new Guardrail(), writer,
				understandingService, new StateMachine());

		when(sessionRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(session(SessionStatus.UNDERSTANDING_IN_PROGRESS)));
		when(productRiskRepository.findByProductIdOrderByRiskIdAsc("PROD_A")).thenReturn(List.of(risk()));
		when(productRepository.findById(anyString())).thenReturn(Optional.empty());
		when(customerProfileRepository.findById(anyString())).thenReturn(Optional.empty());
		when(reExplanationRepository.findFirstBySessionIdAndRiskIdOrderByIdDesc(SESSION_ID, RISK_ID))
				.thenReturn(Optional.empty());
		when(understandingService.requireMisunderstoodAnswer(SESSION_ID, RISK_ID))
				.thenReturn(CUSTOMER_ANSWER);
		when(understandingService.issueRecheckQuestion(SESSION_ID, RISK_ID))
				.thenReturn(new UnderstandingService.IssuedQuestion("후속 질문", GenerationSource.FALLBACK));
		// 저장은 넘어온 엔티티를 그대로 돌려준다 — id 채번은 이 테스트의 관심사가 아니다
		when(writer.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Nested
	@DisplayName("정상 생성")
	class Generated {

		@Test
		@DisplayName("Guardrail 을 통과하면 LLM 결과를 그대로 쓴다")
		void passesGuardrail() {
			when(generator.explain(any(), any(), any(), any(), any(), any(), any()))
					.thenReturn("원금이 보장되지 않습니다. 기준가격의 65% 미만이면 손실이 발생합니다.");

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.source()).isEqualTo(GenerationSource.LLM);
			assertThat(response.guardrail().retried()).isFalse();
			assertThat(response.guardrail().violations()).isEmpty();
			verify(generator, times(1)).explain(any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("응답에 S06 좌·우가 모두 실린다 — 고객 답변과 실제 사실")
		void carriesBothSidesOfTheScreen() {
			when(generator.explain(any(), any(), any(), any(), any(), any(), any()))
					.thenReturn("원금이 보장되지 않습니다.");

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.customerAnswer()).isEqualTo(CUSTOMER_ANSWER);
			assertThat(response.riskFact()).isEqualTo("원금 손실이 발생할 수 있다. 기준은 65%다.");
			assertThat(response.sourcePage()).isEqualTo(11);
		}

		@Test
		@DisplayName("재설명 후 nextAction 은 항상 RECHECK 다")
		void nextActionIsAlwaysRecheck() {
			when(generator.explain(any(), any(), any(), any(), any(), any(), any()))
					.thenReturn("원금이 보장되지 않습니다.");

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.nextAction()).isEqualTo(NextAction.RECHECK);
			assertThat(response.recheckQuestion()).isEqualTo("후속 질문");
		}
	}

	@Nested
	@DisplayName("Guardrail 위반")
	class Violations {

		@Test
		@DisplayName("위반이면 1회만 재생성한다")
		void retriesOnce() {
			when(generator.explain(any(), any(), any(), any(), any(), any(), any()))
					.thenReturn("사실상 원금은 지켜집니다.");

			service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			// TRD §7.1 "1회만" — 무한 재생성이 되면 요금과 지연이 통제되지 않는다
			verify(generator, times(2)).explain(any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("재생성도 실패하면 검수된 문장으로 대체하고 FALLBACK 으로 표시한다")
		void fallsBackToVerifiedText() {
			when(generator.explain(any(), any(), any(), any(), any(), any(), any()))
					.thenReturn("사실상 원금은 지켜집니다.");

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.source()).isEqualTo(GenerationSource.FALLBACK);
			assertThat(response.explanation()).isEqualTo("검수된 쉬운 설명");
			assertThat(response.guardrail().retried()).isTrue();
			assertThat(response.guardrail().violations())
					.contains(GuardrailViolation.MITIGATING_EXPRESSION);
		}

		@Test
		@DisplayName("재생성이 통과하면 그 결과를 쓴다 — retried 만 true 다")
		void secondAttemptCanPass() {
			when(generator.explain(any(), any(), any(), any(), any(), any(), any()))
					.thenReturn("사실상 원금은 지켜집니다.")
					.thenReturn("원금이 보장되지 않습니다.");

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.source()).isEqualTo(GenerationSource.LLM);
			assertThat(response.explanation()).isEqualTo("원금이 보장되지 않습니다.");
			assertThat(response.guardrail().retried()).isTrue();
		}

		@Test
		@DisplayName("생성 자체가 실패해도 흐름을 막지 않는다 — 검수 문장이 있다")
		void generatorFailureFallsBack() {
			when(generator.explain(any(), any(), any(), any(), any(), any(), any()))
					.thenThrow(new IllegalStateException("LLM 없음"));

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.source()).isEqualTo(GenerationSource.FALLBACK);
			assertThat(response.explanation()).isEqualTo("검수된 쉬운 설명");
		}
	}

	@Nested
	@DisplayName("멱등")
	class Idempotency {

		@Test
		@DisplayName("이미 생성된 재설명이 있으면 LLM 을 부르지 않는다")
		void reusesStored() {
			ReExplanation stored = new ReExplanation(SESSION_ID, RISK_ID, 11, "출처 문장",
					"이미 저장된 설명", GenerationSource.LLM, false, null);
			when(reExplanationRepository.findFirstBySessionIdAndRiskIdOrderByIdDesc(SESSION_ID, RISK_ID))
					.thenReturn(Optional.of(stored));

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.explanation()).isEqualTo("이미 저장된 설명");
			// 새로고침이 요금을 다시 물면 안 된다
			verify(generator, never()).explain(any(), any(), any(), any(), any(), any(), any());
			verify(writer, never()).save(any());
		}

		@Test
		@DisplayName("저장된 위반 목록을 다시 읽어 응답에 싣는다")
		void restoresStoredViolations() {
			ReExplanation stored = new ReExplanation(SESSION_ID, RISK_ID, 11, "출처 문장",
					"검수된 쉬운 설명", GenerationSource.FALLBACK, true,
					"MITIGATING_EXPRESSION,UNSUPPORTED_NUMBER");
			when(reExplanationRepository.findFirstBySessionIdAndRiskIdOrderByIdDesc(SESSION_ID, RISK_ID))
					.thenReturn(Optional.of(stored));

			ReExplanationResponse response = service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID));

			assertThat(response.guardrail().violations()).containsExactly(
					GuardrailViolation.MITIGATING_EXPRESSION, GuardrailViolation.UNSUPPORTED_NUMBER);
		}
	}

	@Nested
	@DisplayName("진입 조건")
	class Preconditions {

		@Test
		@DisplayName("MISUNDERSTOOD 가 아니면 understanding 모듈이 거절한다")
		void rejectsWhenNotMisunderstood() {
			when(understandingService.requireMisunderstoodAnswer(SESSION_ID, RISK_ID))
					.thenThrow(new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
							"재설명이 필요한 항목이 아닙니다.", RISK_ID));

			ApiException thrown = catchThrowableOfType(ApiException.class,
					() -> service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID)));

			assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
			verify(generator, never()).explain(any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("종료된 세션은 수정할 수 없다")
		void rejectsClosedSession() {
			when(sessionRepository.findById(SESSION_ID))
					.thenReturn(Optional.of(session(SessionStatus.SESSION_CLOSED_BY_STAFF)));

			ApiException thrown = catchThrowableOfType(ApiException.class,
					() -> service.reExplain(SESSION_ID, new ReExplainRequest(RISK_ID)));

			assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
		}

		@Test
		@DisplayName("riskId 가 없으면 400 이다")
		void rejectsMissingRiskId() {
			ApiException thrown = catchThrowableOfType(ApiException.class,
					() -> service.reExplain(SESSION_ID, new ReExplainRequest(null)));

			assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_REQUEST);
		}
	}

	// ------------------------------------------------------------------

	private ConsultationSession session(SessionStatus status) {
		ConsultationSession session =
				new ConsultationSession(SESSION_ID, "PROD_A", "CUST_A", "A-2026-08-12-01");
		if (status != SessionStatus.DRAFT) {
			overwrite(session, "status", status);
		}
		return session;
	}

	private ProductRisk risk() {
		return new ProductRisk("PROD_A", RISK_ID, "PRINCIPAL_LOSS", "원금 손실 가능성",
				"원금 손실이 발생할 수 있다. 기준은 65%다.",
				CoveragePolicy.GATE_REQUIRED, true, 11, "최초기준가격의 65% 미만이면 원금 손실이 발생합니다.",
				"질문", "후속 질문", "검수된 쉬운 설명",
				OffsetDateTime.parse("2026-08-12T00:00:00Z"), "TEAM");
	}

	private void overwrite(Object target, String fieldName, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("테스트 픽스처를 만들지 못했다: " + fieldName, ex);
		}
	}
}
