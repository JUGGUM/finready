package io.finready.session;

/**
 * openapi.yml v1.4.2 ResumePoint. 프론트가 새로고침 후 어느 화면으로 복귀할지 결정한다
 * (PRD §13).
 *
 * <p>SessionStatus → ResumePoint 매핑은 <b>TRD에 규정이 없다.</b>
 * TRD §6.6은 Understanding 단계의 nextAction → 화면만 정한다(S04/S06/S07/S08).
 * Coverage 이전 상태의 매핑은 SessionService.resumePointOf 에 있으며,
 * 프론트 화면 정의와 대조해 확정해야 한다.
 */
public enum ResumePoint {
	S01,
	S02,
	S03,
	S04,
	S05,
	S06,
	S07,
	S08
}
