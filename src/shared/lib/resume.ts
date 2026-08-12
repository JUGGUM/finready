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
export const RESUME_POINTS = [
  "S01",
  "S02",
  "S03",
  "S04",
  "S05",
  "S06",
  "S07",
  "S08",
] as const satisfies readonly ResumePoint[];

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

export type ResumeDecision =
  | { kind: "navigate"; href: string }
  | { kind: "unknown" };

/**
 * Where to go after an action whose response carries no `nextAction`.
 *
 * Without a `resumePoint` there is no answer to give: choosing one here
 * would mean re-deriving the branch rule the server owns, and a wrong guess
 * could skip a risk that still needs the customer. So it returns `unknown`
 * and the caller surfaces that instead of navigating somewhere plausible.
 */
export function decideResume(
  sessionId: string,
  resumePoint: ResumePoint | undefined | null,
  query = "",
): ResumeDecision {
  if (!resumePoint) return { kind: "unknown" };
  return { kind: "navigate", href: resumeHref(sessionId, resumePoint, query) };
}
