package io.finready.understanding;

/**
 * openapi.yml v1.4.2 NextAction. <b>프론트의 분기는 이 값만 보고 결정한다</b>(규칙 8, TRD §6.6).
 *
 * <p>프론트가 aiStatus 와 attempt 를 보고 자체 분기하지 않는다는 것이 계약의 요지다.
 * 그래서 값 하나로 화면 이동이 결정되며, 산출은 {@link NextActionResolver} 한 곳에서만 한다.
 */
public enum NextAction {

	/** 다음 Risk 로 (S04) */
	NEXT_RISK,

	/** 재설명 (S06). MISUNDERSTOOD attempt 1 에서만 나온다 */
	REEXPLAIN,

	/** 후속 확인 (S07) */
	RECHECK,

	/** 직원 처리 필요 (S07). attempt 2 까지 안 풀린 경우 */
	STAFF_RESOLUTION_REQUIRED,

	/** 리포트로 (S08) */
	GO_TO_REPORT
}
