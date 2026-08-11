"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useCreateSession, useDemoProduct } from "@/shared/api/queries";
import type { ScenarioId } from "@/shared/constants/demo";
import { DISCLAIMER } from "@/shared/constants/labels";
import { ErrorNote } from "@/shared/ui/error-note";

const PILLARS = [
  {
    title: "상품 위험",
    body: "검수된 9개 위험 사실과 근거 페이지",
  },
  {
    title: "상담 설명",
    body: "각 위험이 실제로 설명됐는지 원문으로 확인",
  },
  {
    title: "고객 이해",
    body: "핵심 위험 3건을 고객이 직접 확인",
  },
  {
    title: "근거 기반 재확인",
    body: "상품설명서 원문으로 다시 설명하고 재확인",
  },
];

export function LandingScreen() {
  const router = useRouter();
  const demo = useDemoProduct();
  const createSession = useCreateSession();
  const [pending, setPending] = useState<ScenarioId | null>(null);

  const start = (scenario: ScenarioId) => {
    if (createSession.isPending) return;
    const productId = demo.data?.product?.id;
    const customerId = demo.data?.customers?.[0]?.id;
    if (!productId || !customerId) return;

    setPending(scenario);
    createSession.mutate(
      { productId, customerId },
      {
        onSuccess: (session) => {
          const query = scenario === "safety" ? "?scenario=safety" : "";
          router.push(`/session/${session.sessionId}/prepare${query}`);
        },
        onError: () => setPending(null),
      },
    );
  };

  // The demo product must be loaded before a session can name its ids.
  const busy = createSession.isPending || demo.isLoading;

  return (
    <div className="screen-in">
      <header className="flex items-center justify-between border-b border-[var(--color-line)] px-[120px] py-[24px]">
        <div className="flex items-center gap-[10px]">
          <span
            aria-hidden
            className="size-[18px] rounded-[5px] bg-[var(--color-accent)]"
          />
          <span className="text-[16px] font-semibold tracking-[-0.01em]">
            FinReady
          </span>
        </div>
        <span className="text-[13px] text-[var(--color-muted)]">
          Finance, Ready for You.
        </span>
      </header>

      <main className="mx-auto max-w-[1040px] px-[40px] pt-[112px]">
        <h1 className="text-[68px] leading-[1.14] font-bold tracking-[-0.035em] text-pretty">
          당신이 이해할 때까지,
          <br />
          금융을 더 명확하게.
        </h1>
        <p className="mt-[32px] max-w-[620px] text-[19px] leading-[1.65] text-pretty text-[var(--color-ink-muted)]">
          금융상품을 설명하는 AI가 아니라, 고객이 핵심 위험을 제대로 이해했는지
          확인하는 AI.
        </p>

        <div className="mt-[48px] flex items-center gap-[24px]">
          <button
            type="button"
            onClick={() => start("main")}
            disabled={busy}
            className="rounded-[11px] bg-[var(--color-accent)] px-[30px] py-[15px] text-[16px] font-semibold text-white hover:bg-[var(--color-accent-hover)] disabled:bg-[var(--color-accent-disabled)]"
          >
            {pending === "main" ? "세션 준비 중…" : "대표 데모 시작"}
          </button>
          <span className="text-[14px] text-[var(--color-muted)]">
            Product A · 약 3분
          </span>
        </div>

        <button
          type="button"
          onClick={() => start("safety")}
          disabled={busy}
          className="mt-[22px] block px-[2px] py-[4px] text-[14px] text-[var(--color-muted-soft)] underline underline-offset-[3px] hover:text-[var(--color-ink-body)] disabled:opacity-60"
        >
          {pending === "safety"
            ? "세션 준비 중…"
            : "예외 시나리오: 설명이 사실과 달랐던 상담 보기"}
        </button>

        {createSession.isError || demo.isError ? (
          <ErrorNote
            className="mt-[20px] max-w-[620px]"
            error={createSession.error ?? demo.error}
            onRetry={() =>
              demo.isError ? demo.refetch() : start(pending ?? "main")
            }
          />
        ) : null}

        <div className="mt-[104px] grid grid-cols-4 border-t border-[var(--color-line)] pt-[28px]">
          {PILLARS.map((pillar, index) => (
            <div
              key={pillar.title}
              className={index === PILLARS.length - 1 ? "" : "pr-[24px]"}
            >
              <p className="text-[15px] font-semibold">{pillar.title}</p>
              <p className="mt-[8px] text-[14px] leading-[1.6] text-[oklch(0.5_0.01_260)]">
                {pillar.body}
              </p>
            </div>
          ))}
        </div>

        <p className="mt-[36px] mb-[96px] text-[12.5px] text-[var(--color-muted-faint)]">
          {DISCLAIMER}
        </p>
      </main>
    </div>
  );
}
