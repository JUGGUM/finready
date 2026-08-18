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
	@DisplayName("맨바닥에서 Flyway 전체가 적용되고 ddl-auto: validate가 통과한다")
	void contextLoadsAgainstRealSchema() {
		// 이 테스트가 도는 것 자체가 validate 통과다 — 불일치가 있으면 컨텍스트 기동에서 터진다.
		// 여기서는 마이그레이션이 하나도 실패하지 않았는지만 본다.
		//
		// 개수를 상수로 박지 않는다. 마이그레이션이 늘 때마다 무관한 테스트가 깨지고,
		// 그러면 숫자만 올려 통과시키게 되어 검증이 형식만 남는다.
		Long failed = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where success = false", Long.class);
		assertThat(failed).isZero();

		// version is null인 행은 Flyway가 finready 스키마를 새로 만들 때 남기는
		// "<< Flyway Schema Creation >>" pseudo row다. 실제 마이그레이션만 센다.
		Long applied = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where version is not null", Long.class);
		assertThat(applied).isGreaterThanOrEqualTo(3);
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
