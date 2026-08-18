import { FinReadyApiError, type FinReadyApi } from "@/shared/api/contract";
import type {
  AnalyzeCoverageRequest,
  ApiErrorBody,
  CloseSessionRequest,
  CoverageResponse,
  CreateRevisionRequest,
  CreateSessionRequest,
  DemoProductResponse,
  GateOverrideRequest,
  QuestionsResponse,
  ReExplainRequest,
  ReExplanationResponse,
  ReportResponse,
  RevisionResponse,
  StaffResolutionResponse,
  SessionResponse,
  SessionSnapshotResponse,
  StaffResolutionRequest,
  SubmitAnswerRequest,
  SubmitRecheckRequest,
  UnderstandingResponse,
} from "@/shared/types/domain";

/**
 * Spring Boot REST adapter — the frontend's only backend.
 *
 * `baseUrl` already ends in `/api` (that is how the OpenAPI `servers`
 * entries are written), so every path below starts at `/products` or
 * `/sessions`. Prefixing them with `/api` again would produce
 * `/api/api/...`.
 */
/** Plain reads and writes. */
const DEFAULT_TIMEOUT_MS = 15_000;
/** Coverage analysis and answer classification run LLM calls server-side. */
const LLM_TIMEOUT_MS = 60_000;

export class SpringFinReadyApi implements FinReadyApi {
  constructor(private readonly baseUrl: string) {}

  private async request<T>(
    path: string,
    init?: { method?: string; body?: unknown; timeoutMs?: number },
  ): Promise<T> {
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        method: init?.method ?? "GET",
        headers: init?.body !== undefined ? { "Content-Type": "application/json" } : undefined,
        body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
        cache: "no-store",
        // A hung request otherwise leaves the screen spinning with no way
        // back. LLM-backed steps get a longer budget than plain reads.
        signal: AbortSignal.timeout(init?.timeoutMs ?? DEFAULT_TIMEOUT_MS),
      });
    } catch (cause) {
      const timedOut = cause instanceof DOMException && cause.name === "TimeoutError";
      throw new FinReadyApiError({
        code: timedOut ? "AI_TIMEOUT" : "INTERNAL_ERROR",
        message: timedOut
          ? "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
          : "서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.",
        recoverable: true,
      });
    }

    if (!response.ok) {
      let body: ApiErrorBody;
      try {
        body = (await response.json()) as ApiErrorBody;
      } catch {
        body = {
          code: "INTERNAL_ERROR",
          message: "요청을 처리하지 못했습니다.",
          recoverable: response.status >= 500,
        };
      }
      throw new FinReadyApiError(body, response.status);
    }

    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }

  getDemoProduct(): Promise<DemoProductResponse> {
    return this.request<DemoProductResponse>("/products/demo");
  }

  createSession(req: CreateSessionRequest): Promise<SessionResponse> {
    return this.request<SessionResponse>("/sessions", {
      method: "POST",
      body: req,
    });
  }

  getSession(sessionId: string): Promise<SessionSnapshotResponse> {
    return this.request<SessionSnapshotResponse>(`/sessions/${sessionId}`);
  }

  createRevision(
    sessionId: string,
    req: CreateRevisionRequest,
  ): Promise<RevisionResponse> {
    return this.request<RevisionResponse>(`/sessions/${sessionId}/revisions`, {
      method: "POST",
      body: req,
    });
  }

  analyzeCoverage(
    sessionId: string,
    req?: AnalyzeCoverageRequest,
  ): Promise<CoverageResponse> {
    return this.request<CoverageResponse>(`/sessions/${sessionId}/coverage`, {
      method: "POST",
      body: req ?? {},
      timeoutMs: LLM_TIMEOUT_MS,
    });
  }

  overrideGate(
    sessionId: string,
    req: GateOverrideRequest,
  ): Promise<CoverageResponse> {
    return this.request<CoverageResponse>(`/sessions/${sessionId}/gate-override`, {
      method: "POST",
      body: req,
    });
  }

  getOrCreateQuestions(sessionId: string): Promise<QuestionsResponse> {
    return this.request<QuestionsResponse>(`/sessions/${sessionId}/questions`, {
      method: "POST",
    });
  }

  submitAnswer(
    sessionId: string,
    req: SubmitAnswerRequest,
  ): Promise<UnderstandingResponse> {
    return this.request<UnderstandingResponse>(
      `/sessions/${sessionId}/understanding`,
      { method: "POST", body: req, timeoutMs: LLM_TIMEOUT_MS },
    );
  }

  reexplain(
    sessionId: string,
    req: ReExplainRequest,
  ): Promise<ReExplanationResponse> {
    return this.request<ReExplanationResponse>(`/sessions/${sessionId}/reexplain`, {
      method: "POST",
      body: req,
      timeoutMs: LLM_TIMEOUT_MS,
    });
  }

  submitRecheck(
    sessionId: string,
    req: SubmitRecheckRequest,
  ): Promise<UnderstandingResponse> {
    return this.request<UnderstandingResponse>(`/sessions/${sessionId}/recheck`, {
      method: "POST",
      body: req,
      timeoutMs: LLM_TIMEOUT_MS,
    });
  }

  resolveByStaff(
    sessionId: string,
    riskId: string,
    req: StaffResolutionRequest,
  ): Promise<StaffResolutionResponse> {
    return this.request<StaffResolutionResponse>(
      `/sessions/${sessionId}/risks/${riskId}/staff-resolution`,
      { method: "POST", body: req },
    );
  }

  getReport(sessionId: string): Promise<ReportResponse> {
    return this.request<ReportResponse>(`/sessions/${sessionId}/report`);
  }

  closeSession(
    sessionId: string,
    req: CloseSessionRequest,
  ): Promise<SessionResponse> {
    return this.request<SessionResponse>(`/sessions/${sessionId}/close`, {
      method: "POST",
      body: req,
    });
  }
}
