package io.finready.report;

import io.finready.audit.ActorRole;
import io.finready.audit.AuditEvent;
import io.finready.coverage.CoverageResponse;
import io.finready.coverage.GateStatus;
import io.finready.session.CloseEligibility;
import io.finready.session.RevisionResponse;
import io.finready.session.SessionStatus;
import io.finready.understanding.RiskUnderstandingState;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * openapi.yml v1.4.2 {@code ReportResponse}.
 * required: [sessionId, sessionStatus, coverage, understanding, closeEligibility]
 *
 * <p>계약이 <b>섹션을 분리해서</b> 정의한 이유가 있다 — Coverage 와 Understanding 을 한 배열로
 * 합치면 "설명이 부족했다"와 "고객이 반대로 이해했다"가 한 칸에 섞인다. 둘은 원인도 후속 조치도
 * 다르고, 이 제품이 드러내려는 것이 정확히 그 구분이다.
 *
 * <p><b>AI 원판정을 숨기지 않는다.</b> {@code classifierStatus} 와 {@code aiStatus} 는 Override·
 * 직원 처리가 있어도 그대로 실린다(규칙 1). 리포트는 결론만 보여주는 화면이 아니라
 * 결론에 이른 경로를 보여주는 화면이다.
 */
public record ReportResponse(
		String sessionId,
		SessionStatus sessionStatus,
		ProductView product,
		CoverageSection coverage,
		List<RiskUnderstandingState> understanding,
		List<CoverageResponse.OverrideView> overrides,
		List<RevisionResponse> revisions,
		List<AuditEventView> auditEvents,
		List<String> unresolvedRiskIds,
		CloseEligibility closeEligibility,
		String disclaimer
) {

	/**
	 * 화면 하단 고정 문구. <b>계약에 문자열까지 적혀 있다</b>(PRD §14) — 이 서비스가 법적
	 * 판정이 아니라는 표시라서 문구가 바뀌면 안 된다.
	 */
	public static final String DISCLAIMER =
			"FinReady 분석 결과는 금융상담을 지원하기 위한 참고정보입니다. "
					+ "법적 판정이 아니며 최종 확인은 담당자가 수행합니다.";

	/**
	 * @param productRiskVersion <b>세션이 만들어질 때 고정된 값</b>이다. 시드가 바뀌어도 이
	 *                           상담의 판정 기준은 변하지 않으므로, 상품의 현재 버전이 아니라
	 *                           세션 snapshot 을 싣는다 (TRD §4.1)
	 */
	public record ProductView(String id, String name, String productRiskVersion) {
	}

	/**
	 * @param finalRevisionId 판정에 쓰인 revision. 보완 설명을 여러 번 했다면 <b>마지막 것</b>이다
	 * @param results         분석 전이면 빈 배열이다 — 계약이 {@code coverage} 를 required 로 두므로
	 *                        null 을 내보내지 않는다
	 */
	public record CoverageSection(Long finalRevisionId,
	                              GateStatus gateStatus,
	                              List<CoverageResponse.RiskView> results) {

		static final CoverageSection EMPTY = new CoverageSection(null, null, List.of());
	}

	/**
	 * append-only 기록. 수정·삭제 API 는 제공하지 않는다(계약).
	 *
	 * <p>{@code actorRole} 이 핵심 필드다 — 같은 세션 안에서 <b>모델이 판정한 것</b>(AI)과
	 * <b>직원이 정한 것</b>(STAFF)이 구분돼야 리포트를 신뢰할 수 있다.
	 */
	public record AuditEventView(String eventType,
	                             String actor,
	                             ActorRole actorRole,
	                             String payloadSummary,
	                             OffsetDateTime createdAt) {

		static AuditEventView from(AuditEvent event) {
			return new AuditEventView(
					event.getEventType(),
					event.getActor(),
					event.getActorRole(),
					event.getPayloadSummary(),
					event.getCreatedAt());
		}
	}
}
