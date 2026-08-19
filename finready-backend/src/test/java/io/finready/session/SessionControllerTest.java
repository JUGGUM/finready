package io.finready.session;

import io.finready.common.ApiException;
import io.finready.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 계층 계약 검증. 서비스는 대역이다.
 *
 * <p>여기서 잡으려는 것은 <b>필드명 불일치</b>다. sessionStatus 를 status 로 잘못 내보내도
 * 컴파일은 통과하고 서버도 뜬다. 프론트가 붙일 때야 터진다.
 */
@WebMvcTest(SessionController.class)
class SessionControllerTest {

	private static final String SESSION_ID = "11111111-2222-3333-4444-555555555555";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SessionService sessionService;

	private SessionResponse sessionResponse() {
		return new SessionResponse(SESSION_ID, "PROD_A", "CUST_A", "A-2026-08-12-01",
				SessionStatus.DRAFT, OffsetDateTime.parse("2026-08-14T09:00:00Z"), null);
	}

	@Test
	@DisplayName("POST /api/sessions → 201, 계약 필드명 그대로")
	void createSessionReturnsContractShape() throws Exception {
		when(sessionService.createSession(any())).thenReturn(sessionResponse());

		mockMvc.perform(post("/api/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":\"PROD_A\",\"customerId\":\"CUST_A\"}"))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.sessionId").value(SESSION_ID))
				.andExpect(jsonPath("$.productId").value("PROD_A"))
				.andExpect(jsonPath("$.customerId").value("CUST_A"))
				.andExpect(jsonPath("$.productRiskVersion").value("A-2026-08-12-01"))
				.andExpect(jsonPath("$.sessionStatus").value("DRAFT"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	@DisplayName("POST /api/sessions/{id}/revisions → 201, revisionId 와 revision 은 다른 필드")
	void createRevisionReturnsContractShape() throws Exception {
		when(sessionService.createRevision(anyString(), any())).thenReturn(
				new RevisionResponse(42L, 2, "상담 내용", 5,
						OffsetDateTime.parse("2026-08-14T09:01:00Z")));

		mockMvc.perform(post("/api/sessions/{id}/revisions", SESSION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"text\":\"상담 내용\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.revisionId").value(42))
				.andExpect(jsonPath("$.revision").value(2))
				.andExpect(jsonPath("$.text").value("상담 내용"))
				.andExpect(jsonPath("$.charCount").value(5));
	}

	@Test
	@DisplayName("GET /api/sessions/{id} → 200, 아직 없는 단계는 null / 빈 배열")
	void getSnapshotReturnsContractShape() throws Exception {
		when(sessionService.getSnapshot(SESSION_ID)).thenReturn(new SessionSnapshotResponse(
				SESSION_ID, "PROD_A", "CUST_A", "A-2026-08-12-01", SessionStatus.DRAFT,
				OffsetDateTime.parse("2026-08-14T09:00:00Z"), null,
				ResumePoint.S02, null,
				new RevisionResponse(42L, 2, "상담 내용", 5,
						OffsetDateTime.parse("2026-08-14T09:01:00Z")),
				null, List.of()));

		mockMvc.perform(get("/api/sessions/{id}", SESSION_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").value(SESSION_ID))
				.andExpect(jsonPath("$.sessionStatus").value("DRAFT"))
				.andExpect(jsonPath("$.resumePoint").value("S02"))
				.andExpect(jsonPath("$.currentRevision.revision").value(2))
				// 계약이 oneOf: [X, "null"] 이다. 키는 있고 값이 null 인 게 정상이다
				.andExpect(jsonPath("$.coverage").value(nullValue()))
				.andExpect(jsonPath("$.nextAction").value(nullValue()))
				.andExpect(jsonPath("$.understanding").isArray())
				.andExpect(jsonPath("$.understanding").isEmpty());
	}

	// ------------------------------------------------------------------
	// 오류 응답 — openapi Error 스키마
	// ------------------------------------------------------------------

	@Test
	@DisplayName("SESSION_NOT_FOUND → 404, Error 스키마 형태")
	void notFoundReturnsErrorSchema() throws Exception {
		when(sessionService.getSnapshot(anyString()))
				.thenThrow(new ApiException(ErrorCode.SESSION_NOT_FOUND, "상담 세션을 찾을 수 없습니다."));

		mockMvc.perform(get("/api/sessions/{id}", "nope"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("상담 세션을 찾을 수 없습니다."))
				.andExpect(jsonPath("$.recoverable").value(false))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	@DisplayName("TRANSCRIPT_EMPTY → 400")
	void transcriptEmptyReturns400() throws Exception {
		when(sessionService.createRevision(anyString(), any()))
				.thenThrow(new ApiException(ErrorCode.TRANSCRIPT_EMPTY, "상담 내용을 입력해 주세요."));

		mockMvc.perform(post("/api/sessions/{id}/revisions", SESSION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"text\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TRANSCRIPT_EMPTY"));
	}

	@Test
	@DisplayName("INVALID_STATE_TRANSITION → 409")
	void invalidStateTransitionReturns409() throws Exception {
		when(sessionService.createRevision(anyString(), any()))
				.thenThrow(new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
						"종료된 상담에는 내용을 추가할 수 없습니다."));

		mockMvc.perform(post("/api/sessions/{id}/revisions", SESSION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"text\":\"내용\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
	}

	/** recoverable=true 인 코드는 프론트가 재시도 버튼을 띄운다 */
	@Test
	@DisplayName("CONCURRENT_SESSION_UPDATE 는 recoverable=true 로 나간다")
	void concurrentUpdateIsRecoverable() throws Exception {
		when(sessionService.createRevision(anyString(), any()))
				.thenThrow(new ApiException(ErrorCode.CONCURRENT_SESSION_UPDATE, "다시 시도해 주세요."));

		mockMvc.perform(post("/api/sessions/{id}/revisions", SESSION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"text\":\"내용\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.recoverable").value(true));
	}

	// ------------------------------------------------------------------
	// POST /api/sessions/{id}/close  (F08)
	// ------------------------------------------------------------------

	/** 계약이 close 응답을 SessionResponse 로 정의한다. 201 이 아니라 200 이다 */
	@Test
	@DisplayName("POST /api/sessions/{id}/close → 200 SessionResponse")
	void closeReturnsSessionResponse() throws Exception {
		when(sessionService.closeSession(anyString(), any())).thenReturn(
				new SessionResponse(SESSION_ID, "PROD_A", "CUST_A", "A-2026-08-12-01",
						SessionStatus.SESSION_CLOSED_BY_STAFF,
						OffsetDateTime.parse("2026-08-14T09:00:00Z"),
						OffsetDateTime.parse("2026-08-14T10:00:00Z")));

		mockMvc.perform(post("/api/sessions/{id}/close", SESSION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"actor\":\"staff-001\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionStatus").value("SESSION_CLOSED_BY_STAFF"))
				.andExpect(jsonPath("$.closedAt").exists());
	}

	@Test
	@DisplayName("미해결 사유 누락 → 400 UNRESOLVED_REASON_REQUIRED")
	void closeWithoutReasonReturns400() throws Exception {
		when(sessionService.closeSession(anyString(), any())).thenThrow(
				new ApiException(ErrorCode.UNRESOLVED_REASON_REQUIRED, "사유를 입력해 주세요.", "R01"));

		mockMvc.perform(post("/api/sessions/{id}/close", SESSION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"actor\":\"staff-001\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNRESOLVED_REASON_REQUIRED"))
				.andExpect(jsonPath("$.riskId").value("R01"));
	}

	@Test
	@DisplayName("경고 확인 누락 → 400 WARNING_ACKNOWLEDGEMENT_REQUIRED")
	void closeWithoutAcknowledgementReturns400() throws Exception {
		when(sessionService.closeSession(anyString(), any())).thenThrow(
				new ApiException(ErrorCode.WARNING_ACKNOWLEDGEMENT_REQUIRED, "확인이 필요합니다.", "R07"));

		mockMvc.perform(post("/api/sessions/{id}/close", SESSION_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"actor\":\"staff-001\",\"acknowledgedWarnings\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WARNING_ACKNOWLEDGEMENT_REQUIRED"));
	}

	/** 안 잡으면 500 으로 나간다. 클라이언트 잘못을 서버 오류로 보고하는 셈이다 */
	@Test
	@DisplayName("깨진 JSON → 400 INVALID_REQUEST")
	void malformedJsonReturns400() throws Exception {
		mockMvc.perform(post("/api/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
