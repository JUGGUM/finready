"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api";
import type {
  AnalyzeCoverageRequest,
  CloseSessionRequest,
  CoverageResponse,
  CreateRevisionRequest,
  CreateSessionRequest,
  GateOverrideRequest,
  ReExplainRequest,
  StaffResolutionRequest,
  SubmitAnswerRequest,
  SubmitRecheckRequest,
} from "@/shared/types/domain";

/**
 * Server state lives here, never mirrored into a client store.
 *
 * Coverage and report responses are cached under the session so screens
 * read one copy; anything that can change gate or understanding state
 * invalidates the session snapshot rather than patching it by hand.
 */

export const queryKeys = {
  demoProduct: ["demo-product"] as const,
  session: (sessionId: string) => ["session", sessionId] as const,
  coverage: (sessionId: string) => ["session", sessionId, "coverage"] as const,
  questions: (sessionId: string) => ["session", sessionId, "questions"] as const,
  report: (sessionId: string) => ["session", sessionId, "report"] as const,
};

export function useDemoProduct() {
  return useQuery({
    queryKey: queryKeys.demoProduct,
    queryFn: () => api.getDemoProduct(),
    staleTime: Infinity,
  });
}

/** Source of truth after a reload — carries `resumePoint`. */
export function useSession(sessionId: string) {
  return useQuery({
    queryKey: queryKeys.session(sessionId),
    queryFn: () => api.getSession(sessionId),
    enabled: Boolean(sessionId),
  });
}

export function useReport(sessionId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.report(sessionId),
    queryFn: () => api.getReport(sessionId),
    enabled: Boolean(sessionId) && enabled,
  });
}

export function useCreateSession() {
  return useMutation({
    mutationFn: (req: CreateSessionRequest) => api.createSession(req),
  });
}

/** Writes the coverage response into cache and refreshes session state. */
function useCoverageWriter(sessionId: string) {
  const queryClient = useQueryClient();
  return (coverage: CoverageResponse) => {
    queryClient.setQueryData(queryKeys.coverage(sessionId), coverage);
    queryClient.invalidateQueries({ queryKey: queryKeys.session(sessionId) });
  };
}

export function useAnalyzeCoverage(sessionId: string) {
  const write = useCoverageWriter(sessionId);
  return useMutation({
    mutationFn: (req?: AnalyzeCoverageRequest) => api.analyzeCoverage(sessionId, req),
    onSuccess: write,
  });
}

/**
 * S02's single action: save an immutable revision, then analyse it.
 * Two calls because the contract has two operations; one mutation because
 * it is one thing the staff member did.
 */
export function useSubmitTranscript(sessionId: string) {
  const write = useCoverageWriter(sessionId);
  return useMutation({
    mutationFn: async (req: CreateRevisionRequest) => {
      const revision = await api.createRevision(sessionId, req);
      return api.analyzeCoverage(sessionId, { revisionId: revision.revisionId });
    },
    onSuccess: write,
  });
}

export function useOverrideGate(sessionId: string) {
  const write = useCoverageWriter(sessionId);
  return useMutation({
    mutationFn: (req: GateOverrideRequest) => api.overrideGate(sessionId, req),
    onSuccess: write,
  });
}

/** Idempotent on the server — safe to call on every entry to S04. */
export function useQuestions(sessionId: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.questions(sessionId),
    queryFn: () => api.getOrCreateQuestions(sessionId),
    enabled: Boolean(sessionId) && enabled,
    staleTime: Infinity,
  });
}

function useUnderstandingInvalidator(sessionId: string) {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.session(sessionId) });
    queryClient.invalidateQueries({ queryKey: queryKeys.report(sessionId) });
  };
}

/** POST /understanding — attempt 1. */
export function useSubmitAnswer(sessionId: string) {
  const invalidate = useUnderstandingInvalidator(sessionId);
  return useMutation({
    mutationFn: (req: SubmitAnswerRequest) => api.submitAnswer(sessionId, req),
    onSuccess: invalidate,
  });
}

/** POST /recheck — attempt 2. */
export function useSubmitRecheck(sessionId: string) {
  const invalidate = useUnderstandingInvalidator(sessionId);
  return useMutation({
    mutationFn: (req: SubmitRecheckRequest) => api.submitRecheck(sessionId, req),
    onSuccess: invalidate,
  });
}

export function useReexplain(sessionId: string) {
  return useMutation({
    mutationFn: (req: ReExplainRequest) => api.reexplain(sessionId, req),
  });
}

export function useResolveByStaff(sessionId: string) {
  const invalidate = useUnderstandingInvalidator(sessionId);
  return useMutation({
    mutationFn: ({
      riskId,
      ...req
    }: { riskId: string } & StaffResolutionRequest) =>
      api.resolveByStaff(sessionId, riskId, req),
    onSuccess: invalidate,
  });
}

export function useCloseSession(sessionId: string) {
  const invalidate = useUnderstandingInvalidator(sessionId);
  return useMutation({
    mutationFn: (req: CloseSessionRequest) => api.closeSession(sessionId, req),
    onSuccess: invalidate,
  });
}
