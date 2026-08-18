package io.finready.common;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제약 위반을 <b>구분해서</b> 매핑하는지 본다.
 *
 * <p>여기서 지키려는 것은 "친절한 오류 응답"이 아니라 그 반대다. 동시 채번 충돌만 409 로
 * 내보내고, {@code ck_*} 위반은 500 으로 남겨야 한다 — 후자는 애플리케이션이 DB 규칙을
 * 우회했다는 뜻이라 조용히 409 로 감싸면 규칙 3 위반이 묻힌다.
 */
@DisplayName("GlobalExceptionHandler 제약 위반 분류")
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	@DisplayName("uq_revision 위반은 409 CONCURRENT_SESSION_UPDATE + recoverable")
	void uniqueRevisionViolationBecomesRetryableConflict() {
		ResponseEntity<ErrorResponse> response =
				handler.handleDataIntegrityViolation(hibernateViolation("uq_revision"));

		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo(ErrorCode.CONCURRENT_SESSION_UPDATE);
		assertThat(response.getBody().recoverable()).isTrue();
	}

	@Test
	@DisplayName("ck_explained_requires_verification 위반은 409 로 감싸지 않고 500 으로 남긴다")
	void checkConstraintViolationStaysInternalError() {
		ResponseEntity<ErrorResponse> response =
				handler.handleDataIntegrityViolation(hibernateViolation("ck_explained_requires_verification"));

		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(response.getBody().recoverable()).isFalse();
	}

	@Test
	@DisplayName("제약 이름을 못 찾으면 보수적으로 500")
	void unknownConstraintStaysInternalError() {
		ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
				new DataIntegrityViolationException("이름 없는 위반"));

		assertThat(response.getStatusCode().value()).isEqualTo(500);
	}

	@Test
	@DisplayName("응답에 내부 예외 문구가 새지 않는다 (TRD §12)")
	void responseDoesNotLeakInternals() {
		ResponseEntity<ErrorResponse> response =
				handler.handleDataIntegrityViolation(hibernateViolation("uq_revision"));

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message())
				.doesNotContain("uq_revision")
				.doesNotContain("SQL");
	}

	@Nested
	@DisplayName("ConstraintNames 추출")
	class Extraction {

		@Test
		@DisplayName("JPA 경로 — Hibernate 예외가 이름을 직접 들고 있다")
		void fromHibernateException() {
			assertThat(ConstraintNames.of(hibernateViolation("uq_revision"))).isEqualTo("uq_revision");
		}

		@Test
		@DisplayName("JdbcTemplate 경로 — 이름이 메시지 안에만 있다")
		void fromMessageFallback() {
			DataIntegrityViolationException ex = new DataIntegrityViolationException(
					"""
					PreparedStatementCallback; ERROR: duplicate key value violates unique \
					constraint "uq_revision"
					""");

			assertThat(ConstraintNames.of(ex)).isEqualTo("uq_revision");
		}

		@Test
		@DisplayName("어느 쪽에서도 못 찾으면 null")
		void missingConstraintName() {
			assertThat(ConstraintNames.of(new RuntimeException("아무 정보 없음"))).isNull();
		}
	}

	/** Hibernate 가 JPA 저장 경로에서 감싸는 모양 그대로 만든다 */
	private DataIntegrityViolationException hibernateViolation(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException(
				"could not execute statement", new SQLException("duplicate key", "23505"), constraintName);
		return new DataIntegrityViolationException("could not execute statement", cause);
	}
}
