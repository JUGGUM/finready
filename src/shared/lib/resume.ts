import type { ResumePoint } from "@/shared/types/domain";

/**
 * Where each `resumePoint` lives in this app's routes.
 *
 * `resumePoint` is computed by the server (PRD §13). The client's only job
 * is to translate it into a URL — it must not decide the resume position
 * itself, or a reload would disagree with the session's real state.
 *
 * S04–S07 share one customer route; the screen inside it picks the risk
 * from server state. S07 maps to the staff review because that step belongs
 * to the staff member, not the customer.
 */
const RESUME_ROUTE: Record<ResumePoint, string> = {
  S01: "prepare",
  S02: "transcript",
  S03: "coverage",
  S04: "understanding",
  S05: "understanding",
  S06: "understanding",
  S07: "review",
  S08: "report",
};

export function resumeHref(
  sessionId: string,
  resumePoint: ResumePoint,
  query = "",
): string {
  return `/session/${sessionId}/${RESUME_ROUTE[resumePoint]}${query}`;
}
