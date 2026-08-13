package io.finready.understanding;

/**
 * risk_workflow_state.final_disposition — ck_final_disposition.
 * ck_disposition_only_when_complete 때문에 workflow_status 가 COMPLETE 일 때만 non-null 이다 (TRD §6.3).
 */
public enum FinalDisposition {
	AUTO_RESOLVED,
	RESOLVED_BY_STAFF,
	UNRESOLVED,
	SKIPPED_BY_OVERRIDE
}
