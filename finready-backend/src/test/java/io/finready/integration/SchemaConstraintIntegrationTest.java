package io.finready.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testcontainers 스캐폴딩의 첫 스모크 테스트. 지금까지 로컬 기동으로만 수기 확인했던
 * 두 가지를 CI에서 자동 회귀로 잡는다 (CLAUDE.md "검증한 것 (2026-08-12)").
 *
 * <p>F03 리포지토리·서비스가 생기면 이 클래스가 아니라 {@link AbstractPostgresIntegrationTest}를
 * 상속하는 기능별 테스트로 옮겨간다 — 여기 남는 건 스키마 레벨 회귀뿐이다.
 */
@DisplayName("실 Postgres 스키마 제약 (Testcontainers)")
class SchemaConstraintIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Flyway V1+V2가 적용된 스키마로 ddl-auto: validate가 통과한다")
	void contextLoadsAgainstRealSchema() {
		// version is null인 행은 Flyway가 finready 스키마를 새로 만들 때 남기는
		// "<< Flyway Schema Creation >>" pseudo row다. 실제 마이그레이션만 센다.
		Long historyCount = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where success = true and version is not null",
				Long.class);
		assertThat(historyCount).isEqualTo(2);
	}

	@Test
	@DisplayName("audit_event는 INSERT는 되지만 UPDATE는 트리거가 [23001]로 막는다 (TRD §4.4)")
	void auditEventIsAppendOnly() {
		seedMinimalSessionFixture();

		jdbcTemplate.update("""
				insert into audit_event (session_id, event_type, actor, actor_role, payload_summary, created_at)
				values (?, 'SESSION_CREATED', 'system', 'SYSTEM', 'smoke test row', ?)
				""", "IT_SESSION", OffsetDateTime.now());

		Long rowCount = jdbcTemplate.queryForObject(
				"select count(*) from audit_event where session_id = ?", Long.class, "IT_SESSION");
		assertThat(rowCount).isEqualTo(1L);

		DataAccessException thrown = (DataAccessException) org.assertj.core.api.Assertions.catchThrowable(
				() -> jdbcTemplate.update(
						"update audit_event set payload_summary = 'tampered' where session_id = ?", "IT_SESSION"));

		SQLException rootCause = (SQLException) thrown.getRootCause();
		assertNotNull(rootCause);
		assertEquals("23001", rootCause.getSQLState());
	}

	/** audit_event FK 체인(product → customer_profile → consultation_session)을 만족시키는 최소 픽스처 */
	private void seedMinimalSessionFixture() {
		assertDoesNotThrow(() -> {
			jdbcTemplate.update("""
					insert into product (id, name, archetype, product_risk_version, document_id, document_url,
					                      document_sha256)
					values ('IT_PROD', 'IT smoke product', 'ELS', 'v1', 'IT_DOC', '/documents/IT_PROD/v1.pdf',
					        repeat('0', 64))
					""");
			jdbcTemplate.update("""
					insert into customer_profile (id, label) values ('IT_CUST', 'IT smoke customer')
					""");
			jdbcTemplate.update("""
					insert into consultation_session (id, product_id, customer_id, status, product_risk_version)
					values ('IT_SESSION', 'IT_PROD', 'IT_CUST', 'DRAFT', 'v1')
					""");
		});
	}
}
