package io.finready.audit;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * <b>{@code JpaRepository} 를 상속하지 않는다.</b> 그러면 {@code delete}·{@code deleteAll} 이
 * 딸려 들어와, append-only 테이블에 삭제 경로가 코드 자동완성에 노출된다.
 * V2 트리거가 런타임에 막긴 하지만 <b>부를 수 없는 편이 낫다</b> — 규칙 1이
 * "리포지토리에 UPDATE 메서드를 만들지 말 것"이라고 한 것과 같은 이유다.
 *
 * <p>필요한 두 개만 직접 선언한다. Spring Data 는 {@link Repository} 만 상속해도
 * 메서드 이름으로 구현을 만들어 준다.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

	AuditEvent save(AuditEvent event);

	/** 리포트에 시간순으로 싣는다. ix_audit_session_created 가 이 순서 그대로다 */
	List<AuditEvent> findBySessionIdOrderByCreatedAtAscIdAsc(String sessionId);
}
