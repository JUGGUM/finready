"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useDemoProduct } from "@/shared/api/queries";
import type { ScenarioId } from "@/shared/constants/demo";

/**
 * Staff-side chrome: identity of the session under review, which revision
 * it is on, and where in S01–S08 the staff member currently stands.
 *
 * The customer screens (S04–S07) deliberately do not use this shell — the
 * surface change is how the handoff reads as a change of hands.
 */

export type StepId =
  | "s01"
  | "s02"
  | "s03"
  | "s04"
  | "s05"
  | "s06"
  | "s07"
  | "s08";

const STEPS: ReadonlyArray<{ id: StepId; label: string }> = [
  { id: "s01", label: "S01 준비" },
  { id: "s02", label: "S02 입력" },
  { id: "s03", label: "S03 설명 충족도" },
  { id: "s04", label: "S04 이해확인" },
  { id: "s05", label: "S05 이해 결과" },
  { id: "s06", label: "S06 재설명" },
  { id: "s07", label: "S07 재확인" },
  { id: "s08", label: "S08 리포트" },
];

/** Routes that exist today. Steps without one render as not-yet-reachable. */
const STEP_ROUTE: Partial<Record<StepId, string>> = {
  s01: "prepare",
  s02: "transcript",
  s03: "coverage",
};

const SCENARIO_TAG: Record<ScenarioId, { label: string; className: string }> = {
  main: {
    label: "대표 데모",
    className: "bg-[var(--color-accent-soft)] text-[var(--color-accent)]",
  },
  safety: {
    label: "예외 시나리오",
    className: "bg-[var(--color-none-bg)] text-[var(--color-none-fg)]",
  },
};

export function useScenario(): ScenarioId {
  const params = useSearchParams();
  return params.get("scenario") === "safety" ? "safety" : "main";
}

/** Preserves the demo scenario across in-flow navigation. */
export function useScenarioQuery(): string {
  const scenario = useScenario();
  return scenario === "safety" ? "?scenario=safety" : "";
}

export function StaffShell({
  sessionId,
  revision,
  current,
  children,
}: {
  sessionId: string;
  /** Revision currently under review; 1 before the first save. */
  revision: number;
  current: StepId;
  children: React.ReactNode;
}) {
  const router = useRouter();
  const scenario = useScenario();
  const demo = useDemoProduct();
  const query = scenario === "safety" ? "?scenario=safety" : "";
  const tag = SCENARIO_TAG[scenario];

  const productName = demo.data?.product?.name ?? "";
  const customerLabel = demo.data?.customers?.[0]?.label ?? "";
  const currentIndex = STEPS.findIndex((s) => s.id === current);

  return (
    <>
      <div className="sticky top-0 z-20 border-b border-[var(--color-line)] bg-[oklch(0.985_0.004_85/0.92)] backdrop-blur-[12px] backdrop-saturate-[1.4]">
        <div className="flex items-center justify-between px-[40px] py-[14px]">
          <div className="flex items-center gap-[14px]">
            <span
              aria-hidden
              className="size-[16px] rounded-[5px] bg-[var(--color-accent)]"
            />
            <span className="text-[14px] font-semibold">FinReady</span>
            <span aria-hidden className="text-[oklch(0.86_0.01_260)]">
              |
            </span>
            <span className="text-[13.5px] text-[var(--color-ink-muted)]">
              {productName}
              {customerLabel ? ` · ${customerLabel}` : ""}
            </span>
            <span className="rounded-[6px] border border-[var(--color-line)] px-[7px] py-[3px] font-mono text-[11px] font-semibold text-[var(--color-muted)]">
              rev.{Math.max(revision, 1)}
            </span>
          </div>

          <div className="flex items-center gap-[14px]">
            <span
              className={`rounded-full px-[10px] py-[4px] text-[12px] font-semibold ${tag.className}`}
            >
              {tag.label}
            </span>
            <button
              type="button"
              onClick={() => router.push("/")}
              className="px-[10px] py-[7px] text-[12.5px] text-[var(--color-muted)] hover:text-[oklch(0.3_0.01_260)]"
            >
              처음으로
            </button>
          </div>
        </div>

        <nav
          aria-label="상담 진행 단계"
          className="flex items-center px-[40px] pb-[12px]"
        >
          {STEPS.map((step, index) => {
            const route = STEP_ROUTE[step.id];
            const isActive = step.id === current;
            const isPast = index < currentIndex && Boolean(route);
            const reachable = isActive || isPast;

            return (
              <button
                key={step.id}
                type="button"
                aria-current={isActive ? "step" : undefined}
                disabled={!reachable}
                onClick={() => {
                  if (!reachable || !route || isActive) return;
                  router.push(`/session/${sessionId}/${route}${query}`);
                }}
                className={`rounded-[8px] px-[12px] py-[6px] text-[12.5px] ${
                  isActive
                    ? "bg-[var(--color-accent-tab)] font-semibold text-[var(--color-ink)]"
                    : isPast
                      ? "text-[oklch(0.5_0.01_260)]"
                      : "text-[var(--color-disabled)]"
                } ${reachable && !isActive ? "cursor-pointer" : ""} ${
                  !reachable ? "cursor-default" : ""
                }`}
              >
                {step.label}
              </button>
            );
          })}
        </nav>
      </div>
      {children}
    </>
  );
}
