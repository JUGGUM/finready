package io.finready.integration;

import io.finready.coverage.CoverageResult;
import io.finready.coverage.CoverageResultFactory;
import io.finready.coverage.CoverageStatus;
import io.finready.coverage.ProvenanceCheck;
import io.finready.coverage.ProvenanceFailureReason;
import io.finready.coverage.SemanticRelation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * F03 이 실제로 기대는 DB 규칙을 검증한다 (CLAUDE.md 규칙 1·3, TRD §8.5).
 *
 * <p>여기서 확인하는 것은 두 가지다.
 * <ol>
 *   <li>{@link CoverageResultFactory} 가 막는 조합과 DB 제약이 <b>같은 것</b>을 막는지.
 *       둘이 어긋나면 팩토리를 통과한 값이 INSERT 에서 터지거나(운영 500), 반대로
 *       DB 만 믿고 팩토리를 우회하는 코드가 생긴다.</li>
 *   <li>{@code classifier_status} 의 {@code updatable = false} 가 실제 SQL 수준에서
 *       동작하는지. 단위 테스트로는 "코드에 그렇게 적어놨다"까지만 확인된다 —
 *       Hibernate 가 UPDATE 문을 실제로 만들어야 드러난다.</li>
 * </ol>
 */
@DisplayName("coverage_result 제약 (Testcontainers)")
class CoverageConstraintIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String SESSION_ID = "COV_SESSION";

	@Autowired
	private CoverageResultFactory factory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private Long revisionId;

	@BeforeEach
	void seedFixture() {
		jdbcTemplate.update("delete from coverage_result where session_id = ?", SESSION_ID);
		jdbcTemplate.update("delete from consultation_revision where session_id = ?", SESSION_ID);
		jdbcTemplate.update("delete from consultation_session where id = ?", SESSION_ID);

		jdbcTemplate.update("""
				insert into product (id, name, archetype, product_risk_version, document_id, document_url,
				                      document_sha256)
				values ('COV_PROD', 'coverage fixture', 'ELS', 'v1', 'COV_DOC',
				        '/documents/COV_PROD/v1.pdf', repeat('0', 64))
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into customer_profile (id, label) values ('COV_CUST', 'coverage fixture')
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into consultation_session (id, product_id, customer_id, status, product_risk_version)
				values (?, 'COV_PROD', 'COV_CUST', 'COVERAGE_ANALYZED', 'v1')
				""", SESSION_ID);

		revisionId = jdbcTemplate.queryForObject("""
				insert into consultation_revision (session_id, revision_no, text, char_count)
				values (?, 1, '상담 원문입니다. 원금 손실이 발생할 수 있습니다.', 30)
				returning id
				""", Long.class, SESSION_ID);
	}

	@Nested
	@DisplayName("팩토리가 만든 값은 DB 가 받아준다")
	class FactoryOutputIsAccepted {

		@Test
		@Transactional
		@DisplayName("EXPLAINED — provenance 통과 + SUPPORTS")
		void explainedWithFullVerificationPersists() {
			CoverageResult result = factory.create(SESSION_ID, revisionId, "R01",
					CoverageStatus.EXPLAINED, "설명됨", "근거가 주장을 뒷받침한다",
					"원금 손실이 발생할 수 있습니다.", ProvenanceCheck.found(10, 30),
					SemanticRelation.SUPPORTS);

			entityManager.persist(result);
			entityManager.flush();

			assertThat(result.getId()).isNotNull();
			assertThat(result.getCoverageStatus()).isEqualTo(CoverageStatus.EXPLAINED);
		}

		@Test
		@Transactional
		@DisplayName("provenance 실패 — offset 없이 사유만 남는다")
		void provenanceFailurePersistsWithoutOffsets() {
			CoverageResult result = factory.create(SESSION_ID, revisionId, "R02",
					CoverageStatus.NOT_FOUND, "언급 없음", null, null,
					ProvenanceCheck.failed(ProvenanceFailureReason.EMPTY), null);

			entityManager.persist(result);
			entityManager.flush();

			assertThat(result.getId()).isNotNull();
			assertThat(result.getEvidenceStart()).isNull();
			assertThat(result.getProvenanceFailureReason()).isEqualTo(ProvenanceFailureReason.EMPTY);
		}

		@Test
		@Transactional
		@DisplayName("AI 가 EXPLAINED 라 했지만 근거를 못 찾은 경우 — INSUFFICIENT 로 접혀 저장된다")
		void downgradedResultPersists() {
			CoverageResult result = factory.create(SESSION_ID, revisionId, "R03",
					CoverageStatus.EXPLAINED, "AI 는 설명됐다고 봄", null, "원문에 없는 인용",
					ProvenanceCheck.failed(ProvenanceFailureReason.NOT_FOUND), SemanticRelation.SUPPORTS);

			entityManager.persist(result);
			entityManager.flush();

			// 원판정은 그대로 남고(규칙 1), 확정값만 내려간다(규칙 2)
			assertThat(result.getClassifierStatus()).isEqualTo(CoverageStatus.EXPLAINED);
			assertThat(result.getCoverageStatus()).isEqualTo(CoverageStatus.INSUFFICIENT);
		}
	}

	@Nested
	@DisplayName("팩토리를 우회하면 DB 가 막는다")
	class DatabaseRejectsBypass {

		@Test
		@DisplayName("ck_explained_requires_verification — provenance 없는 EXPLAINED")
		void explainedWithoutProvenanceIsRejected() {
			Throwable thrown = catchThrowable(() -> insertRaw("R04",
					"EXPLAINED", "EXPLAINED", false, null, null, null, "SUPPORTS"));

			assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
			assertThat(constraintOf(thrown)).isEqualTo("ck_explained_requires_verification");
		}

		@Test
		@DisplayName("ck_explained_requires_verification — semantic 이 SUPPORTS 가 아닌 EXPLAINED")
		void explainedWithoutSupportsIsRejected() {
			Throwable thrown = catchThrowable(() -> insertRaw("R05",
					"EXPLAINED", "EXPLAINED", true, 10, 30, null, "INSUFFICIENT"));

			assertThat(constraintOf(thrown)).isEqualTo("ck_explained_requires_verification");
		}

		@Test
		@DisplayName("ck_explained_requires_verification — Verifier 미실행(semantic null) EXPLAINED")
		void explainedWithoutVerifierIsRejected() {
			// 계약 결정표의 "원판정 유지" 를 그대로 구현하면 이 조합이 나온다.
			// CoverageStatusResolver 가 INSUFFICIENT 로 접는 이유가 이것이다
			Throwable thrown = catchThrowable(() -> insertRaw("R06",
					"EXPLAINED", "EXPLAINED", true, 10, 30, null, null));

			assertThat(constraintOf(thrown)).isEqualTo("ck_explained_requires_verification");
		}

		@Test
		@DisplayName("ck_provenance_consistency — valid 인데 offset 이 없다")
		void validProvenanceWithoutOffsetsIsRejected() {
			Throwable thrown = catchThrowable(() -> insertRaw("R07",
					"INSUFFICIENT", "INSUFFICIENT", true, null, null, null, null));

			assertThat(constraintOf(thrown)).isEqualTo("ck_provenance_consistency");
		}

		@Test
		@DisplayName("ck_provenance_consistency — valid 인데 실패 사유가 있다")
		void validProvenanceWithFailureReasonIsRejected() {
			Throwable thrown = catchThrowable(() -> insertRaw("R08",
					"INSUFFICIENT", "INSUFFICIENT", true, 10, 30, "AMBIGUOUS", null));

			assertThat(constraintOf(thrown)).isEqualTo("ck_provenance_consistency");
		}
	}

	/**
	 * 규칙 1 — AI 원판정은 어떤 경로로도 UPDATE 되지 않는다.
	 *
	 * <p>setter 가 없으니 정상 코드로는 못 바꾼다. 여기서는 리플렉션으로 필드를 억지로
	 * 바꿔 Hibernate 의 dirty check 를 통과시킨 뒤, 생성된 UPDATE 문에
	 * {@code classifier_status} 가 들어가는지를 본다. {@code updatable = false} 가
	 * 빠지면 이 테스트가 깨진다.
	 */
	@Test
	@Transactional
	@DisplayName("classifier_status 는 updatable=false — UPDATE 문에서 아예 빠진다 (규칙 1)")
	void classifierStatusIsNeverUpdated() throws Exception {
		CoverageResult result = factory.create(SESSION_ID, revisionId, "R09",
				CoverageStatus.EXPLAINED, "설명됨", "뒷받침됨",
				"원금 손실이 발생할 수 있습니다.", ProvenanceCheck.found(10, 30),
				SemanticRelation.SUPPORTS);
		entityManager.persist(result);
		entityManager.flush();

		// 두 필드를 함께 바꾼다. 하나만 바꾸면 dirty check 가 UPDATE 를 안 만들 수도 있다
		overwriteField(result, "classifierStatus", CoverageStatus.NOT_FOUND);
		overwriteField(result, "coverageStatus", CoverageStatus.CONTRADICTED);
		entityManager.flush();

		String storedClassifier = jdbcTemplate.queryForObject(
				"select classifier_status from coverage_result where id = ?", String.class, result.getId());
		String storedCoverage = jdbcTemplate.queryForObject(
				"select coverage_status from coverage_result where id = ?", String.class, result.getId());

		assertThat(storedClassifier).isEqualTo("EXPLAINED");        // 원판정은 살아남는다
		assertThat(storedCoverage).isEqualTo("CONTRADICTED");       // 검증 후 값은 갱신된다
	}

	private void insertRaw(String riskId,
	                       String classifierStatus,
	                       String coverageStatus,
	                       boolean provenanceValid,
	                       Integer evidenceStart,
	                       Integer evidenceEnd,
	                       String provenanceFailureReason,
	                       String semanticRelation) {
		jdbcTemplate.update("""
				insert into coverage_result (session_id, revision_id, risk_id, classifier_status,
				                             coverage_status, provenance_valid, evidence_start, evidence_end,
				                             provenance_failure_reason, semantic_relation)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", SESSION_ID, revisionId, riskId, classifierStatus, coverageStatus,
				provenanceValid, evidenceStart, evidenceEnd, provenanceFailureReason, semanticRelation);
	}

	private void overwriteField(Object target, String fieldName, Object value) throws Exception {
		Field field = CoverageResult.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private String constraintOf(Throwable ex) {
		return io.finready.common.ConstraintNames.of(ex);
	}
}
