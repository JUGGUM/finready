package io.finready.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 기록의 유일한 쓰기 지점 (TRD §13).
 *
 * <p><b>{@code Propagation.MANDATORY} 다.</b> 호출부에 이미 열린 트랜잭션이 없으면 던진다 —
 * 감사 기록은 <b>자기가 기록하는 변경과 같은 트랜잭션</b>이어야 하기 때문이다. 새 트랜잭션을
 * 열게 두면 상태 전이는 롤백됐는데 "종료했다"는 기록만 남는 조합이 생긴다. 그렇게 어긋난
 * 감사 로그는 없느니만 못하다.
 *
 * <p>그래서 이 서비스는 {@code Writer} 빈들(트랜잭션 경계) 안에서만 불린다. LLM 호출
 * 구간에서 부르면 규칙 6과 정면으로 부딪히는데, MANDATORY 가 그 실수를 기동이 아니라
 * 첫 호출에서 바로 드러낸다.
 */
@Service
public class AuditRecorder {

	/** P0 에는 인증이 없다. 요청이 행위자를 밝히지 않는 경로에서 쓴다 */
	public static final String SYSTEM_ACTOR = "system";

	/** LLM 판정에 쓰는 행위자. 사람 식별자와 섞이지 않게 고정 문자열을 쓴다 */
	public static final String AI_ACTOR = "claude";

	private final AuditEventRepository repository;

	public AuditRecorder(AuditEventRepository repository) {
		this.repository = repository;
	}

	/**
	 * @param actor          행위자 식별자. <b>인증된 신원이 아니다</b> — P0 에서는 요청이 신고한
	 *                       값이거나 고정 문자열이며, 권한 증명으로 쓰이지 않는다 (PRD §14.2)
	 * @param payloadSummary <b>요약만 넣는다.</b> 상담 원문·고객 답변 본문을 그대로 넣지 않는다 —
	 *                       개인정보가 감사 테이블로 번지고, append-only 라 지울 수도 없다.
	 *                       API 키·자격증명은 애초에 여기까지 오지 않아야 한다 (규칙 10)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String sessionId,
	                   AuditEventType eventType,
	                   String actor,
	                   ActorRole actorRole,
	                   String payloadSummary) {
		repository.save(new AuditEvent(
				sessionId, eventType.name(), actor, actorRole, payloadSummary));
	}

	/** 트랜잭션 밖에서 조립한 항목을 그대로 기록한다 ({@link AuditEntry} 참조) */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String sessionId, AuditEntry entry) {
		record(sessionId, entry.eventType(), entry.actor(), entry.actorRole(), entry.summary());
	}

	/** 직원 행위. actor 는 요청이 보낸 값이라 검증되지 않았다 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordStaff(String sessionId, AuditEventType eventType,
	                        String actor, String payloadSummary) {
		record(sessionId, AuditEntry.staff(eventType, actor, payloadSummary));
	}

	/** {@link AuditEntry#ai} 참조 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordAi(String sessionId, AuditEventType eventType, String payloadSummary) {
		record(sessionId, AuditEntry.ai(eventType, payloadSummary));
	}

	/** {@link AuditEntry#system} 참조 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordSystem(String sessionId, AuditEventType eventType, String payloadSummary) {
		record(sessionId, AuditEntry.system(eventType, payloadSummary));
	}
}
