package io.finready.understanding;

/**
 * understanding_result.ai_status — ck_ai_status.
 * AI 최초 판정이며 Staff Resolution 이 이 값을 덮어쓰지 않는다 (TRD §4.2).
 */
public enum UnderstandingStatus {
	UNDERSTOOD,
	MISUNDERSTOOD,
	UNCERTAIN
}
