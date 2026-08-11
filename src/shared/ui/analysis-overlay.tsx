"use client";

import { useEffect, useState } from "react";
import { ANALYSIS_STAGES } from "@/shared/constants/labels";

/**
 * Progress feedback for coverage analysis.
 *
 * It names the stage the pipeline is in rather than inventing a
 * percentage — the app cannot know how far along an LLM call is, and a
 * fabricated number would be the first dishonest thing on screen. The bar
 * is an indeterminate sweep, not a measurement.
 */
export function AnalysisOverlay({ label }: { label?: string }) {
  const [stage, setStage] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setStage((s) => Math.min(s + 1, ANALYSIS_STAGES.length - 1));
    }, 520);
    return () => clearInterval(timer);
  }, []);

  return (
    <div
      role="status"
      aria-live="polite"
      className="fade-in fixed inset-0 z-60 flex items-center justify-center bg-[oklch(0.985_0.004_85/0.86)] backdrop-blur-[8px]"
    >
      <div className="w-[420px]">
        <p className="mb-[24px] text-[20px] font-semibold tracking-[-0.015em]">
          {label ?? "상담 내용을 분석하고 있습니다"}
        </p>

        {ANALYSIS_STAGES.map((text, index) => {
          const done = index < stage;
          const active = index === stage;
          return (
            <div key={text} className="flex items-center gap-[12px] py-[9px]">
              <span
                aria-hidden
                className="size-[9px] flex-none rounded-full"
                style={{
                  background: done
                    ? "var(--color-ok-dot)"
                    : active
                      ? "var(--color-accent)"
                      : "oklch(0.9 0.006 85)",
                }}
              />
              <span
                className="text-[15px]"
                style={{
                  color: done || active ? "var(--color-ink-body)" : "var(--color-disabled)",
                  fontWeight: active ? 600 : 400,
                }}
              >
                {text}
              </span>
            </div>
          );
        })}

        <div className="mt-[22px] h-[3px] overflow-hidden rounded-[2px] bg-[oklch(0.92_0.006_85)]">
          <div className="h-full w-1/3 animate-[finready-sweep_1.4s_ease-in-out_infinite] rounded-[2px] bg-[var(--color-accent)]" />
        </div>
      </div>
    </div>
  );
}
