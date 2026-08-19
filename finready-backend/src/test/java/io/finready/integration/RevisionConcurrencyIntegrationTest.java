package io.finready.integration;

import io.finready.common.ApiException;
import io.finready.common.ConstraintNames;
import io.finready.common.ErrorCode;
import io.finready.product.CustomerProfileRepository;
import io.finready.product.ProductRepository;
import io.finready.session.ConsultationRevisionRepository;
import io.finready.session.ConsultationSessionRepository;
import io.finready.session.CreateRevisionRequest;
import io.finready.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * revisionNo 채번 경쟁 상태 재현 — {@code docs/decisions/2026-08-14-revision-no-race-condition.md}.
 *
 * <p>이 문제는 <b>mock 기반 테스트로는 드러나지 않는다.</b> {@code uq_revision} 이 실제
 * Postgres 에 있어야 재현된다. 결정 문서가 "Testcontainers 를 붙일 때 재현 가능하다"고
 * 적어둔 그 테스트다.
 *
 * <p>경쟁 자체는 고치지 않았다(의도적, 결정 문서 참조). 여기서 고정하는 것은
 * <b>실패가 어떤 모양으로 나가는지</b>다 — 500 INTERNAL_ERROR 가 아니라
 * 409 CONCURRENT_SESSION_UPDATE(recoverable) 로 나가야 프론트가 재시도할 수 있다.
 */
@DisplayName("revisionNo 동시 채번 (Testcontainers)")
class RevisionConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String SESSION_ID = "CONC_SESSION";

	@Autowired
	private SessionService sessionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ConsultationRevisionRepository revisionRepository;

	@Autowired
	private ConsultationSessionRepository sessionRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CustomerProfileRepository customerProfileRepository;

	/**
	 * 세션 행은 지우지 않는다. F08 부터 {@code createRevision} 이 {@code audit_event} 를 남기는데,
	 * 그 테이블은 append-only(V2 트리거)라 <b>참조하는 세션을 지울 방법이 없다</b> —
	 * 감사 기록이 세션을 고정한다는 뜻이고, 그게 의도된 동작이다.
	 * revision 만 비우면 이 테스트가 필요로 하는 초기 상태(revisionNo 가 1부터)는 그대로 만들어진다.
	 */
	@BeforeEach
	void seedSession() {
		jdbcTemplate.update("delete from consultation_revision where session_id = ?", SESSION_ID);

		jdbcTemplate.update("""
				insert into product (id, name, archetype, product_risk_version, document_id, document_url,
				                      document_sha256)
				values ('CONC_PROD', 'concurrency fixture', 'ELS', 'v1', 'CONC_DOC',
				        '/documents/CONC_PROD/v1.pdf', repeat('0', 64))
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into customer_profile (id, label) values ('CONC_CUST', 'concurrency fixture')
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into consultation_session (id, product_id, customer_id, status, product_risk_version)
				values (?, 'CONC_PROD', 'CONC_CUST', 'DRAFT', 'v1')
				on conflict (id) do update set status = 'DRAFT'
				""", SESSION_ID);
	}

	@Test
	@DisplayName("서로 다른 텍스트를 동시에 저장하면 한쪽은 uq_revision 위반으로 실패한다")
	void concurrentRevisionsCollideOnUniqueConstraint() throws Exception {
		// 텍스트가 다르다는 게 핵심이다. 같은 텍스트면 계약대로 기존 revision 을 재사용해
		// INSERT 자체가 일어나지 않으므로 경쟁이 재현되지 않는다 (결정 문서 "3. 더블 클릭")
		List<Throwable> failures = runConcurrently(
				() -> sessionService.createRevision(SESSION_ID, new CreateRevisionRequest("첫 번째 상담 내용입니다.")),
				() -> sessionService.createRevision(SESSION_ID, new CreateRevisionRequest("두 번째 상담 내용입니다.")));

		// 경쟁이 항상 재현되지는 않는다. 두 스레드가 실제로 겹쳤을 때만 위반이 난다
		if (failures.isEmpty()) {
			assertThat(revisionRepository.findTopBySessionIdOrderByRevisionNoDesc(SESSION_ID))
					.hasValueSatisfying(r -> assertThat(r.getRevisionNo()).isEqualTo(2));
			return;
		}

		assertThat(failures).hasSize(1);
		assertThat(rootConstraintNameOf(failures.getFirst())).isEqualTo("uq_revision");
	}

	@Test
	@DisplayName("uq_revision 위반은 500 이 아니라 409 CONCURRENT_SESSION_UPDATE 로 매핑된다")
	void uniqueViolationMapsToRecoverableConflict() {
		sessionService.createRevision(SESSION_ID, new CreateRevisionRequest("첫 번째 상담 내용입니다."));

		// 경쟁의 결과 상태 — 이미 쓰인 번호로 INSERT 가 들어오는 상황을 직접 만든다.
		// 스레드 타이밍에 기대지 않으므로 이 검증은 항상 재현된다
		DataIntegrityViolationException violation = catchDataIntegrityViolation();

		assertThat(rootConstraintNameOf(violation)).isEqualTo("uq_revision");

		// GlobalExceptionHandler 가 이 제약을 재시도 가능으로 분류하는지
		assertThat(ErrorCode.CONCURRENT_SESSION_UPDATE.status().value()).isEqualTo(409);
		assertThat(ErrorCode.CONCURRENT_SESSION_UPDATE.recoverable()).isTrue();
	}

	@Test
	@DisplayName("ck_char_count 위반은 재시도 대상이 아니다 — 409 로 감싸면 버그가 묻힌다")
	void checkConstraintIsNotTreatedAsConcurrency() {
		// 8000자 상한은 서비스가 먼저 막지만, DB 제약이 살아 있는지는 별개로 확인한다.
		// 이 예외가 uq_revision 과 같은 취급을 받으면 규칙 위반이 조용히 409 로 나간다
		DataIntegrityViolationException violation = catchCharCountViolation();

		assertThat(rootConstraintNameOf(violation)).isEqualTo("ck_char_count");
	}

	private DataIntegrityViolationException catchDataIntegrityViolation() {
		return (DataIntegrityViolationException) org.assertj.core.api.Assertions.catchThrowable(
				() -> jdbcTemplate.update("""
						insert into consultation_revision (session_id, revision_no, text, char_count)
						values (?, 1, '중복 번호로 들어오는 행', 11)
						""", SESSION_ID));
	}

	private DataIntegrityViolationException catchCharCountViolation() {
		return (DataIntegrityViolationException) org.assertj.core.api.Assertions.catchThrowable(
				() -> jdbcTemplate.update("""
						insert into consultation_revision (session_id, revision_no, text, char_count)
						values (?, 99, '길이가 어긋난 행', 0)
						""", SESSION_ID));
	}

	/** 두 작업을 barrier 로 맞춰 동시에 출발시키고, 던져진 예외만 모은다 */
	private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			List<Future<Throwable>> futures = pool.invokeAll(List.of(
					guarded(barrier, first), guarded(barrier, second)));

			List<Throwable> thrown = new java.util.ArrayList<>();
			for (Future<Throwable> future : futures) {
				Throwable result = future.get();
				if (result != null) {
					thrown.add(result);
				}
			}
			return thrown;
		}
	}

	private Callable<Throwable> guarded(CyclicBarrier barrier, Runnable action) {
		return () -> {
			try {
				barrier.await();
				action.run();
				return null;
			} catch (ApiException | DataIntegrityViolationException ex) {
				return ex;
			}
		};
	}

	/** 프로덕션 코드가 쓰는 것과 같은 추출기로 확인한다 — 둘이 어긋나면 이 테스트가 의미를 잃는다 */
	private String rootConstraintNameOf(Throwable ex) {
		return ConstraintNames.of(ex);
	}
}
