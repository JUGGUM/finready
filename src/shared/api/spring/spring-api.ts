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
  RiskUnderstandingState,
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
export class SpringFinReadyApi implements FinReadyApi {
  constructor(private readonly baseUrl: string) {}

  private async request<T>(
    path: string,
    init?: { method?: string; body?: unknown },
  ): Promise<T> {
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        method: init?.method ?? "GET",
        headers: init?.body !== undefined ? { "Content-Type": "application/json" } : undefined,
        body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
        cache: "no-store",
      });
    } catch {
      throw new FinReadyApiError({
        code: "INTERNAL_ERROR",
        message: "서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.",
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
      { method: "POST", body: req },
    );
  }

  reexplain(
    sessionId: string,
    req: ReExplainRequest,
  ): Promise<ReExplanationResponse> {
    return this.request<ReExplanationResponse>(`/sessions/${sessionId}/reexplain`, {
      method: "POST",
      body: req,
    });
  }

  submitRecheck(
    sessionId: string,
    req: SubmitRecheckRequest,
  ): Promise<UnderstandingResponse> {
    return this.request<UnderstandingResponse>(`/sessions/${sessionId}/recheck`, {
      method: "POST",
      body: req,
    });
  }

  resolveByStaff(
    sessionId: string,
    riskId: string,
    req: StaffResolutionRequest,
  ): Promise<RiskUnderstandingState> {
    return this.request<RiskUnderstandingState>(
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
