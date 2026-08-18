package io.finready.coverage;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.common.StateMachine;
import io.finready.product.CoveragePolicy;
import io.finready.product.ProductRisk;
import io.finready.product.ProductRiskRepository;
import io.finready.session.ConsultationRevision;
import io.finready.session.ConsultationRevisionRepository;
import io.finready.session.ConsultationSession;
import io.finready.session.ConsultationSessionRepository;
import io.finready.session.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03 오케스트레이션 검증. <b>LLM 없이 돈다</b> — 두 포트가 목이라 모델이 정해지기 전에도
 * 순서·대상 선정·오류 매핑을 고정할 수 있다.
 *
 * <p>판정 자체는 여기서 다시 보지 않는다. 결정표는 {@code CoverageStatusResolverTest},
 * Gate 는 {@code GateEvaluatorTest}, 근거 대조는 {@code ProvenanceVerifierTest} 가 갖는다.
 */
@DisplayName("CoverageAnalysisService — 분석 오케스트레이션")
class CoverageAnalysisServiceTest {

	private static final String SESSION_ID = "S-1";
	private static final Long REVISION_ID = 10L;
	private static final String TRANSCRIPT =
			"이 상품은 원금이 보장되지 않습니다. 최대 손실률은 마이너스 100%입니다. 조기상환은 6개월마다 평가합니다.";

	private final ConsultationSessionRepository sessionRepository = mock(ConsultationSessionRepository.class);
	private final ConsultationRevisionRepository revisionRepository = mock(ConsultationRevisionRepository.class);
	private final ProductRiskRepository productRiskRepository = mock(ProductRiskRepository.class);
	private final CoverageResultRepository coverageResultRepository = mock(CoverageResultRepository.class);
	private final GateOverrideRepository gateOverrideRepository = mock(GateOverrideRepository.class);
	private final CoverageClassifier classifier = mock(CoverageClassifier.class);
	private final SemanticVerifier semanticVerifier = mock(SemanticVerifier.class);
	private final CoverageWriter writer = mock(CoverageWriter.class);

	private CoverageAnalysisService service;

	@BeforeEach
	void setUp() {
		service = new CoverageAnalysisService(
				sessionRepository, revisionRepository, productRiskRepository,
				coverageResultRepository, gateOverrideRepository,
				classifier, semanticVerifier,
				new ProvenanceVerifier(15, 300),
				new CoverageResultFactory(new CoverageStatusResolver()),
				new GateEvaluator(), writer, new StateMachine());

		when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.DRAFT)));
		when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
				.thenReturn(Optional.of(revision()));
		when(productRiskRepository.findByProductIdOrderByRiskIdAsc("PROD_A")).thenReturn(risks());
		when(coverageResultRepository.findByRevisionIdOrderByRiskIdAsc(REVISION_ID))
				.thenReturn(List.of());
		when(gateOverrideRepository.findBySessionIdOrderByRiskIdAsc(SESSION_ID)).thenReturn(List.of());
		when(writer.saveAnalysis(anyString(), anyList(), any())).thenAnswer(
				invocation -> invocation.getArgument(2));
		when(writer.saveOverrides(anyString(), anyList(), any())).thenAnswer(
				invocation -> invocation.getArgument(2));
	}

	@Nested
	@DisplayName("정상 분석")
	class HappyPath {

		@Test
		@DisplayName("설명된 Risk 는 EXPLAINED 로 저장되고 Gate 가 열린다")
		void allExplainedOpensGate() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."),
					verdict("R05", CoverageStatus.EXPLAINED, "조기상환은 6개월마다 평가합니다."));
			// R05 는 WARN_ONLY 비-CONTRADICTED 라 Verifier 대상이 아니다
			verifierReturns(
					relation("R01", SemanticRelation.SUPPORTS),
					relation("R02", SemanticRelation.SUPPORTS));

			CoverageResponse response = service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			assertThat(response.gateStatus()).isEqualTo(GateStatus.READY_FOR_UNDERSTANDING);
			assertThat(response.canProceedToUnderstanding()).isTrue();
			assertThat(response.blockingRiskIds()).isEmpty();
			assertThat(response.sessionStatus()).isEqualTo(SessionStatus.COVERAGE_ANALYZED);
		}

		@Test
		@DisplayName("GATE_REQUIRED 가 미충족이면 세션이 GATE_BLOCKED 로 간다")
		void blockingRiskMovesSessionToGateBlocked() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R02", CoverageStatus.NOT_FOUND, null),
					verdict("R05", CoverageStatus.EXPLAINED, "조기상환은 6개월마다 평가합니다."));
			// R02 는 인용이 없어 Verifier 대상에서 빠진다 — 물어볼 근거가 없다
			verifierReturns(relation("R01", SemanticRelation.SUPPORTS));

			CoverageResponse response = service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			assertThat(response.gateStatus()).isEqualTo(GateStatus.GATE_BLOCKED);
			assertThat(response.blockingRiskIds()).containsExactly("R02");
			assertThat(response.sessionStatus()).isEqualTo(SessionStatus.GATE_BLOCKED);
		}

		@Test
		@DisplayName("응답에 classifierStatus 와 coverageStatus 가 모두 실린다 — downgraded 는 파생값")
		void exposesBothStatuses() {
			// AI 는 설명됐다고 했지만 인용을 원문에서 찾을 수 없는 경우
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "원문에 존재하지 않는 인용 문장입니다."),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."),
					verdict("R05", CoverageStatus.NOT_FOUND, null));
			verifierReturns(relation("R02", SemanticRelation.SUPPORTS));

			CoverageResponse response = service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			CoverageResponse.RiskView r01 = riskView(response, "R01");
			assertThat(r01.classifierStatus()).isEqualTo(CoverageStatus.EXPLAINED);
			assertThat(r01.coverageStatus()).isEqualTo(CoverageStatus.INSUFFICIENT);
			assertThat(r01.downgraded()).isTrue();
			assertThat(r01.evidence().provenanceValid()).isFalse();
			assertThat(r01.evidence().provenanceFailureReason())
					.isEqualTo(ProvenanceFailureReason.NOT_FOUND);
		}

		@Test
		@DisplayName("offset 은 서버가 원문에서 재계산한 값이다 (규칙 4)")
		void offsetsAreRecalculatedFromTranscript() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R02", CoverageStatus.NOT_FOUND, null),
					verdict("R05", CoverageStatus.NOT_FOUND, null));
			verifierReturns(relation("R01", SemanticRelation.SUPPORTS));

			CoverageResponse response = service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			CoverageResponse.EvidenceView evidence = riskView(response, "R01").evidence();
			assertThat(evidence.provenanceValid()).isTrue();
			assertThat(TRANSCRIPT.substring(evidence.startOffset(), evidence.endOffset()))
					.isEqualTo("이 상품은 원금이 보장되지 않습니다.");
		}
	}

	@Nested
	@DisplayName("Verifier 대상 선정")
	class VerifierScope {

		@Test
		@DisplayName("GATE_REQUIRED 는 대상이고 WARN_ONLY 비-CONTRADICTED 는 제외된다")
		void gateRequiredOnly() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."),
					verdict("R05", CoverageStatus.EXPLAINED, "조기상환은 6개월마다 평가합니다."));
			verifierReturns(
					relation("R01", SemanticRelation.SUPPORTS),
					relation("R02", SemanticRelation.SUPPORTS));

			service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			assertThat(capturedVerifierTargets())
					.extracting(SemanticVerifier.VerificationRequest::riskId)
					.containsExactly("R01", "R02");
		}

		@Test
		@DisplayName("WARN_ONLY 라도 CONTRADICTED 후보면 대상에 들어간다")
		void contradictedWarnOnlyIsIncluded() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."),
					verdict("R05", CoverageStatus.CONTRADICTED, "조기상환은 6개월마다 평가합니다."));
			verifierReturns(
					relation("R01", SemanticRelation.SUPPORTS),
					relation("R02", SemanticRelation.SUPPORTS),
					relation("R05", SemanticRelation.CONTRADICTS));

			service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			assertThat(capturedVerifierTargets())
					.extracting(SemanticVerifier.VerificationRequest::riskId)
					.contains("R05");
		}

		@Test
		@DisplayName("근거가 원문 대조를 통과 못 하면 Verifier 에 보내지 않는다 — 물어볼 인용이 없다")
		void skipsRisksWithoutValidProvenance() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "원문에 없는 문장을 인용했습니다."),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."),
					verdict("R05", CoverageStatus.NOT_FOUND, null));
			verifierReturns(relation("R02", SemanticRelation.SUPPORTS));

			service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			assertThat(capturedVerifierTargets())
					.extracting(SemanticVerifier.VerificationRequest::riskId)
					.containsExactly("R02");
		}

		@Test
		@DisplayName("대상이 하나도 없으면 Verifier 를 아예 호출하지 않는다")
		void noTargetsMeansNoCall() {
			classifierReturns(
					verdict("R01", CoverageStatus.NOT_FOUND, null),
					verdict("R02", CoverageStatus.NOT_FOUND, null),
					verdict("R05", CoverageStatus.NOT_FOUND, null));

			service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			verify(semanticVerifier, never()).verify(anyString(), anyList());
		}
	}

	@Nested
	@DisplayName("멱등")
	class Idempotency {

		@Test
		@DisplayName("같은 revision 에 결과가 있으면 LLM 을 다시 부르지 않는다")
		void reusesExistingResults() {
			CoverageResultFactory factory = new CoverageResultFactory(new CoverageStatusResolver());
			List<CoverageResult> stored = List.of(
					factory.create(SESSION_ID, REVISION_ID, "R01", CoverageStatus.EXPLAINED,
							"설명됨", "뒷받침됨", "이 상품은 원금이 보장되지 않습니다.",
							ProvenanceCheck.found(0, 20), SemanticRelation.SUPPORTS),
					factory.create(SESSION_ID, REVISION_ID, "R02", CoverageStatus.NOT_FOUND,
							"언급 없음", null, null,
							ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY), null),
					factory.create(SESSION_ID, REVISION_ID, "R05", CoverageStatus.NOT_FOUND,
							"언급 없음", null, null,
							ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY), null));
			when(coverageResultRepository.findByRevisionIdOrderByRiskIdAsc(REVISION_ID))
					.thenReturn(stored);

			CoverageResponse response = service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			verify(classifier, never()).classify(anyString(), anyList());
			verify(semanticVerifier, never()).verify(anyString(), anyList());
			assertThat(response.blockingRiskIds()).containsExactly("R02");
			// 이번 호출에서 잰 레이턴시가 없다. 0을 넣으면 "0ms에 끝났다"로 읽힌다
			assertThat(response.analysis()).isNull();
		}
	}

	@Nested
	@DisplayName("LLM 응답 검증 (규칙 9)")
	class BatchResponseValidation {

		@Test
		@DisplayName("Risk 하나가 빠지면 파싱 실패 — 기본값으로 채우지 않는다")
		void missingRiskIsParsingFailure() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."));

			assertThat(errorCodeOf(() -> service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest())))
					.isEqualTo(ErrorCode.AI_PARSING_FAILED);
		}

		@Test
		@DisplayName("모르는 riskId 가 섞이면 파싱 실패")
		void unknownRiskIsParsingFailure() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."),
					verdict("R99", CoverageStatus.EXPLAINED, "조기상환은 6개월마다 평가합니다."));

			assertThat(errorCodeOf(() -> service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest())))
					.isEqualTo(ErrorCode.AI_PARSING_FAILED);
		}

		@Test
		@DisplayName("같은 riskId 가 두 번 오면 파싱 실패")
		void duplicateRiskIsParsingFailure() {
			classifierReturns(
					verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."),
					verdict("R01", CoverageStatus.NOT_FOUND, null),
					verdict("R02", CoverageStatus.EXPLAINED, "최대 손실률은 마이너스 100%입니다."));

			assertThat(errorCodeOf(() -> service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest())))
					.isEqualTo(ErrorCode.AI_PARSING_FAILED);
		}

		@Test
		@DisplayName("파싱 실패면 아무것도 저장하지 않는다")
		void parsingFailureWritesNothing() {
			classifierReturns(verdict("R01", CoverageStatus.EXPLAINED, "이 상품은 원금이 보장되지 않습니다."));

			errorCodeOf(() -> service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest()));

			verify(writer, never()).saveAnalysis(anyString(), anyList(), any());
		}
	}

	@Nested
	@DisplayName("선행 조건")
	class Preconditions {

		@Test
		@DisplayName("revision 이 없으면 409 — 404 가 아니다")
		void noRevisionIsConflict() {
			when(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.thenReturn(Optional.empty());

			assertThat(errorCodeOf(() -> service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest())))
					.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
		}

		@Test
		@DisplayName("없는 세션이면 SESSION_NOT_FOUND")
		void unknownSession() {
			when(sessionRepository.findById("nope")).thenReturn(Optional.empty());

			assertThat(errorCodeOf(() -> service.analyze("nope", AnalyzeCoverageRequest.latest())))
					.isEqualTo(ErrorCode.SESSION_NOT_FOUND);
		}

		@Test
		@DisplayName("종료된 세션은 재분석할 수 없다")
		void closedSessionRejected() {
			when(sessionRepository.findById(SESSION_ID))
					.thenReturn(Optional.of(session(SessionStatus.SESSION_CLOSED_BY_STAFF)));

			assertThat(errorCodeOf(() -> service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest())))
					.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
			verify(classifier, never()).classify(anyString(), anyList());
		}

		@Test
		@DisplayName("NOT_APPLICABLE Risk 는 분류기에 보내지 않는다")
		void notApplicableRisksAreExcluded() {
			List<ProductRisk> withNotApplicable = new ArrayList<>(risks());
			withNotApplicable.add(risk("R09", CoveragePolicy.NOT_APPLICABLE, false));
			when(productRiskRepository.findByProductIdOrderByRiskIdAsc("PROD_A"))
					.thenReturn(withNotApplicable);

			classifierReturns(
					verdict("R01", CoverageStatus.NOT_FOUND, null),
					verdict("R02", CoverageStatus.NOT_FOUND, null),
					verdict("R05", CoverageStatus.NOT_FOUND, null));

			service.analyze(SESSION_ID, AnalyzeCoverageRequest.latest());

			ArgumentCaptor<List<CoverageClassifier.RiskPrompt>> captor = captor();
			verify(classifier).classify(anyString(), captor.capture());
			assertThat(captor.getValue())
					.extracting(CoverageClassifier.RiskPrompt::riskId)
					.containsExactly("R01", "R02", "R05");
		}
	}

	@Nested
	@DisplayName("Gate Override")
	class Override {

		@BeforeEach
		void storedResults() {
			CoverageResultFactory factory = new CoverageResultFactory(new CoverageStatusResolver());
			when(coverageResultRepository.findByRevisionIdOrderByRiskIdAsc(REVISION_ID)).thenReturn(List.of(
					factory.create(SESSION_ID, REVISION_ID, "R01", CoverageStatus.NOT_FOUND,
							"언급 없음", null, null,
							ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY), null),
					factory.create(SESSION_ID, REVISION_ID, "R02", CoverageStatus.EXPLAINED,
							"설명됨", "뒷받침됨", "최대 손실률은 마이너스 100%입니다.",
							ProvenanceCheck.found(0, 20), SemanticRelation.SUPPORTS),
					factory.create(SESSION_ID, REVISION_ID, "R05", CoverageStatus.NOT_FOUND,
							"언급 없음", null, null,
							ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY), null)));
			when(sessionRepository.findById(SESSION_ID))
					.thenReturn(Optional.of(session(SessionStatus.GATE_BLOCKED)));
		}

		@Test
		@DisplayName("막고 있던 Risk 를 override 하면 Gate 가 열리고 상태가 구분된다")
		void overrideOpensGate() {
			CoverageResponse response = service.override(SESSION_ID, new GateOverrideRequest(
					List.of("R01"), OverrideCategory.AI_MISCLASSIFICATION,
					"상담 중 구두로 충분히 설명했습니다.", true, "staff-001"));

			assertThat(response.gateStatus()).isEqualTo(GateStatus.READY_WITH_STAFF_OVERRIDE);
			assertThat(response.canProceedToUnderstanding()).isTrue();
			assertThat(response.sessionStatus()).isEqualTo(SessionStatus.COVERAGE_ANALYZED);
		}

		@Test
		@DisplayName("사유가 5자 미만이면 OVERRIDE_REASON_REQUIRED")
		void shortReasonRejected() {
			assertThat(errorCodeOf(() -> service.override(SESSION_ID, new GateOverrideRequest(
					List.of("R01"), OverrideCategory.OTHER, "짧음", true, "staff-001"))))
					.isEqualTo(ErrorCode.OVERRIDE_REASON_REQUIRED);
		}

		@Test
		@DisplayName("understandingCheck Risk 인데 확인 여부가 없으면 거부한다")
		void missingStaffConfirmationRejected() {
			assertThat(errorCodeOf(() -> service.override(SESSION_ID, new GateOverrideRequest(
					List.of("R01"), OverrideCategory.OTHER, "충분히 설명했습니다.", null, "staff-001"))))
					.isEqualTo(ErrorCode.STAFF_EXPLANATION_CONFIRMATION_REQUIRED);
		}

		@Test
		@DisplayName("understandingCheck 가 아닌 Risk 는 확인 여부 없이도 override 된다")
		void nonUnderstandingCheckNeedsNoConfirmation() {
			CoverageResponse response = service.override(SESSION_ID, new GateOverrideRequest(
					List.of("R05"), OverrideCategory.OPERATIONAL_EXCEPTION,
					"운영상 예외로 처리합니다.", null, "staff-001"));

			assertThat(response).isNotNull();
		}

		@Test
		@DisplayName("존재하지 않는 Risk 는 RISK_NOT_FOUND")
		void unknownRiskRejected() {
			assertThat(errorCodeOf(() -> service.override(SESSION_ID, new GateOverrideRequest(
					List.of("R99"), OverrideCategory.OTHER, "충분히 설명했습니다.", true, "staff-001"))))
					.isEqualTo(ErrorCode.RISK_NOT_FOUND);
		}

		@Test
		@DisplayName("분석 전에는 override 할 수 없다")
		void overrideBeforeAnalysisRejected() {
			when(coverageResultRepository.findByRevisionIdOrderByRiskIdAsc(REVISION_ID))
					.thenReturn(List.of());

			assertThat(errorCodeOf(() -> service.override(SESSION_ID, new GateOverrideRequest(
					List.of("R01"), OverrideCategory.OTHER, "충분히 설명했습니다.", true, "staff-001"))))
					.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
		}

		@Test
		@DisplayName("AI 원판정은 override 로 바뀌지 않는다 (규칙 1)")
		void classifierStatusSurvivesOverride() {
			CoverageResponse response = service.override(SESSION_ID, new GateOverrideRequest(
					List.of("R01"), OverrideCategory.AI_MISCLASSIFICATION,
					"상담 중 구두로 충분히 설명했습니다.", true, "staff-001"));

			CoverageResponse.RiskView r01 = riskView(response, "R01");
			assertThat(r01.classifierStatus()).isEqualTo(CoverageStatus.NOT_FOUND);
			assertThat(r01.coverageStatus()).isEqualTo(CoverageStatus.NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private void classifierReturns(CoverageClassifier.RiskVerdict... verdicts) {
		when(classifier.classify(anyString(), anyList())).thenReturn(List.of(verdicts));
	}

	private void verifierReturns(SemanticVerifier.RelationVerdict... relations) {
		when(semanticVerifier.verify(anyString(), anyList())).thenReturn(List.of(relations));
	}

	private List<SemanticVerifier.VerificationRequest> capturedVerifierTargets() {
		ArgumentCaptor<List<SemanticVerifier.VerificationRequest>> captor = captor();
		verify(semanticVerifier).verify(anyString(), captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings("unchecked")
	private <T> ArgumentCaptor<List<T>> captor() {
		return ArgumentCaptor.forClass(List.class);
	}

	private CoverageClassifier.RiskVerdict verdict(String riskId, CoverageStatus status, String evidence) {
		return new CoverageClassifier.RiskVerdict(riskId, status, "판정 근거", evidence);
	}

	private SemanticVerifier.RelationVerdict relation(String riskId, SemanticRelation relation) {
		return new SemanticVerifier.RelationVerdict(riskId, relation, "의미 판정 근거");
	}

	private CoverageResponse.RiskView riskView(CoverageResponse response, String riskId) {
		return response.risks().stream()
				.filter(view -> view.riskId().equals(riskId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("응답에 " + riskId + " 가 없다"));
	}

	private ErrorCode errorCodeOf(Runnable action) {
		ApiException thrown = catchThrowableOfType(action::run, ApiException.class);
		assertThat(thrown).as("ApiException 이 던져지지 않았다").isNotNull();
		return thrown.code();
	}

	private ConsultationSession session(SessionStatus status) {
		ConsultationSession session =
				new ConsultationSession(SESSION_ID, "PROD_A", "CUST_A", "A-2026-08-12-01");
		if (status != SessionStatus.DRAFT) {
			overwrite(session, "status", status);
		}
		return session;
	}

	private ConsultationRevision revision() {
		ConsultationRevision revision = new ConsultationRevision(SESSION_ID, 1, TRANSCRIPT);
		overwrite(revision, "id", REVISION_ID);
		return revision;
	}

	private List<ProductRisk> risks() {
		return new ArrayList<>(List.of(
				risk("R01", CoveragePolicy.GATE_REQUIRED, true),
				risk("R02", CoveragePolicy.GATE_REQUIRED, true),
				risk("R05", CoveragePolicy.WARN_ONLY, false)));
	}

	private ProductRisk risk(String riskId, CoveragePolicy policy, boolean understandingCheck) {
		return new ProductRisk("PROD_A", riskId, "카테고리", riskId + " 제목", riskId + " 사실",
				policy, understandingCheck, 1, "출처 문장", "질문", "후속 질문", "쉬운 설명",
				OffsetDateTime.parse("2026-08-12T00:00:00Z"), "reviewer");
	}

	/** 엔티티가 id·status setter 를 열지 않으므로(의도된 설계) 테스트에서만 강제로 채운다 */
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
