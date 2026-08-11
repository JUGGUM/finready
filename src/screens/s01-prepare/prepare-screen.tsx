"use client";

import { useRouter } from "next/navigation";
import { useDemoProduct, useSession } from "@/shared/api/queries";
import type { ScenarioId } from "@/shared/constants/demo";
import { ErrorNote } from "@/shared/ui/error-note";
import { ScreenSkeleton } from "@/shared/ui/screen-skeleton";
import { StaffShell, useScenario } from "@/shared/ui/staff-shell";

const SCENARIO_NOTE: Record<ScenarioId, string> = {
  main: "샘플 상담 내용을 불러와 바로 분석할 수 있습니다.",
  safety: "설명이 사실과 달랐던 상담 기록으로 진행합니다.",
};

export function PrepareScreen({ sessionId }: { sessionId: string }) {
  const router = useRouter();
  const scenario = useScenario();
  const demo = useDemoProduct();
  const session = useSession(sessionId);

  if (demo.isError || session.isError) {
    return (
      <div className="mx-auto max-w-[1040px] px-[40px] py-[64px]">
        <ErrorNote
          error={demo.error ?? session.error}
          onRetry={() => {
            void demo.refetch();
            void session.refetch();
          }}
        />
      </div>
    );
  }

  if (!demo.data || !session.data) return <ScreenSkeleton />;

  const product = demo.data.product;
  const customer = demo.data.customers?.[0];
  const risks = demo.data.risks ?? [];
  const understandingTargets = risks.filter((r) => r.understandingCheck);
  const query = scenario === "safety" ? "?scenario=safety" : "";

  return (
    <StaffShell
      sessionId={sessionId}
      revision={session.data.currentRevision?.revision ?? 1}
      current="s01"
    >
      <div className="screen-in mx-auto max-w-[1040px] px-[40px] pt-[64px] pb-[96px]">
        <p className="mb-[10px] text-[12.5px] font-semibold tracking-[0.1em] text-[var(--color-muted-soft)]">
          S01 · 상담 준비
        </p>
        <h2 className="text-[34px] leading-[1.25] font-bold tracking-[-0.025em]">
          이 상담에서 확인할 내용
        </h2>
        <p className="mt-[12px] text-[17px] leading-[1.6] text-[var(--color-ink-muted)]">
          아래 설정으로 바로 시작할 수 있습니다. 데모 프리셋이 이미 선택되어 있습니다.
        </p>

        <section className="mt-[44px] border-b border-[oklch(0.91_0.006_85)] pb-[24px]">
          <div className="flex flex-wrap items-baseline gap-[12px]">
            <span className="font-mono text-[12px] font-semibold text-[var(--color-muted)]">
              상품
            </span>
            <h3 className="text-[22px] font-semibold tracking-[-0.015em]">
              {product?.name}
            </h3>
            <span className="rounded-[6px] bg-[var(--color-accent-soft)] px-[9px] py-[4px] text-[12.5px] font-semibold text-[var(--color-accent)]">
              Product A
            </span>
          </div>
          <p className="mt-[14px] max-w-[720px] text-[16.5px] leading-[1.7] text-[var(--color-ink-body)]">
            6개월마다 조건을 평가해 조건이 충족되면 조기상환되는 투자상품입니다. 예금이
            아니며, 조건에 따라 원금 손실이 발생할 수 있습니다.
          </p>
          <p className="mt-[10px] text-[14px] text-[var(--color-muted)]">
            검수된 상품 위험 {risks.length}건 · 상품설명서 원문 근거 포함
          </p>
          {product?.syntheticNotice ? (
            <p className="mt-[10px] max-w-[720px] text-[12.5px] leading-[1.6] text-[var(--color-muted-faint)]">
              {product.syntheticNotice}
            </p>
          ) : null}
        </section>

        <section className="mt-[32px] grid grid-cols-2 gap-[48px] border-b border-[oklch(0.91_0.006_85)] pb-[28px]">
          <div>
            <p className="mb-[12px] font-mono text-[12px] font-semibold text-[var(--color-muted)]">
              고객
            </p>
            <p className="text-[18px] font-semibold">{customer?.label}</p>
            <p className="mt-[8px] text-[15px] leading-[1.65] text-[oklch(0.48_0.01_260)]">
              투자 경험 2년 · ELS 첫 가입 · 원금 보장 상품을 주로 이용
            </p>
          </div>
          <div>
            <p className="mb-[12px] font-mono text-[12px] font-semibold text-[var(--color-muted)]">
              이해확인 대상 {understandingTargets.length}건
            </p>
            <p className="text-[16px] leading-[1.9]">
              {understandingTargets.map((risk) => (
                <span key={risk.riskId} className="block">
                  {risk.title}
                </span>
              ))}
            </p>
          </div>
        </section>

        <div className="mt-[36px] flex items-center gap-[20px]">
          <button
            type="button"
            onClick={() => router.push(`/session/${sessionId}/transcript${query}`)}
            className="rounded-[10px] bg-[var(--color-accent)] px-[28px] py-[14px] text-[15.5px] font-semibold text-white hover:bg-[var(--color-accent-hover)]"
          >
            상담 시작
          </button>
          <span className="text-[13.5px] text-[var(--color-muted-soft)]">
            {SCENARIO_NOTE[scenario]}
          </span>
        </div>
      </div>
    </StaffShell>
  );
}
