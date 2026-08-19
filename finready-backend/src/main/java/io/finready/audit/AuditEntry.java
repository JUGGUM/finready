package io.finready.audit;

/**
 * 감사 이벤트 한 건의 내용. <b>트랜잭션 경계 밖에서 조립해 안으로 넘기기 위한 값이다.</b>
 *
 * <p>이런 타입을 둔 이유는 규칙 6 때문이다. Coverage·Understanding 은 LLM 을 호출하므로
 * 서비스에 트랜잭션이 없고, 쓰기만 {@code *Writer} 빈이 묶는다. 그런데 감사 요약에 필요한
 * 정보(Gate 판정, 판정 결과, Override 대상)는 <b>서비스가 갖고 있고 Writer 는 모른다.</b>
 *
 * <p>그래서 서비스가 이 값을 만들어 Writer 에 넘기고, Writer 가 자기 트랜잭션 안에서 기록한다.
 * Writer 가 요약을 직접 만들게 하면 판정 로직이 트랜잭션 경계 클래스로 새어 들어간다.
 *
 * @param summary <b>요약만 담는다.</b> 상담 원문·고객 답변 본문은 넣지 않는다
 *                — append-only 테이블이라 한 번 들어가면 지울 수 없다
 */
public record AuditEntry(AuditEventType eventType, String actor, ActorRole actorRole, String summary) {

	/** 직원 행위. {@code actor} 는 요청이 신고한 값이라 검증되지 않았다 (PRD §14.2) */
	public static AuditEntry staff(AuditEventType eventType, String actor, String summary) {
		return new AuditEntry(eventType, actor, ActorRole.STAFF, summary);
	}

	/**
	 * AI 판정. <b>{@code STAFF} 로 적지 않는 것이 요점이다</b> — 리포트에서 사람이 정한 것과
	 * 모델이 정한 것이 구분되지 않으면 "AI 원판정을 숨기지 않는다"는 원칙이 무너진다.
	 */
	public static AuditEntry ai(AuditEventType eventType, String summary) {
		return new AuditEntry(eventType, AuditRecorder.AI_ACTOR, ActorRole.AI, summary);
	}

	/**
	 * 서버가 스스로 한 일. 화면을 조작한 사람이 누구인지 모르는 경로에서 쓴다.
	 *
	 * <p>인증이 없는데 {@code STAFF} 로 적으면 <b>없는 신원을 지어내는 것</b>이고,
	 * 감사 로그에서 가장 하면 안 되는 일이다.
	 */
	public static AuditEntry system(AuditEventType eventType, String summary) {
		return new AuditEntry(eventType, AuditRecorder.SYSTEM_ACTOR, ActorRole.SYSTEM, summary);
	}
}
