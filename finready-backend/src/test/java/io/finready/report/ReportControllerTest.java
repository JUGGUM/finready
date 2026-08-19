package io.finready.report;

import io.finready.audit.ActorRole;
import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import io.finready.coverage.CoverageResponse;
import io.finready.coverage.CoverageStatus;
import io.finready.coverage.GateStatus;
import io.finready.product.CoveragePolicy;
import io.finready.session.CloseEligibility;
import io.finready.session.SessionStatus;
import io.finready.understanding.RiskUnderstandingState;
import io.finready.understanding.WorkflowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F08 리포트의 <b>계약 필드명</b> 검증. 서비스는 대역이다.
 *
 * <p>리포트는 프론트가 가장 많은 필드를 읽는 응답이라, 이름 하나가 어긋나면 화면 한 칸이
 * 조용히 비어 있게 된다. 값이 아니라 <b>이름과 구조</b>를 고정하는 것이 이 클래스의 일이다.
 */
@WebMvcTest(ReportController.class)
class ReportControllerTest {

	private static final String SESSION_ID = "11111111-2222-3333-4444-555555555555";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReportService reportService;

	private ReportResponse report() {
		CoverageResponse.RiskView risk = new CoverageResponse.RiskView(
				"R01", "원금 손실 가능성", CoveragePolicy.GATE_REQUIRED,
				CoverageStatus.EXPLAINED, CoverageStatus.INSUFFICIENT, true,
				"분류기 근거", "검증 근거", null, null, null);

		return new ReportResponse(
				SESSION_ID,
				SessionStatus.AWAITING_STAFF_REVIEW,
				new ReportResponse.ProductView("PROD_A", "테스트 상품", "A-2026-08-12-01"),
				new ReportResponse.CoverageSection(7L, GateStatus.GATE_BLOCKED, List.of(risk)),
				List.of(new RiskUnderstandingState("R01", "원금 손실 가능성", List.of(), null,
						WorkflowStatus.MANUAL_REVIEW_REQUIRED, null, null)),
				List.of(),
				List.of(),
				List.of(new ReportResponse.AuditEventView("COVERAGE_ANALYZED", "claude",
						ActorRole.AI, "gateStatus=GATE_BLOCKED",
						OffsetDateTime.parse("2026-08-19T09:00:00Z"))),
				List.of("R01"),
				new CloseEligibility(true, true, List.of("R06"),
						SessionStatus.SESSION_CLOSED_WITH_UNRESOLVED),
				ReportResponse.DISCLAIMER);
	}

	@Test
	@DisplayName("GET /api/sessions/{id}/report → 계약 섹션명 그대로")
	void reportReturnsContractShape() throws Exception {
		when(reportService.getReport(anyString())).thenReturn(report());

		mockMvc.perform(get("/api/sessions/{id}/report", SESSION_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").value(SESSION_ID))
				.andExpect(jsonPath("$.sessionStatus").value("AWAITING_STAFF_REVIEW"))
				.andExpect(jsonPath("$.product.productRiskVersion").value("A-2026-08-12-01"))
				.andExpect(jsonPath("$.coverage.finalRevisionId").value(7))
				.andExpect(jsonPath("$.coverage.gateStatus").value("GATE_BLOCKED"))
				.andExpect(jsonPath("$.understanding[0].workflowStatus")
						.value("MANUAL_REVIEW_REQUIRED"))
				.andExpect(jsonPath("$.unresolvedRiskIds[0]").value("R01"))
				.andExpect(jsonPath("$.disclaimer").exists());
	}

	/** 규칙 1 — Override·직원 처리가 있어도 AI 원판정은 리포트에 그대로 남는다 */
	@Test
	@DisplayName("classifierStatus 와 coverageStatus 를 둘 다 싣는다")
	void keepsClassifierVerdict() throws Exception {
		when(reportService.getReport(anyString())).thenReturn(report());

		mockMvc.perform(get("/api/sessions/{id}/report", SESSION_ID))
				.andExpect(jsonPath("$.coverage.results[0].classifierStatus").value("EXPLAINED"))
				.andExpect(jsonPath("$.coverage.results[0].coverageStatus").value("INSUFFICIENT"))
				.andExpect(jsonPath("$.coverage.results[0].downgraded").value(true));
	}

	/** 사람이 정한 것과 모델이 정한 것이 구분돼야 리포트를 신뢰할 수 있다 */
	@Test
	@DisplayName("감사 이벤트에 actorRole 이 실린다")
	void exposesActorRole() throws Exception {
		when(reportService.getReport(anyString())).thenReturn(report());

		mockMvc.perform(get("/api/sessions/{id}/report", SESSION_ID))
				.andExpect(jsonPath("$.auditEvents[0].actorRole").value("AI"))
				.andExpect(jsonPath("$.auditEvents[0].eventType").value("COVERAGE_ANALYZED"));
	}

	/** 종료 버튼 조건은 서버가 정한다(규칙 8). 프론트가 재계산하지 않는다 */
	@Test
	@DisplayName("closeEligibility 4개 필드를 그대로 내보낸다")
	void exposesCloseEligibility() throws Exception {
		when(reportService.getReport(anyString())).thenReturn(report());

		mockMvc.perform(get("/api/sessions/{id}/report", SESSION_ID))
				.andExpect(jsonPath("$.closeEligibility.canClose").value(true))
				.andExpect(jsonPath("$.closeEligibility.requiresUnresolvedReason").value(true))
				.andExpect(jsonPath("$.closeEligibility.requiresWarningAcknowledgement[0]")
						.value("R06"))
				.andExpect(jsonPath("$.closeEligibility.expectedCloseStatus")
						.value("SESSION_CLOSED_WITH_UNRESOLVED"));
	}

	@Test
	@DisplayName("없는 세션 → 404 SESSION_NOT_FOUND")
	void missingSessionReturns404() throws Exception {
		when(reportService.getReport(anyString()))
				.thenThrow(new ApiException(ErrorCode.SESSION_NOT_FOUND, "상담 세션을 찾을 수 없습니다."));

		mockMvc.perform(get("/api/sessions/{id}/report", "nope"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
	}
}
