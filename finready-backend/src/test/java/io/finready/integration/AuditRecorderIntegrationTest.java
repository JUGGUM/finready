package io.finready.integration;

import io.finready.audit.ActorRole;
import io.finready.audit.AuditEvent;
import io.finready.audit.AuditEventRepository;
import io.finready.audit.AuditEventType;
import io.finready.audit.AuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 감사 기록의 두 가지 보장을 실 Postgres 로 확인한다.
 *
 * <p>둘 다 <b>순수 단위 테스트로는 검증되지 않는다.</b> {@code Propagation.MANDATORY} 는
 * Spring 프록시가 있어야 걸리고, append-only 는 트리거가 있어야 걸린다. 대역을 쓰면
 * 어느 쪽도 "코드에 그렇게 적어놨다"까지만 확인된다.
 */
class AuditRecorderIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String SESSION_ID = "audit-it-session";

	@Autowired
	private AuditRecorder auditRecorder;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * audit_event.session_id 가 consultation_session 을, 그게 다시 product·customer_profile 을
	 * 참조한다. SeedLoader 는 {@code @Profile("!test")} 라 안 도므로 여기서 직접 만든다.
	 *
	 * <p>기록을 지우고 시작하지 않는다 — <b>지울 수 없다.</b> append-only 가 이 테이블의 요점이라,
	 * 각 테스트가 자기 이벤트 타입으로 골라 확인한다.
	 */
	@BeforeEach
	void seedSession() {
		jdbcTemplate.update("""
				insert into product (id, name, archetype, product_risk_version, document_id,
				                     document_url, document_sha256)
				values ('AUDIT_PROD', 'audit fixture', 'ELS', 'v1', 'AUDIT_DOC',
				        '/documents/AUDIT_PROD/v1.pdf', repeat('0', 64))
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into customer_profile (id, label) values ('AUDIT_CUST', 'audit fixture')
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into consultation_session (id, product_id, customer_id, status, product_risk_version)
				values (?, 'AUDIT_PROD', 'AUDIT_CUST', 'DRAFT', 'v1')
				on conflict (id) do nothing
				""", SESSION_ID);
	}

	/**
	 * 감사 기록이 자기 트랜잭션을 새로 열면, 기록하려던 변경이 롤백된 뒤에도 기록만 남는다.
	 * MANDATORY 는 그 조합을 <b>기동이 아니라 첫 호출에서</b> 드러낸다.
	 */
	@Test
	@DisplayName("트랜잭션 밖에서 부르면 던진다 — 감사 기록은 변경과 같은 경계여야 한다")
	void requiresActiveTransaction() {
		assertThatThrownBy(() -> auditRecorder.recordSystem(
				SESSION_ID, AuditEventType.SESSION_CREATED, "트랜잭션 없음"))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	/**
	 * actorRole 이 살아 있는지가 요점이다 — 모델이 판정한 것과 직원이 정한 것이 구분되지
	 * 않으면 리포트에서 "AI 원판정을 숨기지 않는다"가 무너진다.
	 */
	@Test
	@DisplayName("트랜잭션 안에서는 기록되고 시간순으로 조회된다")
	void recordsWithinTransaction() {
		transactionTemplate.executeWithoutResult(status -> {
			auditRecorder.recordAi(SESSION_ID, AuditEventType.COVERAGE_ANALYZED,
					"gateStatus=GATE_BLOCKED");
			auditRecorder.recordStaff(SESSION_ID, AuditEventType.SESSION_CLOSED,
					"staff-001", "status=SESSION_CLOSED_BY_STAFF");
		});

		// append-only 라 앞선 테스트의 행이 남아 있을 수 있다. 이 테스트가 만든 것만 고른다
		List<AuditEvent> events =
				auditEventRepository.findBySessionIdOrderByCreatedAtAscIdAsc(SESSION_ID).stream()
						.filter(event -> event.getEventType().equals("COVERAGE_ANALYZED")
								|| event.getEventType().equals("SESSION_CLOSED"))
						.toList();

		assertThat(events).extracting(AuditEvent::getEventType)
				.containsExactly("COVERAGE_ANALYZED", "SESSION_CLOSED");
		assertThat(events).extracting(AuditEvent::getActorRole)
				.containsExactly(ActorRole.AI, ActorRole.STAFF);
		assertThat(events).extracting(AuditEvent::getActor)
				.containsExactly("claude", "staff-001");
	}

	/**
	 * V2 트리거가 실제로 막는지 본다. {@code @Immutable} 은 Hibernate 쪽만 막으므로
	 * JdbcTemplate 으로 우회해 확인해야 트리거를 검증한 것이 된다 (TRD §4.4).
	 */
	@Test
	@DisplayName("기록된 행은 UPDATE 로 고칠 수 없다")
	void isAppendOnly() {
		transactionTemplate.executeWithoutResult(status ->
				auditRecorder.recordSystem(SESSION_ID, AuditEventType.REVISION_SAVED,
						"revisionNo=1, charCount=10"));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"update audit_event set actor = 'tampered' where session_id = ?", SESSION_ID))
				.hasMessageContaining("append-only");
	}
}
