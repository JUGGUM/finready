package io.finready.audit;

/**
 * audit_event.event_type 값 목록.
 *
 * <p><b>DDL 에 check 제약이 없다.</b> 컬럼은 varchar(48) 이고 값 목록은 이 enum 에만 있다 —
 * 즉 규칙 9("enum 은 계약")가 여기서는 DB 가 아니라 코드에만 걸려 있다. 목록을 DB 로
 * 내리려면 마이그레이션으로 check 를 먼저 추가해야 둘이 같이 강제된다.
 *
 * <p>계약({@code ReportResponse.auditEvents.eventType})은 자유 문자열이라 값을 늘려도
 * 프론트가 깨지지 않는다. 다만 <b>지우거나 이름을 바꾸면 과거 행이 해석 불가</b>가 된다 —
 * append-only 테이블이라 지난 행을 고칠 방법이 없기 때문이다.
 */
public enum AuditEventType {

	/** F01 세션 생성 */
	SESSION_CREATED,

	/** F02 상담 원문 저장. 동일 텍스트 재전송으로 새 행이 안 생기면 기록하지 않는다 */
	REVISION_SAVED,

	/** F03 Coverage 분석 완료 (AI 판정) */
	COVERAGE_ANALYZED,

	/** F03 직원 Gate Override */
	GATE_OVERRIDE_APPLIED,

	/** F04 이해확인 질문 발급 */
	QUESTIONS_ISSUED,

	/** F05·F07 고객 답변 판정 (AI 판정) */
	ANSWER_JUDGED,

	/** F06 재설명 생성 */
	RE_EXPLANATION_GENERATED,

	/** F07 직원 처리 */
	STAFF_RESOLUTION_RECORDED,

	/** F08 세션 종료 */
	SESSION_CLOSED
}
