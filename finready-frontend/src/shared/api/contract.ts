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
 * The single API surface the UI talks to — one method per OpenAPI
 * operation, same names, same shapes.
 *
 * Backed by `MockFinReadyApi` until the Spring service is reachable, then
 * by `SpringFinReadyApi`; swapping is a base-URL change. Screens never call
 * `fetch` themselves and never reach Supabase directly.
 *
 * Note `submitAnswer` is attempt 1 and `submitRecheck` is attempt 2 — two
 * endpoints, not a mode flag.
 */
export interface FinReadyApi {
  /** GET /products/demo */
  getDemoProduct(): Promise<DemoProductResponse>;

  /** POST /sessions */
  createSession(req: CreateSessionRequest): Promise<SessionResponse>;

  /** GET /sessions/{sessionId} — full state for reload recovery. */
  getSession(sessionId: string): Promise<SessionSnapshotResponse>;

  /** POST /sessions/{sessionId}/revisions — full replacement text. */
  createRevision(
    sessionId: string,
    req: CreateRevisionRequest,
  ): Promise<RevisionResponse>;

  /** POST /sessions/{sessionId}/coverage */
  analyzeCoverage(
    sessionId: string,
    req?: AnalyzeCoverageRequest,
  ): Promise<CoverageResponse>;

  /** POST /sessions/{sessionId}/gate-override */
  overrideGate(
    sessionId: string,
    req: GateOverrideRequest,
  ): Promise<CoverageResponse>;

  /** POST /sessions/{sessionId}/questions — idempotent. */
  getOrCreateQuestions(sessionId: string): Promise<QuestionsResponse>;

  /** POST /sessions/{sessionId}/understanding — attempt 1. */
  submitAnswer(
    sessionId: string,
    req: SubmitAnswerRequest,
  ): Promise<UnderstandingResponse>;

  /** POST /sessions/{sessionId}/reexplain */
  reexplain(
    sessionId: string,
    req: ReExplainRequest,
  ): Promise<ReExplanationResponse>;

  /** POST /sessions/{sessionId}/recheck — attempt 2. */
  submitRecheck(
    sessionId: string,
    req: SubmitRecheckRequest,
  ): Promise<UnderstandingResponse>;

  /** POST /sessions/{sessionId}/risks/{riskId}/staff-resolution */
  resolveByStaff(
    sessionId: string,
    riskId: string,
    req: StaffResolutionRequest,
  ): Promise<RiskUnderstandingState>;

  /** GET /sessions/{sessionId}/report */
  getReport(sessionId: string): Promise<ReportResponse>;

  /** POST /sessions/{sessionId}/close — idempotent. */
  closeSession(
    sessionId: string,
    req: CloseSessionRequest,
  ): Promise<SessionResponse>;
}

/**
 * One error shape for every adapter.
 *
 * `recoverable` comes straight from the contract and is the only signal the
 * UI uses to decide whether to offer a retry.
 */
export class FinReadyApiError extends Error {
  readonly code: string;
  readonly recoverable: boolean;
  readonly requestId?: string;
  readonly riskId?: string | null;
  readonly status?: number;

  constructor(body: ApiErrorBody, status?: number) {
    super(body.message);
    this.name = "FinReadyApiError";
    this.code = body.code;
    this.recoverable = body.recoverable ?? false;
    this.requestId = body.requestId;
    this.riskId = body.riskId;
    this.status = status;
  }
}
