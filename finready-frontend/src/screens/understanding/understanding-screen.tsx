"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AnswerForm } from "@/screens/understanding/answer-form";
import { ReExplanationView } from "@/screens/understanding/reexplanation-view";
import { ResultView } from "@/screens/understanding/result-view";
import {
  useQuestions,
  useReexplain,
  useSession,
  useSubmitAnswer,
  useSubmitRecheck,
} from "@/shared/api/queries";
import { RISK_ANSWERS } from "@/shared/constants/demo";
import type {
  NextAction,
  ReExplanationResponse,
  UnderstandingResponse,
} from "@/shared/types/domain";
import { CustomerShell } from "@/shared/ui/customer-shell";
import { ErrorNote } from "@/shared/ui/error-note";
import { ScreenSkeleton } from "@/shared/ui/screen-skeleton";
import { useScenario } from "@/shared/ui/staff-shell";

/**
 * S04–S07, the customer's side of the session.
 *
 * One route, because to the customer it is one continuous conversation.
 * The view within it is chosen by the server's `nextAction` — this screen
 * never inspects `aiStatus` and `attempt` to decide where to go next, which
 * is how a second, divergent copy of the branch rules would get built.
 *
 * CONTRACT BLOCKER — mid-step reload.
 * `SessionSnapshotResponse` carries attempts and `workflowStatus`, so a
 * reload can tell which risk is open and whether it has escalated. It does
 * not carry the last result or the re-explanation, so S05 and S06 cannot be
 * restored exactly; a reload lands on the question for that risk instead.
 * Reconstructing either locally would mean inventing content the server
 * never sent, so we don't. Revisit once the contract exposes them.
 */

type View =
  | { kind: "question"; mode: "INITIAL" | "RECHECK" }
  | { kind: "result"; result: UnderstandingResponse }
  | { kind: "reexplain"; data: ReExplanationResponse; misunderstanding?: string }
  | { kind: "done" };

export function UnderstandingScreen({ sessionId }: { sessionId: string }) {
  const router = useRouter();
  const scenario = useScenario();
  const query = scenario === "safety" ? "?scenario=safety" : "";

  const questions = useQuestions(sessionId, true);
  const session = useSession(sessionId);
  const submitAnswer = useSubmitAnswer(sessionId);
  const submitRecheck = useSubmitRecheck(sessionId);
  const reexplain = useReexplain(sessionId);

  /**
   * Which risk is on screen. Null means "whatever the server says is still
   * open" — that way re-entering (after a staff resolution, or a reload)
   * resumes at the right risk instead of restarting at the first one.
   */
  const [activeRiskId, setActiveRiskId] = useState<string | null>(null);
  const [answer, setAnswer] = useState("");
  const [view, setView] = useState<View>({ kind: "question", mode: "INITIAL" });

  const allQuestions = questions.data?.questions ?? [];
  // Settled, or waiting on a staff decision — either way the customer has
  // nothing left to answer for that risk.
  const closedToCustomer = new Set(
    (session.data?.understanding ?? [])
      .filter(
        (s) =>
          s.workflowStatus === "COMPLETE" ||
          s.workflowStatus === "MANUAL_REVIEW_REQUIRED",
      )
      .map((s) => s.riskId as string),
  );
  const openQuestions = allQuestions.filter(
    (q) => !closedToCustomer.has(q.riskId as string),
  );
  const customerDone =
    Boolean(questions.data) &&
    Boolean(session.data) &&
    openQuestions.length === 0;

  // Nothing left for the customer to answer — hand the device back. Done in
  // an effect because navigating during render updates the router mid-render.
  useEffect(() => {
    if (customerDone) router.replace(`/session/${sessionId}/return${query}`);
  }, [customerDone, router, sessionId, query]);

  if (questions.isError) {
    return (
      <CustomerShell currentIndex={1} totalCount={3}>
        <div className="mx-auto max-w-[760px] px-[40px] py-[72px]">
          <ErrorNote
            error={questions.error}
            onRetry={() => void questions.refetch()}
          />
        </div>
      </CustomerShell>
    );
  }
  if (!questions.data || !session.data) return <ScreenSkeleton />;
  if (customerDone) return <ScreenSkeleton label="직원 화면으로 이동합니다" />;

  const total = questions.data.totalRiskCount ?? allQuestions.length;
  const current =
    openQuestions.find((q) => q.riskId === activeRiskId) ?? openQuestions[0];

  if (!current) return <ScreenSkeleton />;

  const riskId = current.riskId as string;
  const kicker = `핵심 위험 ${current.orderIndex ?? 1} / ${total}`;
  const samples = RISK_ANSWERS[riskId];
  const pending =
    submitAnswer.isPending || submitRecheck.isPending || reexplain.isPending;

  /** Advance to whatever the server said comes next. */
  const follow = (nextAction: NextAction, result?: UnderstandingResponse) => {
    switch (nextAction) {
      case "NEXT_RISK": {
        const next = openQuestions.find((q) => q.riskId !== current.riskId);
        setActiveRiskId((next?.riskId as string) ?? null);
        setAnswer("");
        setView({ kind: "question", mode: "INITIAL" });
        return;
      }
      case "RECHECK":
        setAnswer("");
        setView({ kind: "question", mode: "RECHECK" });
        return;
      case "REEXPLAIN":
        reexplain.mutate(
          { riskId },
          {
            onSuccess: (data) =>
              setView({
                kind: "reexplain",
                data,
                misunderstanding: result?.reason,
              }),
          },
        );
        return;
      case "STAFF_RESOLUTION_REQUIRED":
      case "GO_TO_REPORT":
        setView({ kind: "done" });
        router.push(`/session/${sessionId}/return${query}`);
        return;
    }
  };

  const submit = () => {
    if (view.kind !== "question") return;
    const req = {
      riskId,
      answer: answer.trim(),
      answerSource: "CUSTOMER_DIRECT_DEMO" as const,
    };
    const mutation = view.mode === "INITIAL" ? submitAnswer : submitRecheck;
    mutation.mutate(req, {
      onSuccess: (result) => setView({ kind: "result", result }),
    });
  };

  const error = submitAnswer.error ?? submitRecheck.error ?? reexplain.error;

  return (
    <CustomerShell
      currentIndex={current.orderIndex ?? 1}
      totalCount={total}
    >
      {error ? (
        <div className="mx-auto max-w-[760px] px-[40px] pt-[40px]">
          <ErrorNote error={error} onRetry={submit} />
        </div>
      ) : null}

      {view.kind === "question" ? (
        <AnswerForm
          question={
            view.mode === "INITIAL"
              ? (current.question ?? "")
              : (reexplain.data?.recheckQuestion ?? current.question ?? "")
          }
          kicker={kicker}
          note={
            view.mode === "INITIAL"
              ? "답변은 상담 기록과 함께 보관됩니다."
              : "두 번째 확인입니다. 이번에도 판단이 어려우면 담당 직원이 함께 확인합니다."
          }
          answer={answer}
          onAnswerChange={setAnswer}
          samples={samples}
          bucket={view.mode === "INITIAL" ? "initial" : "recheck"}
          pending={pending}
          onSubmit={submit}
        />
      ) : null}

      {view.kind === "result" ? (
        <ResultView
          aiStatus={view.result.aiStatus}
          reason={view.result.reason}
          answer={view.result.answer ?? answer}
          kicker={kicker}
          nextAction={view.result.nextAction}
          pending={pending}
          onContinue={() => follow(view.result.nextAction, view.result)}
        />
      ) : null}

      {view.kind === "reexplain" ? (
        <ReExplanationView
          data={view.data}
          misunderstanding={view.misunderstanding}
          kicker={kicker}
          pending={pending}
          onContinue={() => follow(view.data.nextAction ?? "RECHECK")}
        />
      ) : null}

      {view.kind === "done" ? <ScreenSkeleton label="직원 화면으로 이동합니다" /> : null}
    </CustomerShell>
  );
}
